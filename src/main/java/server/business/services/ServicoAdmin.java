package server.business.services;

import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import common.dto.RespostaDTO;
import common.exceptions.AdminException;
import common.interfaces.IServicoAdmin;
import common.PerformanceMetrics;
import server.business.domain.Usuario;
import server.data.repository.IUsuarioRepository;
import server.data.repository.RepositoryFactory;

/**
 * Serviço administrativo expandido com métricas
 */
public class ServicoAdmin implements IServicoAdmin {
    private final IUsuarioRepository usuarioRepository;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final PerformanceMetrics metrics;
    
    // Estatísticas do servidor
    private long startTime;
    
    public ServicoAdmin() {
        this.usuarioRepository = RepositoryFactory.getInstance().getUsuarioRepository();
        this.metrics = PerformanceMetrics.getInstance();
        this.startTime = System.currentTimeMillis();
    }
    
    @Override
    public RespostaDTO listarClientes() throws AdminException {
        lock.readLock().lock();
        try {
            List<Usuario> usuarios = usuarioRepository.listarTodos();
            
            StringBuilder sb = new StringBuilder();
            sb.append("=== CLIENTES REGISTADOS ===\n");
            sb.append("Total: ").append(usuarios.size()).append("\n\n");
            
            for (Usuario u : usuarios) {
                sb.append("- ").append(u.getUsername()).append("\n");
            }
            
            return RespostaDTO.sucesso(sb.toString());
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public RespostaDTO obterEstatisticas() throws AdminException {
        lock.readLock().lock();
        try {
            int totalUsuarios = usuarioRepository.contarUtilizadores();
            long uptime = (System.currentTimeMillis() - startTime) / 1000;
            
            // Obter métricas de performance
            PerformanceMetrics.MetricsSnapshot metricsSnapshot = metrics.getSnapshot();
            
            StringBuilder sb = new StringBuilder();
            sb.append("=== ESTATÍSTICAS DO SISTEMA ===\n\n");
            sb.append("Utilizadores registados: ").append(totalUsuarios).append("\n");
            sb.append("Uptime: ").append(formatUptime(uptime)).append("\n\n");
            
            sb.append("=== PERFORMANCE ===\n");
            sb.append("Requisições: ").append(metricsSnapshot.totalRequests).append("\n");
            sb.append("Erros: ").append(metricsSnapshot.totalErrors).append(" (")
              .append(String.format("%.2f%%", metricsSnapshot.errorRate)).append(")\n");
            sb.append("Throughput: ").append(String.format("%.2f req/s", metricsSnapshot.throughput)).append("\n");
            sb.append("Latência média: ").append(String.format("%.2fms", metricsSnapshot.avgLatencyMs)).append("\n");
            sb.append("Latência min: ").append(String.format("%.2fms", metricsSnapshot.minLatencyMs)).append("\n");
            sb.append("Latência max: ").append(String.format("%.2fms", metricsSnapshot.maxLatencyMs)).append("\n\n");
            
            sb.append("=== CACHE ===\n");
            sb.append("Cache hits: ").append(metricsSnapshot.cacheHits).append("\n");
            sb.append("Cache misses: ").append(metricsSnapshot.cacheMisses).append("\n");
            sb.append("Hit rate: ").append(String.format("%.2f%%", metricsSnapshot.cacheHitRate)).append("\n");
            
            return RespostaDTO.sucesso(sb.toString());
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Obtém métricas detalhadas (para monitorização)
     */
    public RespostaDTO obterMetricas() throws AdminException {
        PerformanceMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
        return RespostaDTO.sucesso("Métricas obtidas", snapshot);
    }
    
    /**
     * Remove utilizador (nova funcionalidade)
     */
    public RespostaDTO removerUtilizador(String username) throws AdminException {
        if (username == null || username.isBlank()) {
            throw new AdminException("Username inválido");
        }
        
        lock.writeLock().lock();
        try {
            if (!usuarioRepository.existe(username)) {
                throw new AdminException("Utilizador não encontrado: " + username);
            }
            
            usuarioRepository.deletar(username);
            
            return RespostaDTO.sucesso("Utilizador " + username + " removido com sucesso");
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Limpa todos os utilizadores (exceto admin)
     */
    public RespostaDTO limparUtilizadores() throws AdminException {
        lock.writeLock().lock();
        try {
            List<Usuario> usuarios = usuarioRepository.listarTodos();
            int removidos = 0;
            
            for (Usuario u : usuarios) {
                if (!"admin".equals(u.getUsername())) {
                    usuarioRepository.deletar(u.getUsername());
                    removidos++;
                }
            }
            
            return RespostaDTO.sucesso("Removidos " + removidos + " utilizadores");
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Reseta estatísticas
     */
    public RespostaDTO resetarEstatisticas() {
        lock.writeLock().lock();
        try {
            metrics.reset();
            startTime = System.currentTimeMillis();
            
            return RespostaDTO.sucesso("Estatísticas resetadas");
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Obtém informações detalhadas do sistema
     */
    public RespostaDTO obterInfoSistema() {
        lock.readLock().lock();
        try {
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory() / (1024 * 1024); // MB
            long freeMemory = runtime.freeMemory() / (1024 * 1024);
            long usedMemory = totalMemory - freeMemory;
            
            StringBuilder sb = new StringBuilder();
            sb.append("=== INFORMAÇÕES DO SISTEMA ===\n\n");
            sb.append("Memória Total: ").append(totalMemory).append(" MB\n");
            sb.append("Memória Usada: ").append(usedMemory).append(" MB\n");
            sb.append("Memória Livre: ").append(freeMemory).append(" MB\n");
            sb.append("Processadores: ").append(runtime.availableProcessors()).append("\n");
            sb.append("Threads Ativos: ").append(Thread.activeCount()).append("\n");
            
            return RespostaDTO.sucesso(sb.toString());
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // === Utilitários ===
    
    private String formatUptime(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        if (days > 0) {
            return String.format("%dd %dh %dm %ds", days, hours, minutes, secs);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }
}