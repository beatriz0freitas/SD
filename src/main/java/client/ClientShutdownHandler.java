package client;

import middleware.ShutdownMessage;

/**
 * Handler para shutdown no cliente
 * Detecta quando servidor fecha e limpa recursos
 */
public class ClientShutdownHandler {
    private volatile boolean serverShutdown = false;
    private final Runnable onShutdownCallback;
    
    public ClientShutdownHandler(Runnable onShutdownCallback) {
        this.onShutdownCallback = onShutdownCallback;
    }
    
    /**
     * Processa mensagem recebida do servidor
     * Retorna true se é mensagem de shutdown
     */
    public boolean processMessage(Object payload) {
        if (payload instanceof ShutdownMessage) {
            ShutdownMessage shutdownMsg = (ShutdownMessage) payload;
            
            System.out.println("\n========================================");
            System.out.println("         SERVIDOR ENCERRADO");
            System.out.println("========================================");
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