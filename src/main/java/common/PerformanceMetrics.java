package common;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Sistema de métricas de performance
 * Thread-safe usando atomic variables e read-write locks
 */
public class PerformanceMetrics {
    private static PerformanceMetrics instance;
    
    // Contadores atômicos para operações de leitura
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final AtomicLong totalCacheHits = new AtomicLong(0);
    private final AtomicLong totalCacheMisses = new AtomicLong(0);
    
    // Latências (protegidas por locks)
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private long totalLatencyNs = 0;
    private long minLatencyNs = Long.MAX_VALUE;
    private long maxLatencyNs = 0;
    private int latencySamples = 0;
    
    // Timestamps
    private final long startTime;
    
    private PerformanceMetrics() {
        this.startTime = System.currentTimeMillis();
    }
    
    public static synchronized PerformanceMetrics getInstance() {
        if (instance == null) {
            instance = new PerformanceMetrics();
        }
        return instance;
    }
    
    /**
     * Registra uma requisição
     */
    public void recordRequest(boolean success, long latencyNs) {
        totalRequests.incrementAndGet();
        
        if (!success) {
            totalErrors.incrementAndGet();
        }
        
        // Atualizar latências
        lock.writeLock().lock();
        try {
            totalLatencyNs += latencyNs;
            latencySamples++;
            
            if (latencyNs < minLatencyNs) {
                minLatencyNs = latencyNs;
            }
            if (latencyNs > maxLatencyNs) {
                maxLatencyNs = latencyNs;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Registra cache hit
     */
    public void recordCacheHit() {
        totalCacheHits.incrementAndGet();
    }
    
    /**
     * Registra cache miss
     */
    public void recordCacheMiss() {
        totalCacheMisses.incrementAndGet();
    }
    
    /**
     * Obtém snapshot das métricas
     */
    public MetricsSnapshot getSnapshot() {
        lock.readLock().lock();
        try {
            long requests = totalRequests.get();
            long errors = totalErrors.get();
            long cacheHits = totalCacheHits.get();
            long cacheMisses = totalCacheMisses.get();
            
            double avgLatencyMs = latencySamples > 0 
                ? (totalLatencyNs / latencySamples) / 1_000_000.0 
                : 0.0;
            
            double minLatencyMs = minLatencyNs != Long.MAX_VALUE 
                ? minLatencyNs / 1_000_000.0 
                : 0.0;
            
            double maxLatencyMs = maxLatencyNs / 1_000_000.0;
            
            double errorRate = requests > 0 
                ? (errors * 100.0) / requests 
                : 0.0;
            
            double cacheHitRate = (cacheHits + cacheMisses) > 0 
                ? (cacheHits * 100.0) / (cacheHits + cacheMisses) 
                : 0.0;
            
            long uptimeMs = System.currentTimeMillis() - startTime;
            
            double throughput = uptimeMs > 0 
                ? (requests * 1000.0) / uptimeMs 
                : 0.0;
            
            return new MetricsSnapshot(
                requests, errors, errorRate,
                cacheHits, cacheMisses, cacheHitRate,
                avgLatencyMs, minLatencyMs, maxLatencyMs,
                throughput, uptimeMs
            );
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Reseta todas as métricas
     */
    public void reset() {
        totalRequests.set(0);
        totalErrors.set(0);
        totalCacheHits.set(0);
        totalCacheMisses.set(0);
        
        lock.writeLock().lock();
        try {
            totalLatencyNs = 0;
            minLatencyNs = Long.MAX_VALUE;
            maxLatencyNs = 0;
            latencySamples = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Snapshot imutável das métricas
     */
    public static class MetricsSnapshot {
        public final long totalRequests;
        public final long totalErrors;
        public final double errorRate;
        
        public final long cacheHits;
        public final long cacheMisses;
        public final double cacheHitRate;
        
        public final double avgLatencyMs;
        public final double minLatencyMs;
        public final double maxLatencyMs;
        
        public final double throughput;
        public final long uptimeMs;
        
        MetricsSnapshot(long totalRequests, long totalErrors, double errorRate,
                       long cacheHits, long cacheMisses, double cacheHitRate,
                       double avgLatencyMs, double minLatencyMs, double maxLatencyMs,
                       double throughput, long uptimeMs) {
            this.totalRequests = totalRequests;
            this.totalErrors = totalErrors;
            this.errorRate = errorRate;
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
            this.cacheHitRate = cacheHitRate;
            this.avgLatencyMs = avgLatencyMs;
            this.minLatencyMs = minLatencyMs;
            this.maxLatencyMs = maxLatencyMs;
            this.throughput = throughput;
            this.uptimeMs = uptimeMs;
        }
        
        @Override
        public String toString() {
            return String.format(
                "=== MÉTRICAS DE PERFORMANCE ===\n" +
                "Requisições: %d total, %d erros (%.2f%%)\n" +
                "Cache: %d hits, %d misses (%.2f%% hit rate)\n" +
                "Latência: avg=%.2fms, min=%.2fms, max=%.2fms\n" +
                "Throughput: %.2f req/s\n" +
                "Uptime: %s",
                totalRequests, totalErrors, errorRate,
                cacheHits, cacheMisses, cacheHitRate,
                avgLatencyMs, minLatencyMs, maxLatencyMs,
                throughput, formatUptime(uptimeMs)
            );
        }
        
        private String formatUptime(long ms) {
            long seconds = ms / 1000;
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
}