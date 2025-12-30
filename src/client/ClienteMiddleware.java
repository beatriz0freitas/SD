package client;

import common.dto.RespostaDTO;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import middleware.Message;
import middleware.Protocolo;

public class ClienteMiddleware {
    private final String host;
    private final int porta;
    private final Protocolo protocolo;
    private final ReentrantLock lockEscrita;
    private final AtomicLong contadorPedidos;
    
    private Socket socket;
    private DataOutputStream saida;
    private Demultiplexer demux;
    private Thread threadDemux;
    private volatile boolean conectado;
    
    public ClienteMiddleware(String host, int porta) {
        this.host = host;
        this.porta = porta;
        this.protocolo = new Protocolo();
        this.lockEscrita = new ReentrantLock();
        this.contadorPedidos = new AtomicLong(0);
        this.conectado = false;
    }
    
    public void conectar() throws IOException {
        lockEscrita.lock();
        try {
            if (conectado) return;
            
            socket = new Socket(host, porta);
            DataInputStream entrada = new DataInputStream(socket.getInputStream());
            saida = new DataOutputStream(socket.getOutputStream());
            
            demux = new Demultiplexer(entrada);
            threadDemux = new Thread(demux);
            threadDemux.start();
            
            conectado = true;
            System.out.println("Conectado ao servidor " + host + ":" + porta);
        } finally {
            lockEscrita.unlock();
        }
    }
    
    public void desconectar() {
        lockEscrita.lock();
        try {
            conectado = false;
            
            if (demux != null) {
                demux.parar();
            }
            
            fecharSocket();
            System.out.println("Desconectado do servidor");
        } finally {
            lockEscrita.unlock();
        }
    }
    
    public RespostaDTO invocar(byte serviceId, byte methodId, Object parametros) throws IOException {
        garantirConexao();
        
        try {
            long tag = contadorPedidos.incrementAndGet();
            Message pedido = Message.request(tag, serviceId, methodId, parametros);
            
            enviarPedido(pedido);
            
            Object resposta = demux.aguardar(tag);
            return (RespostaDTO) resposta;
            
        } catch (Exception e) {
            throw new IOException("Erro ao invocar: " + e.getMessage(), e);
        }
    }
    
    public boolean isConectado() {
        return conectado && socket != null && !socket.isClosed();
    }
    
    private void garantirConexao() throws IOException {
        if (!isConectado()) {
            lockEscrita.lock();
            try {
                if (!isConectado()) {
                    desconectar();
                    conectar();
                }
            } finally {
                lockEscrita.unlock();
            }
        }
    }
    
    private void enviarPedido(Message pedido) throws IOException {
        lockEscrita.lock();
        try {
            protocolo.enviar(pedido, saida);
        } finally {
            lockEscrita.unlock();
        }
    }
    
    private void fecharSocket() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignora
            }
            socket = null;
        }
        saida = null;
        demux = null;
        threadDemux = null;
    }
}