package server.business.services;

import common.dto.EventoDTO;
import common.dto.RespostaDTO;
import common.exceptions.EventoException;
import common.interfaces.IServicoEventos;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
        } finally {
            lock.writeLock().unlock();
        }

        
        System.out.println(String.format("Evento registrado: Produto=%d, Qtd=%d, Preço=%.2f (Dia %d)",
            dto.getProdutoID(), dto.getQuantidade(), dto.getPreco(), diaAtual));
        
        return RespostaDTO.sucesso("Evento registado com sucesso");
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

    @Override
    public RespostaDTO novoDia() throws EventoException {
        lock.writeLock().lock();
        try {
            // Backup do estado
            int diaAnterior = diaAtual;
            Map<Integer, List<Evento>> backup = new HashMap<>();
            for (Map.Entry<Integer, List<Evento>> entry : eventosDiaAtual.entrySet()) {
                backup.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            
            try {
                // 1. Persistir eventos do dia anterior
                if (!eventosDiaAtual.isEmpty()) {
                    eventoRepository.salvarEventosDia(diaAnterior, eventosDiaAtual);
                    System.out.println("Eventos do dia " + diaAnterior + " persistidos no disco");
                }
                
                // 2. Avançar dia
                diaAtual++;
                
                // 3. Limpar agregações que saem da janela D
                if (cacheManager != null) {
                    // Se estamos no dia diaAtual e a janela é D dias,
                    // o dia mais antigo válido é: diaAtual - D
                    // Logo, dias anteriores a (diaAtual - D) devem ser removidos
                    int diaForaDaJanela = diaAtual - D - 1;
                
                    if (diaForaDaJanela >= 0) {
                        cacheManager.limparAgregacoesDia(diaForaDaJanela);
                        cacheManager.removerSerieDaMemoria(diaForaDaJanela);
                        System.out.println("Dia " + diaForaDaJanela + " saiu da janela (D=" + D + ")");
                    }
                }
                
                eventosDiaAtual.clear();
                
                System.out.println("========================================");
                System.out.println("Novo dia iniciado: " + diaAtual);
                System.out.println("========================================");
                
                return RespostaDTO.sucesso("Novo dia iniciado: " + diaAtual);
                
            } catch (Exception e) {
                // Rollback em caso de erro
                diaAtual = diaAnterior;
                eventosDiaAtual.clear();
                eventosDiaAtual.putAll(backup);
                
                System.err.println("Erro ao avançar dia: " + e.getMessage());
                throw new EventoException("Erro ao avançar dia: " + e.getMessage(), e);
            }
            
        } finally {
            lock.writeLock().unlock();
        }
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
}