package client.connection;

import java.io.*;
import java.net.Socket;

/**
 * Wrapper para uma conexão TCP reutilizável
 * Gerida por ConnectionPool
 */
public class PooledConnection implements AutoCloseable {
    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final ConnectionPool pool;
    
    private volatile boolean valid;
    private long lastUsed;
    
    PooledConnection(String host, int porta, ConnectionPool pool) throws IOException {
        this.socket = new Socket(host, porta);
        this.input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        this.pool = pool;
        this.valid = true;
        this.lastUsed = System.currentTimeMillis();
    }
    
    /**
     * Obtém stream de entrada
     */
    public DataInputStream getInputStream() {
        updateLastUsed();
        return input;
    }
    
    /**
     * Obtém stream de saída
     */
    public DataOutputStream getOutputStream() {
        updateLastUsed();
        return output;
    }
    
    /**
     * Verifica se conexão está válida
     */
    public boolean isValid() {
        if (!valid) return false;
        
        try {
            // Verificar se socket está conectado e não fechado
            return socket.isConnected() && 
                   !socket.isClosed() && 
                   !socket.isInputShutdown() && 
                   !socket.isOutputShutdown();
        } catch (Exception e) {
            valid = false;
            return false;
        }
    }
    
    /**
     * Marca conexão como inválida
     */
    public void invalidate() {
        valid = false;
    }
    
    /**
     * Retorna conexão ao pool (implementação de AutoCloseable)
     */
    @Override
    public void close() {
        // NÃO fecha o socket físico, apenas retorna ao pool
        pool.releaseConnection(this);
    }
    
    /**
     * Fecha fisicamente a conexão
     * Chamado apenas pelo pool quando descarta a conexão
     */
    void closePhysical() {
        valid = false;
        
        try {
            if (input != null) input.close();
        } catch (IOException e) {
            // Ignorar
        }
        
        try {
            if (output != null) output.close();
        } catch (IOException e) {
            // Ignorar
        }
        
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignorar
        }
    }
    
    /**
     * Obtém endereço remoto
     */
    public String getRemoteAddress() {
        return socket.getInetAddress() + ":" + socket.getPort();
    }
    
    /**
     * Tempo desde última utilização (millis)
     */
    public long getIdleTime() {
        return System.currentTimeMillis() - lastUsed;
    }
    
    private void updateLastUsed() {
        lastUsed = System.currentTimeMillis();
    }
    
    @Override
    public String toString() {
        return String.format("PooledConnection{addr=%s, valid=%b, idle=%dms}",
            getRemoteAddress(), valid, getIdleTime());
    }
}