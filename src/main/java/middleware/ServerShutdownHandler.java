package middleware;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Handler para shutdown gracioso no servidor.
 *
 * IMPORTANTE (para evitar corrupção no stream):
 * - Este handler NÃO deve escrever nos sockets diretamente enquanto o ClientHandler
 *   também está a escrever, porque não partilham o mesmo lock de escrita.
 *
 * Versão simples e segura:
 * - apenas marca shutdown enviando um "frame" mínimo por conexão, com tag=-1
 * - não envia objeto/DTO (payload null)
 *
 * O cliente deteta shutdown por (msg.getTag() == -1).
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

    public void notificarClientes(String razao) {
        // "razao" fica só para logging do servidor, não vai na rede (para não criar codec/DTO extra)
        lock.lock();
        try {
            System.out.println("\n========================================");
            System.out.println("Notificando " + clientesAtivos.size() + " clientes sobre shutdown...");
            System.out.println("Razão: " + razao);
            System.out.println("========================================");

            int notificados = 0;
            int falhas = 0;

            for (Socket socket : clientesAtivos) {
                try {
                    if (!socket.isClosed() && socket.isConnected()) {
                        // Nota: idealmente o socket já deveria estar a usar um BufferedOutputStream
                        // no ClientHandler. Aqui mantemos simples e funcional.
                        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                        // tag=-1, payload null
                        Message shutdownMsg = Message.response(-1L, null);

                        proto.enviar(shutdownMsg, out);
                        notificados++;
                    }
                } catch (IOException e) {
                    falhas++;
                }
            }

            System.out.println("Notificados " + notificados + " clientes");
            if (falhas > 0) System.out.println("Falhas ao notificar: " + falhas);

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

        } finally {
            lock.unlock();
        }
    }
}