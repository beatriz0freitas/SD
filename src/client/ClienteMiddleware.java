package client;

import common.dto.RespostaDTO;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.locks.ReentrantLock;
import middleware.proto.*;

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
    
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 30000;
    
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
            socket.connect(new InetSocketAddress(host, porta), CONNECT_TIMEOUT);
            socket.setSoTimeout(READ_TIMEOUT);
            
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
            conectado = false; // garantir mesmo se algum close falhar
            System.out.println("Desconectado do servidor");
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Invoca operação remota 
     * 
     * @param operacao nome da operação (ex: "AUTH:LOGIN")
     * @param parametros parâmetros da operação (DTO ou null)
     * @return RespostaDTO do servidor
     */
    public RespostaDTO invocar(String operacao, Object parametros) throws IOException {
        lock.lock();
        try {
            // 1. Verificar/reconectar
            if (!conectado || socket.isClosed()) {
                System.out.println("Reconectando...");
                conectar(); //TODO aplicar tentativas
            }
            
            // 2. Criar requisição
            Requisicao requisicao = new Requisicao(operacao, parametros);
            
            // 3. Enviar requisição
            protocoloHandler.enviar(requisicao, output);
            
            // 4. Receber resposta (sempre RespostaDTO)
            return protocoloHandler.receber(input, RespostaDTO.class);
            
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