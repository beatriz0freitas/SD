package server.business.services;

import common.dto.EventoDTO;
import common.dto.NotificacaoDTO;
import common.dto.RespostaDTO;
import common.exceptions.EventoException;
import common.interfaces.IServicoEventos;
import server.business.domain.Evento;
import server.data.cache.CacheManager;
import server.data.repository.IEventoRepository;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Serviço de gestão de eventos com sistema de notificações assíncronas
 */
public class ServicoEventos implements IServicoEventos {
    private final IEventoRepository eventoRepository;
    private final CacheManager cacheManager;
    private final NotificationManager notificationManager;
    private final int D;
    
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private int diaAtual;
    private final Map<Integer, List<Evento>> eventosDiaAtual = new HashMap<>();
    
    // Estado para vendas consecutivas
    private int lastProductID = -1;
    private int consecutiveCount = 0;
    
    public ServicoEventos(IEventoRepository eventoRepository, CacheManager cacheManager, int D) {
        this.eventoRepository = eventoRepository;
        this.cacheManager = cacheManager;
        this.notificationManager = new NotificationManager();
        this.D = D;
        this.diaAtual = eventoRepository.obterUltimoDia() + 1;
        
        System.out.println("ServicoEventos iniciado no dia: " + diaAtual + " (Janela D=" + D + ")");
    }
    
    @Override
    public RespostaDTO registrarEvento(EventoDTO dto) throws EventoException {
        validarEvento(dto);
        
        Evento evento = new Evento(dto.getProdutoID(), dto.getQuantidade(), dto.getPreco());
        
        lock.writeLock().lock();
        try {
            // Adicionar evento ao dia atual
            List<Evento> lista = eventosDiaAtual.computeIfAbsent(
                dto.getProdutoID(), k -> new ArrayList<>()
            );
            lista.add(evento);
            
            // Atualizar contadores de vendas consecutivas
            if (lastProductID == dto.getProdutoID()) {
                consecutiveCount++;
            } else {
                lastProductID = dto.getProdutoID();
                consecutiveCount = 1;
            }
            
            // Notificar sistema de notificações
            notificationManager.notificarEvento(
                dto.getProdutoID(), 
                diaAtual, 
                eventosDiaAtual,
                lastProductID, 
                consecutiveCount
            );
            
        } finally {
            lock.writeLock().unlock();
        }
        
        System.out.println(String.format(
            "Evento registrado: Produto=%d, Qtd=%d, Preço=%.2f (Dia %d)",
            dto.getProdutoID(), dto.getQuantidade(), dto.getPreco(), diaAtual
        ));
        
        return RespostaDTO.sucesso("Evento registado com sucesso");
    }
    
