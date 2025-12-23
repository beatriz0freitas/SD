package client;

import common.dto.RespostaDTO;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;
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
    private final ProtocoloHandler protocoloHandler;
    private final ReentrantLock writeLock;
    private final AtomicLong requestCounter;
    
    private Socket socket;
    private DataOutputStream output;
    private Demultiplexer demultiplexer;
    private Thread demuxThread;
    private boolean conectado;

    public ClienteMiddleware(String host, int porta) {
        this.host = host;
        this.porta = porta;
        this.protocoloHandler = new ProtocoloHandler();
        this.writeLock = new ReentrantLock(true);
        this.requestCounter = new AtomicLong(0);
        this.conectado = false;
    }

    public void conectar() throws IOException {
        writeLock.lock();
        try {
            if (conectado) return;

            socket = new Socket(host, porta);
            DataInputStream input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());
            
            demultiplexer = new Demultiplexer(input);
            demuxThread = new Thread(demultiplexer);
            demuxThread.start();
            
            conectado = true;
            System.out.println("Conectado ao servidor " + host + ":" + porta);
        } finally {
            writeLock.unlock();
        }
    }

    public void desconectar() {
        writeLock.lock();
        try {
            conectado = false;
            
            if (demultiplexer != null) {
                demultiplexer.parar();
            }
            
            fecharRecursos();
            
            System.out.println("Desconectado do servidor");
        } finally {
            writeLock.unlock();
        }
    }

    public RespostaDTO invocar(byte serviceId, byte methodId, Object parametros) throws IOException {
        if (!conectado || socket.isClosed()) {
            desconectar();
            conectar(); 
        }

        try {
            long tag = requestCounter.incrementAndGet();
            Requisicao req = new Requisicao(serviceId, methodId, parametros, tag);

            writeLock.lock();
            try {
                protocoloHandler.enviar(req, output);
            } finally {
                writeLock.unlock();
            }

            Object resp = demultiplexer.aguardar(tag);
            return (RespostaDTO) resp;

        } catch (Exception e) {
            throw new IOException("Erro ao invocar: " + e.getMessage(), e);
        }
    }

    public boolean isConectado() {
        return conectado && socket != null && !socket.isClosed();
    }

    private void fecharRecursos() {
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) {}
            socket = null;
        }
        output = null;
        demultiplexer = null;
        demuxThread = null;
    }
}