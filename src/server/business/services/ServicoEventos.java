package server.business.services;

import common.dto.EventoDTO;
import common.dto.EventosFiltradosDTO;
import common.dto.FiltrarEventosDTO;
import common.dto.NotificacaoDTO;
import common.dto.RespostaDTO;
import common.exceptions.EventoException;
import common.interfaces.IServicoEventos;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import server.business.domain.Evento;
import server.data.cache.CacheManager;
import server.data.repository.IEventoRepository;

/**
 * Serviço de negócio para gestão de eventos
 */
public class ServicoEventos implements IServicoEventos {
    private final IEventoRepository eventoRepository;
    private final CacheManager cacheManager;
    private final int D; // Janela de dias
    
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private int diaAtual;
    private final Map<Integer, List<Evento>> eventosDiaAtual = new HashMap<>();
    private final Map<Integer, ConditionCounter> condsProduto = new HashMap<>();
    private int lastProductID = -1;
    private int consecutiveCount = -1;

    private class ConditionCounter {
        int interested = 0;
        Condition c;
        
        ConditionCounter(ReentrantReadWriteLock lock) {
            this.c = lock.writeLock().newCondition();
        }

        void increment() {
            interested++;
        }

        void decrement() {
            interested--;
        }
    }
    
    public ServicoEventos(IEventoRepository eventoRepository, CacheManager cacheManager, int D) {
        this.eventoRepository = eventoRepository;
        this.cacheManager = cacheManager;
        this.D = D;
        this.diaAtual = eventoRepository.obterUltimoDia() + 1;
        
        System.out.println("ServicoEventos iniciado no dia: " + diaAtual + " (Janela D=" + D + ")");
    }
    
    @Override
    public RespostaDTO registrarEvento(EventoDTO dto) throws EventoException {
        validarEvento(dto);
        
        // Criar entidade
        Evento evento = new Evento(dto.getProdutoID(), dto.getQuantidade(), dto.getPreco());
        
        // Adicionar ao dia atual
        lock.writeLock().lock();
        try {
            List<Evento> lista = eventosDiaAtual.get(dto.getProdutoID());
        
            if (lista == null) {
                lista = new ArrayList<>();
                eventosDiaAtual.put(dto.getProdutoID(), lista);
            }
        
            lista.add(evento);
            if (lastProductID == dto.getProdutoID()) {
                consecutiveCount++;
            } else {
                lastProductID = dto.getProdutoID();
                consecutiveCount = 1;
            }
            // Notificar condições específicas
            ConditionCounter cc = condsProduto.get(dto.getProdutoID());
            if (cc != null) {
                cc.c.signalAll();
            }
        } finally {
            lock.writeLock().unlock();
        }

        
        System.out.println(String.format("Evento registrado: Produto=%d, Qtd=%d, Preço=%.2f (Dia %d)",
            dto.getProdutoID(), dto.getQuantidade(), dto.getPreco(), diaAtual));
        
        return RespostaDTO.sucesso("Evento registado com sucesso");
    }

