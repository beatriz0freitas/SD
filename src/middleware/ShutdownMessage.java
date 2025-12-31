package middleware;

import java.io.Serializable;

/**
 * Mensagem especial para notificar shutdown do servidor
 */
public class ShutdownMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String razao;
    private final long timestamp;
    
    public ShutdownMessage(String razao) {
        this.razao = razao;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getRazao() {
        return razao;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return "ShutdownMessage{razao='" + razao + "', timestamp=" + timestamp + "}";
    }
}

/**
 * Handler para shutdown gracioso no servidor
 * Notifica todos os clientes antes de fechar
 */
class ServerShutdownHandler {
    private final java.util.Set<java.net.Socket> clientesAtivos;
    private final java.util.concurrent.locks.ReentrantLock lock;
    
    public ServerShutdownHandler(java.util.Set<java.net.Socket> clientesAtivos,  java.util.concurrent.locks.ReentrantLock lock) {
        this.clientesAtivos = clientesAtivos;
        this.lock = lock;
    }
    
    /**
     * Notifica todos os clientes sobre shutdown
     */
    public void notificarClientes(String razao) {
        lock.lock();
        try {
            ShutdownMessage msg = new ShutdownMessage(razao);
            Protocolo proto = new Protocolo();
            
            System.out.println("Notificando " + clientesAtivos.size() + " clientes sobre shutdown...");
            
            int notificados = 0;
            for (java.net.Socket socket : clientesAtivos) {
                try {
                    java.io.DataOutputStream out = 
                        new java.io.DataOutputStream(socket.getOutputStream());
                    
                    // Enviar mensagem de shutdown
                    proto.enviar(msg, out);
                    out.flush();
                    
                    notificados++;
                    
                } catch (Exception e) {
                    // Ignorar erros - cliente pode já estar desconectado
                }
            }
            
            System.out.println("Notificados " + notificados + " clientes");
            
            // Dar tempo para clientes receberem
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
        } finally {
            lock.unlock();
        }
    }
}

/**
 * Handler para shutdown no cliente
 * Detecta quando servidor fecha e limpa recursos
 */
class ClientShutdownHandler {
    private volatile boolean serverShutdown = false;
    private final Runnable onShutdownCallback;
    
    public ClientShutdownHandler(Runnable onShutdownCallback) {
        this.onShutdownCallback = onShutdownCallback;
    }
    
    /**
     * Processa mensagem recebida do servidor
     * Retorna true se é mensagem de shutdown
     */
    public boolean processMessage(Object msg) {
        if (msg instanceof ShutdownMessage) {
            ShutdownMessage shutdownMsg = (ShutdownMessage) msg;
            
            System.out.println("\n========================================");
            System.out.println("SERVIDOR ENCERRADO");
            System.out.println("Razão: " + shutdownMsg.getRazao());
            System.out.println("========================================\n");
            
            serverShutdown = true;
            
            // Executar callback de limpeza
            if (onShutdownCallback != null) {
                try {
                    onShutdownCallback.run();
                } catch (Exception e) {
                    System.err.println("Erro ao processar shutdown: " + e.getMessage());
                }
            }
            
            return true;
        }
        
        return false;
    }
    
    public boolean isServerShutdown() {
        return serverShutdown;
    }
}