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
import server.data.repository.RepositoryFactory;

/**
 * Serviço de negócio para gestão de eventos
 */
public class ServicoEventos implements IServicoEventos {
    private final IEventoRepository eventoRepository;
    private final CacheManager cacheManager;
    
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private int diaAtual;
    private final Map<Integer, List<Evento>> eventosDiaAtual = new HashMap<>();
    
    public ServicoEventos(int D) {
        this.eventoRepository = RepositoryFactory.getInstance().getEventoRepository();
        this.cacheManager = new CacheManager(eventoRepository, D);
        this.diaAtual = eventoRepository.obterUltimoDia() + 1;
        
        System.out.println("ServicoEventos iniciado no dia: " + diaAtual);
    }
    
    @Override
    public RespostaDTO registrarEvento(EventoDTO dto) throws EventoException {
        // Criar entidade
        Evento evento = new Evento(dto.getProdutoID(), dto.getQuantidade(), dto.getPreco());
        
        // Adicionar ao dia atual
        lock.writeLock().lock();
        try {
            eventosDiaAtual
                .computeIfAbsent(dto.getProdutoID(), k -> new ArrayList<>())
                .add(evento);
        } finally {
            lock.writeLock().unlock();
        }
        
        return RespostaDTO.sucesso("Evento registado com sucesso");
    }
    
    @Override
    public RespostaDTO listarEventosDiaAtual() throws EventoException {
        lock.readLock().lock();
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("=== EVENTOS DO DIA ").append(diaAtual).append(" ===\n\n");
            
            if (eventosDiaAtual.isEmpty()) {
                sb.append("(sem eventos)\n");
            } else {
                for (Map.Entry<Integer, List<Evento>> entry : eventosDiaAtual.entrySet()) {
                    int produtoID = entry.getKey();
                    List<Evento> eventos = entry.getValue();
                    
                    sb.append("Produto ").append(produtoID)
                      .append(" (").append(eventos.size()).append(" eventos):\n");
                    
                    for (int i = 0; i < eventos.size(); i++) {
                        Evento e = eventos.get(i);
                        sb.append(String.format(
                            "  %d. Qtd: %d - Preço: %.2f€ (Volume: %.2f€)\n",
                            i + 1, e.getQuantidade(), e.getPreco(), e.getVolume()
                        ));
                    }
                    sb.append("\n");
                }
            }
            
            return RespostaDTO.sucesso(sb.toString());
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public RespostaDTO novoDia() throws EventoException {
        lock.writeLock().lock();
        try {
            // Backup do estado
            int diaAnterior = diaAtual;
            Map<Integer, List<Evento>> backup = new HashMap<>(eventosDiaAtual);
            
            try {
                // 1. Persistir
                eventoRepository.salvarEventosDia(diaAtual, eventosDiaAtual);
                
                // 2. Limpar cache
                cacheManager.limparDiaAntigo(diaAtual);
                
                // 3. Avançar dia APÓS sucesso
                diaAtual++;
                eventosDiaAtual.clear();
                
                System.out.println("Novo dia iniciado: " + diaAtual);
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
}