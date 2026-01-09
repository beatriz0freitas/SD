package client.connection;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


public class ConnectionPool {
    private final String host;
    private final int porta;
    private final int maxConnections;
    private final long connectionTimeout; 
    
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition available = lock.newCondition();
    
    
    private final Queue<PooledConnection> freeConnections = new ArrayDeque<>();
    
    
    private final Set<PooledConnection> usedConnections = new HashSet<>();
    
    private int totalConnections = 0;
    private boolean closed = false;
    
    public ConnectionPool(String host, int porta, int maxConnections) {
        this(host, porta, maxConnections, 30000); 
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
    
    
    public PooledConnection getConnection() throws IOException, InterruptedException {
        lock.lock();
        try {
            long deadline = System.currentTimeMillis() + connectionTimeout;

            while (true) {
                if (closed) {
                    throw new IOException("Pool fechado");
                }
                
                
                PooledConnection conn = freeConnections.poll();
                if (conn != null) {
                    if (conn.isValid()) {
                        usedConnections.add(conn);
                        return conn;
                    } else {
                        
                        totalConnections--;
                        conn.closePhysical();
                    }
                }
                
                
                if (totalConnections < maxConnections) {
                    PooledConnection newConn = createNewConnection();
                    totalConnections++;
                    usedConnections.add(newConn);
                    return newConn;
                }
                
                
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
    
    
    public void releaseConnection(PooledConnection conn) {
        if (conn == null) return;
        
        lock.lock();
        try {
            if (!usedConnections.remove(conn)) {
                
                return;
            }
            
            if (closed || !conn.isValid()) {
                
                totalConnections--;
                conn.closePhysical();
            } else {
                
                freeConnections.offer(conn);
                available.signal(); 
            }
            
        } finally {
            lock.unlock();
        }
    }
    
    
    public void close() {
        lock.lock();
        try {
            if (closed) return;
            
            closed = true;
            
            
            for (PooledConnection conn : freeConnections) {
                conn.closePhysical();
            }
            freeConnections.clear();
            
            
            for (PooledConnection conn : usedConnections) {
                conn.closePhysical();
            }
            usedConnections.clear();
            
            totalConnections = 0;
            
            
            available.signalAll();
            
            System.out.println("ConnectionPool fechado");
            
        } finally {
            lock.unlock();
        }
    }
    
    
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
    
    
    
    private PooledConnection createNewConnection() throws IOException {
        try {
            return new PooledConnection(host, porta, this);
        } catch (IOException e) {
            throw new IOException("Falha ao criar conexão: " + e.getMessage(), e);
        }
    }
    
    
    void removeInvalidConnection(PooledConnection conn) {
        lock.lock();
        try {
            usedConnections.remove(conn);
            freeConnections.remove(conn);
            totalConnections--;
            conn.closePhysical();
            
            available.signal(); 
            
        } finally {
            lock.unlock();
        }
    }
}