    @Override
    public RespostaDTO notificarVendaEspecifica(NotificacaoDTO notificacao) throws EventoException {
        int produtoID1 = notificacao.getArg1();
        int produtoID2 = notificacao.getArg2();
        lock.writeLock().lock();
        try {
            ConditionCounter cc1 = condsProduto.get(produtoID1);
            if (cc1 == null) {
                cc1 = new ConditionCounter(lock);
                condsProduto.put(produtoID1, cc1);
            }
            cc1.increment();

            ConditionCounter cc2 = condsProduto.get(produtoID2);
            if (cc2 == null) {
                cc2 = new ConditionCounter(lock);
                condsProduto.put(produtoID2, cc2);
            }
            cc2.increment();

            int diaAtual = this.diaAtual;
            List<Evento> l1 = eventosDiaAtual.computeIfAbsent(produtoID1, k -> new ArrayList<>());
            List<Evento> l2 = eventosDiaAtual.computeIfAbsent(produtoID2, k -> new ArrayList<>());
            int size1 = l1.size();
            int size2 = l2.size();

            while (size1 == eventosDiaAtual.getOrDefault(produtoID1, Collections.emptyList()).size() || size2 == eventosDiaAtual.getOrDefault(produtoID2, Collections.emptyList()).size()) {
                try {
                    if (size1 == eventosDiaAtual.getOrDefault(produtoID1, Collections.emptyList()).size()) {
                        cc1.c.await();
                    }
                    if (diaAtual != this.diaAtual) {
                        return RespostaDTO.erro("Dia avançou, notificação falhou");
                    }
                    if (size2 == eventosDiaAtual.getOrDefault(produtoID2, Collections.emptyList()).size()) {
                        cc2.c.await();
                    }
                    if (diaAtual != this.diaAtual) {
                        return RespostaDTO.erro("Dia avançou, notificação falhou"); // verificar apos ambos awaits para evitar bloqueios
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new EventoException("Espera por notificação interrompida", e);
                }
            }

            return RespostaDTO.sucesso("Itens vendidos!");
                
        } finally {
            // decrementar aqui para evitar incoerencias por interrupções
            ConditionCounter cc1 = condsProduto.get(produtoID1);
            if (cc1 != null) {
                cc1.decrement();
                if (cc1.interested == 0) {
                    condsProduto.remove(produtoID1);
                }
            }
            ConditionCounter cc2 = condsProduto.get(produtoID2);
            if (cc2 != null) {
                cc2.decrement();
                if (cc2.interested == 0) {
                    condsProduto.remove(produtoID2);
                }
            }
            lock.writeLock().unlock();
        }
    }

    @Override
    public RespostaDTO notificarVendasConsecutivas(NotificacaoDTO notificacao) throws EventoException {
        int produtoID = notificacao.getArg1();
        int n = notificacao.getArg2();
        
        lock.writeLock().lock();
        try {
            ConditionCounter cc = condsProduto.get(produtoID);
            if (cc == null) {
                cc = new ConditionCounter(lock);
                condsProduto.put(produtoID, cc);
            }
            cc.increment();

            int diaAtual = this.diaAtual;

            int base = 0;

            while (diaAtual == this.diaAtual){

                if (lastProductID != produtoID) { // nao esta numa streak desejada
                    base = 0;
                } else { // esta numa streak
                    if (base == 0) {
                        // contar a partir de streak existente apos request
                        base = consecutiveCount;
                    }
                    int progress = consecutiveCount - base + 1; // numero de eventos apos base
                    if (progress >= n) {
                        return RespostaDTO.sucesso("TODO INSERIR NOME DE PRODUTO AQUI?!");
                    }
                }

                try {
                    cc.c.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new EventoException("Espera por notificação interrompida", e);
                }
            }
            
            return new RespostaDTO(false, null);

        } finally {
            // decrementar aqui para evitar incoerencias por interrupções
            ConditionCounter cc = condsProduto.get(produtoID);
            if (cc != null) {
                cc.decrement();
                if (cc.interested == 0) {
                    condsProduto.remove(produtoID);
                }
            }
            lock.writeLock().unlock();
        }

    }


    @Override
    public RespostaDTO listarEventosDiaAtual() throws EventoException {
        lock.readLock().lock();
        try {
            if (eventosDiaAtual.isEmpty()) {
                return RespostaDTO.sucesso(
                    "=== EVENTOS DO DIA " + diaAtual + " ===\n\n(sem eventos)\n"
                );
            }

            StringBuilder sb = new StringBuilder();
            sb.append("=== EVENTOS DO DIA ").append(diaAtual).append(" ===\n\n");

            int totalEventos = 0;

            for (var entry : eventosDiaAtual.entrySet()) {
                totalEventos += appendEventosProduto(sb, entry.getKey(), entry.getValue());
            }

            sb.append("Total: ").append(totalEventos).append(" eventos\n");

            return RespostaDTO.sucesso(sb.toString());

        } finally {
            lock.readLock().unlock();
        }
    }

    private int appendEventosProduto(StringBuilder sb, int produtoID, List<Evento> eventos) {
        sb.append("Produto ").append(produtoID)
          .append(" (").append(eventos.size()).append(" eventos):\n");

        int i = 1;
        for (Evento e : eventos) {
            sb.append(String.format(
                "  %d. Qtd: %d - Preço: %.2f€ (Volume: %.2f€)\n",
                i++, e.getQuantidade(), e.getPreco(), e.getVolume()
            ));
        }

        sb.append("\n");
        return eventos.size();
    }

    /**
    * Avança para novo dia com I/O FORA DO LOCK
    * 
    * - Copia dados COM lock (rápido)
    * - Faz I/O SEM lock (não paralisa servidor)
    * - Rollback automático em caso de falha
    */
    @Override
    public RespostaDTO novoDia() throws EventoException {
        // Variáveis para backup
        Map<Integer, List<Evento>> backup;
        int diaAnterior;
        
        // CÓPIA RÁPIDA COM LOCK
        lock.writeLock().lock();
        try {
            diaAnterior = diaAtual;

            // Cópia profunda (para segurança)
            backup = new HashMap<>();
            for (Map.Entry<Integer, List<Evento>> entry : eventosDiaAtual.entrySet()) {
                backup.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }

            diaAtual++;

            // Sinalizar todas as notificações (dia acabou)
            for (ConditionCounter cc : condsProduto.values()) {
                cc.c.signalAll();
            }
            condsProduto.clear();

            // Limpar eventos
            eventosDiaAtual.clear();
            lastProductID = -1;
            consecutiveCount = 0;

            System.out.println("Novo dia iniciado: " + diaAtual);

        } finally {
            lock.writeLock().unlock();
        }

        // I/O SEM LOCK (NÃO PARALISA SERVIDOR)
        try {
            // Persistir eventos do dia anterior
            if (!backup.isEmpty()) {
                eventoRepository.salvarEventosDia(diaAnterior, backup);
                System.out.println("Eventos do dia " + diaAnterior + " persistidos (" + 
                                 backup.size() + " produtos)");
            }

            // Limpar cache fora da janela D
            if (cacheManager != null) {
                int diaForaDaJanela = diaAtual - D - 1;
                if (diaForaDaJanela >= 0) {
                    cacheManager.limparDia(diaForaDaJanela);
                    System.out.println("Dia " + diaForaDaJanela + " saiu da janela (D=" + D + ")");
                }
            }

            return RespostaDTO.sucesso("Novo dia iniciado: " + diaAtual);

        } catch (Exception e) {
            // ROLLBACK EM CASO DE ERRO
            lock.writeLock().lock();
            try {
                System.err.println("ERRO ao persistir dia " + diaAnterior + ": " + e.getMessage());
                System.err.println("Executando rollback...");

                diaAtual = diaAnterior;
                eventosDiaAtual.clear();
                eventosDiaAtual.putAll(backup);

                System.err.println("Rollback completo. Dia atual: " + diaAtual);

            } finally {
                lock.writeLock().unlock();
            }

            throw new EventoException("Erro ao avançar dia (rollback executado): " + e.getMessage(), e);
        }
    }
    
    /**
     * Filtra eventos de um dia específico por conjunto de produtos
     */
    public RespostaDTO filtrarEventos(FiltrarEventosDTO dto) throws EventoException {
        validarFiltro(dto);

        int diaReal = diaAtual - dto.getDiaAnterior();

        if (diaReal < 0) {
            throw new EventoException("Dia requisitado ainda não ocorreu");
        }

        // Carregar eventos do dia
        Map<Integer, List<Evento>> eventosDia = eventoRepository.carregarEventosDia(diaReal);

        // Filtrar apenas produtos solicitados
        Map<Integer, List<EventosFiltradosDTO.EventoCompacto>> eventosFiltrados = new HashMap<>();

        for (Integer produtoID : dto.getProdutosIDs()) {
            List<Evento> eventos = eventosDia.get(produtoID);

            if (eventos != null && !eventos.isEmpty()) {
                // Converter para formato compacto
                List<EventosFiltradosDTO.EventoCompacto> compactos = eventos.stream()
                    .map(e -> new EventosFiltradosDTO.EventoCompacto(
                        e.getQuantidade(), 
                        e.getPreco()
                    ))
                    .collect(Collectors.toList());
                
                eventosFiltrados.put(produtoID, compactos);
            }
        }

        EventosFiltradosDTO resultado = new EventosFiltradosDTO(eventosFiltrados, diaReal);

        String mensagem = String.format(
            "Eventos do dia %d filtrados: %d produtos, %d eventos",
            diaReal, resultado.getEventosPorProduto().size(), resultado.getTotalEventos()
        );

        System.out.println(mensagem);
        return RespostaDTO.sucesso(mensagem, resultado);
    }

    public int getDiaAtual() {
        lock.readLock().lock();
        try {
            return diaAtual;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Obtém eventos do dia atual para um conjunto de produtos
     */
    public Map<Integer, List<Evento>> obterEventosDiaAtual(Set<Integer> produtosIDs) {
        lock.readLock().lock();
        try {
            Map<Integer, List<Evento>> resultado = new HashMap<>();
            for (Integer produtoID : produtosIDs) {
                List<Evento> eventos = eventosDiaAtual.get(produtoID);
                if (eventos != null && !eventos.isEmpty()) {
                    resultado.put(produtoID, new ArrayList<>(eventos));
                }
            }
            return resultado;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    private void validarEvento(EventoDTO dto) throws EventoException {
        if (dto == null) {
            throw new EventoException("Dados do evento não fornecidos");
        }
        if (dto.getProdutoID() <= 0) {
            throw new EventoException("ID de produto inválido");
        }
        if (dto.getQuantidade() <= 0) {
            throw new EventoException("Quantidade deve ser positiva");
        }
        if (dto.getPreco() <= 0) {
            throw new EventoException("Preço deve ser positivo");
        }
    }

    private void validarFiltro(FiltrarEventosDTO dto) throws EventoException {
        if (dto == null) {
            throw new EventoException("Dados de filtro não fornecidos");
        }
        if (dto.getProdutosIDs() == null || dto.getProdutosIDs().isEmpty()) {
            throw new EventoException("Conjunto de produtos vazio");
        }
        if (dto.getDiaAnterior() < 1 || dto.getDiaAnterior() > D) {
            throw new EventoException("Dia anterior deve estar entre 1 e " + D);
        }
    }
}