package client;

import common.dto.RespostaDTO;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.locks.ReentrantLock;
import middleware.proto.ProtocoloHandler;
import middleware.proto.Requisicao;

/**
 * Middleware do lado do cliente
 * Gerencia comunicação com servidor
 */
public class ClienteMiddleware {
    private final String host;
    private final int porta;
    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;
    private final ProtocoloHandler protocoloHandler;
    private final ReentrantLock lock;
    private boolean conectado;

    public ClienteMiddleware(String host, int porta) {
        this.host = host;
        this.porta = porta;
        this.protocoloHandler = new ProtocoloHandler();
        this.lock = new ReentrantLock(true);
        this.conectado = false;
    }

    public void conectar() throws IOException {
        lock.lock();
        try {
            if (conectado) return;

            socket = new Socket();
            socket.connect(new InetSocketAddress(host, porta));
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());
            conectado = true;

            System.out.println("Conectado ao servidor " + host + ":" + porta);
        } finally {
            lock.unlock();
        }
    }

    public void desconectar() {
        lock.lock();
        try {
            if (input != null) {
                try { input.close(); } catch (IOException ignored) {}
                input = null;
            }
            if (output != null) {
                try { output.close(); } catch (IOException ignored) {}
                output = null;
            }
            if (socket != null && !socket.isClosed()) {
                try { socket.close(); } catch (IOException ignored) {}
            }
            socket = null;
            conectado = false;
            System.out.println("Desconectado do servidor");
        } finally {
            lock.unlock();
        }
    }

    
    public RespostaDTO invocar(byte serviceId, byte methodId, Object parametros) throws IOException {
        lock.lock();
        try {
            if (!conectado || socket.isClosed()) {
                System.out.println("Reconectando...");
                conectar();
            }
    
            // Cria requisição com service_id e method_id para encapsular parâmetros do middleware
            Requisicao requisicao = new Requisicao(serviceId, methodId, parametros);
    
            // Envia requisição
            protocoloHandler.enviar(requisicao, output);
    
            // Recebe resposta
            Object resp = protocoloHandler.receber(input);
            return (RespostaDTO) resp;
    
        } catch (ClassNotFoundException e) {
            throw new IOException("Erro ao deserializar resposta", e);
        } finally {
            lock.unlock();
        }
    }
    
    public boolean isConectado() {
        lock.lock();
        try {
            return conectado && socket != null && !socket.isClosed();
        } finally {
            lock.unlock();
        }
    }
}