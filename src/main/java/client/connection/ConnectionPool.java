package client.connection;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Pool de conexões reutilizáveis para o servidor
 * Thread-safe usando locks e conditions
 * 
 * Permite reutilizar conexões TCP em vez de criar novas a cada pedido
 */
public class ConnectionPool {
    private final String host;
    private final int porta;
    private final int maxConnections;
    private final long connectionTimeout; 
    
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition available = lock.newCondition();
    
    // Conexões disponíveis para reutilização
    private final Queue<PooledConnection> freeConnections = new ArrayDeque<>();
    
    // Conexões atualmente em uso
    private final Set<PooledConnection> usedConnections = new HashSet<>();
    
    private int totalConnections = 0;
    private boolean closed = false;
    
    public ConnectionPool(String host, int porta, int maxConnections) {
        this(host, porta, maxConnections, 30000); // 30s timeout padrão
    }
    
    public ConnectionPool(String host, int porta, int maxConnections, long connectionTimeout) {
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("maxConnections deve ser > 0");
        } 
        this.host = host;
        this.porta = porta;
        this.maxConnections = maxConnections;
        this.connectionTimeout = connectionTimeout;
    }
    
    /**
     * Obtém uma conexão do pool
     * Bloqueia se todas as conexões estiverem em uso (até maxConnections)
     */
    public PooledConnection getConnection() throws IOException, InterruptedException {
        lock.lock();
        try {
            long deadline = System.currentTimeMillis() + connectionTimeout;

            while (true) {
                if (closed) {
                    throw new IOException("Pool fechado");
                }
                
                // 1. Tentar reutilizar conexão livre
                PooledConnection conn = freeConnections.poll();
                if (conn != null) {
                    if (conn.isValid()) {
                        usedConnections.add(conn);
                        return conn;
                    } else {
                        // Conexão inválida, descartar
                        totalConnections--;
                        conn.closePhysical();
                    }
                }
                
                // 2. Criar nova conexão se não atingiu máximo
                if (totalConnections < maxConnections) {
                    PooledConnection newConn = createNewConnection();
                    totalConnections++;
                    usedConnections.add(newConn);
                    return newConn;
                }
                
                // 3. Aguardar com timeout
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    throw new IOException("Timeout aguardando conexão disponível (" + connectionTimeout + "ms)");
                }
                available.await(remaining, TimeUnit.MILLISECONDS);
                }
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Devolve conexão ao pool
     */
    public void releaseConnection(PooledConnection conn) {
        if (conn == null) return;
        
        lock.lock();
        try {
            if (!usedConnections.remove(conn)) {
                // Conexão não pertence a este pool
                return;
            }
            
            if (closed || !conn.isValid()) {
                // Pool fechado ou conexão inválida
                totalConnections--;
                conn.closePhysical();
            } else {
                // Retornar ao pool de conexões livres
                freeConnections.offer(conn);
                available.signal(); // Acordar thread esperando
            }
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Fecha o pool e todas as conexões
     */
    public void close() {
        lock.lock();
        try {
            if (closed) return;
            
            closed = true;
            
            // Fechar conexões livres
            for (PooledConnection conn : freeConnections) {
                conn.closePhysical();
            }
            freeConnections.clear();
            
            // Fechar conexões em uso
            for (PooledConnection conn : usedConnections) {
                conn.closePhysical();
            }
            usedConnections.clear();
            
            totalConnections = 0;
            
            // Acordar threads esperando
            available.signalAll();
            
            System.out.println("ConnectionPool fechado");
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Estatísticas do pool
     */
    public String getStats() {
        lock.lock();
        try {
            return String.format(
                "ConnectionPool{total=%d, free=%d, used=%d, max=%d}",
                totalConnections, freeConnections.size(), 
                usedConnections.size(), maxConnections
            );
        } finally {
            lock.unlock();
        }
    }
    
    // === Métodos Privados ===
    
    private PooledConnection createNewConnection() throws IOException {
        try {
            return new PooledConnection(host, porta, this);
        } catch (IOException e) {
            throw new IOException("Falha ao criar conexão: " + e.getMessage(), e);
        }
    }
    
    /**
     * Remove conexão inválida do pool
     * Chamado internamente quando conexão falha
     */
    void removeInvalidConnection(PooledConnection conn) {
        lock.lock();
        try {
            usedConnections.remove(conn);
            freeConnections.remove(conn);
            totalConnections--;
            conn.closePhysical();
            
            available.signal(); // Acordar thread esperando
            
        } finally {
            lock.unlock();
        }
    }
}