    @Override
    public RespostaDTO notificarVendaEspecifica(NotificacaoDTO notificacao) throws EventoException {
        int produtoID1 = notificacao.getArg1();
        int produtoID2 = notificacao.getArg2();
        
        lock.readLock().lock();
        int diaSnapshot = diaAtual;
        lock.readLock().unlock();
        
        try {
            // Registar interesse na notificação
            final boolean[] notified = {false};
            final String[] message = {null};
            
            notificationManager.registrarVendaEspecifica(
                produtoID1, 
                produtoID2, 
                diaSnapshot,
                msg -> {
                    notified[0] = true;
                    message[0] = msg;
                }
            );
            
            // Verificar se já foi satisfeita
            lock.readLock().lock();
            try {
                if (diaAtual != diaSnapshot) {
                    return RespostaDTO.erro("Dia avançou, notificação cancelada");
                }
                
                if (eventosDiaAtual.containsKey(produtoID1) && 
                    eventosDiaAtual.containsKey(produtoID2)) {
                    return RespostaDTO.sucesso("Produtos já vendidos no dia atual!");
                }
            } finally {
                lock.readLock().unlock();
            }
            
            // Aguardar notificação ou timeout
            long timeout = System.currentTimeMillis() + 60000; // 60s timeout
            while (!notified[0] && System.currentTimeMillis() < timeout) {
                Thread.sleep(100);
                
                // Verificar se dia mudou
                lock.readLock().lock();
                try {
                    if (diaAtual != diaSnapshot) {
                        return RespostaDTO.erro("Dia avançou, notificação cancelada");
                    }
                } finally {
                    lock.readLock().unlock();
                }
            }
            
            if (notified[0]) {
                return RespostaDTO.sucesso(message[0]);
            } else {
                return RespostaDTO.erro("Timeout aguardando vendas específicas");
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventoException("Espera interrompida", e);
        }
    }
    
    @Override
    public RespostaDTO notificarVendasConsecutivas(NotificacaoDTO notificacao) throws EventoException {
        int produtoID = notificacao.getArg1();
        int n = notificacao.getArg2();
        
        lock.readLock().lock();
        int diaSnapshot = diaAtual;
        
        // Verificar se já atingiu o objetivo
        if (lastProductID == produtoID && consecutiveCount >= n) {
            lock.readLock().unlock();
            return RespostaDTO.sucesso("Produto já atingiu " + n + " vendas consecutivas!");
        }
        lock.readLock().unlock();
        
        try {
            final boolean[] notified = {false};
            final String[] message = {null};
            
            notificationManager.registrarVendasConsecutivas(
                produtoID, 
                n, 
                diaSnapshot,
                msg -> {
                    notified[0] = true;
                    message[0] = msg;
                }
            );
            
            // Aguardar notificação
            long timeout = System.currentTimeMillis() + 60000; // 60s timeout
            while (!notified[0] && System.currentTimeMillis() < timeout) {
                Thread.sleep(100);
                
                lock.readLock().lock();
                try {
                    if (diaAtual != diaSnapshot) {
                        return RespostaDTO.erro("Dia avançou, notificação cancelada");
                    }
                } finally {
                    lock.readLock().unlock();
                }
            }
            
            if (notified[0]) {
                return RespostaDTO.sucesso(message[0]);
            } else {
                return RespostaDTO.erro("Timeout aguardando vendas consecutivas");
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventoException("Espera interrompida", e);
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
                sb.append("Produto ").append(entry.getKey())
                  .append(" (").append(entry.getValue().size()).append(" eventos):\n");
                
                int i = 1;
                for (Evento e : entry.getValue()) {
                    sb.append(String.format(
                        "  %d. Qtd: %d - Preço: %.2f€ (Volume: %.2f€)\n",
                        i++, e.getQuantidade(), e.getPreco(), e.getVolume()
                    ));
                    totalEventos++;
                }
                sb.append("\n");
            }
            
            sb.append("Total: ").append(totalEventos).append(" eventos\n");
            
            return RespostaDTO.sucesso(sb.toString());
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public RespostaDTO novoDia() throws EventoException {
        lock.writeLock().lock();
        try {
            int diaAnterior = diaAtual;
            Map<Integer, List<Evento>> backup = new HashMap<>();
            for (Map.Entry<Integer, List<Evento>> entry : eventosDiaAtual.entrySet()) {
                backup.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            
            try {
                // 1. Persistir eventos do dia anterior
                if (!eventosDiaAtual.isEmpty()) {
                    eventoRepository.salvarEventosDia(diaAnterior, eventosDiaAtual);
                    System.out.println("Eventos do dia " + diaAnterior + " persistidos");
                }
                
                // 2. Avançar dia
                diaAtual++;
                
                // 3. Limpar notificações do dia anterior
                notificationManager.limparNotificacoesDia(diaAnterior);
                
                // 4. Limpar agregações fora da janela
                if (cacheManager != null) {
                    int diaForaDaJanela = diaAtual - D - 1;
                    if (diaForaDaJanela >= 0) {
                        cacheManager.limparAgregacoesDia(diaForaDaJanela);
                        cacheManager.removerSerieDaMemoria(diaForaDaJanela);
                        System.out.println("Dia " + diaForaDaJanela + " saiu da janela");
                    }
                }
                
                // 5. Resetar estado
                eventosDiaAtual.clear();
                lastProductID = -1;
                consecutiveCount = 0;
                
                System.out.println("========================================");
                System.out.println("Novo dia iniciado: " + diaAtual);
                System.out.println("========================================");
                
                return RespostaDTO.sucesso("Novo dia iniciado: " + diaAtual);
                
            } catch (Exception e) {
                // Rollback
                diaAtual = diaAnterior;
                eventosDiaAtual.clear();
                eventosDiaAtual.putAll(backup);
                
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
    
    public void shutdown() {
        notificationManager.shutdown();
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