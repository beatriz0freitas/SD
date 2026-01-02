package middleware;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Handler para shutdown gracioso no servidor
 * Notifica todos os clientes antes de fechar
 */
public class ServerShutdownHandler {
    private final Set<Socket> clientesAtivos;
    private final ReentrantLock lock;
    private final Protocolo proto;
    
    public ServerShutdownHandler(Set<Socket> clientesAtivos, ReentrantLock lock) {
        this.clientesAtivos = clientesAtivos;
        this.lock = lock;
        this.proto = new Protocolo();
    }
    
    /**
     * Notifica todos os clientes sobre shutdown
     */
    public void notificarClientes(String razao) {
        lock.lock();
        try {
            ShutdownMessage msg = new ShutdownMessage(razao);
            
            System.out.println("\n========================================");
            System.out.println("Notificando " + clientesAtivos.size() + " clientes sobre shutdown...");
            System.out.println("========================================");
            
            int notificados = 0;
            int falhas = 0;
            
            for (Socket socket : clientesAtivos) {
                try {
                    if (!socket.isClosed()) {
                        DataOutputStream out = 
                            new DataOutputStream(socket.getOutputStream());
                        
                        // Enviar mensagem especial de shutdown
                        Message shutdownMsg = Message.response(-1, msg);
                        proto.enviar(shutdownMsg, out);
                        out.flush();
                        
                        notificados++;
                    }
                } catch (IOException e) {
                    falhas++;
                    // Ignorar erros - cliente pode já estar desconectado
                }
            }
            
            System.out.println("Notificados " + notificados + " clientes");
            if (falhas > 0) {
                System.out.println("Falhas ao notificar: " + falhas);
            }
            
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