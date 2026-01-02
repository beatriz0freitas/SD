package server.presentation.handlers;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import common.concurrency.ThreadPool;
import common.dto.RespostaDTO;
import middleware.Message;
import middleware.Protocolo;
import server.presentation.skeleton.RequestDispatcher;

/**
 * Handler para cada cliente conectado
 * Suporta processamento concorrente de múltiplos pedidos do mesmo cliente
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final RequestDispatcher dispatcher;
    private final Protocolo proto;
    private final ThreadPool requestExecutor;
    private final Lock writeLock;
    private final Consumer<Socket> onClientDisconnect;
    
    public ClientHandler(Socket socket, RequestDispatcher dispatcher, 
                        ThreadPool requestExecutor, Consumer<Socket> onClientDisconnect) {
        this.socket = socket;
        this.dispatcher = dispatcher;
        this.proto = new Protocolo();
        this.requestExecutor = requestExecutor;
        this.writeLock = new ReentrantLock();
        this.onClientDisconnect = onClientDisconnect;
    }
    
    @Override
    public void run() {
        System.out.println("Cliente conectado: " + socket.getInetAddress());
        
        try (DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            
            while (true) {
                Message msg = (Message) proto.receber(in);
                
                if (!msg.isRequest()) {
                    System.err.println("Servidor recebeu response (inesperado): " + msg);
                    continue;
                }
                
                if (!requestExecutor.submit(new RequestProcessor(msg, out))) {
                    enviarErro(msg.getTag(), 
                             "Fila de pedidos cheia (ou shutdown). Tente novamente mais tarde.", 
                             out);
                }
            }
            
        } catch (EOFException e) {
            System.out.println("Cliente desconectado: " + socket.getInetAddress());
        } catch (Exception e) {
            System.err.println("Erro ao processar cliente: " + e.getMessage());
        } finally {
            encerrar();
        }
    }
    
    private class RequestProcessor implements Runnable {
        private final Message msg;
        private final DataOutputStream out;
        
        public RequestProcessor(Message msg, DataOutputStream out) {
            this.msg = msg;
            this.out = out;
        }
        
        @Override
        public void run() {
            processarPedido(msg, out);
        }
    }
    
    private void processarPedido(Message msg, DataOutputStream out) {
        try {
            RespostaDTO resp = dispatcher.despachar(msg);
            enviarResposta(Message.response(msg.getTag(), resp), out);
        } catch (Exception e) {
            System.err.println("Erro ao processar pedido: " + e.getMessage());
            enviarErro(msg.getTag(), e.getMessage(), out);
        }
    }
    
    private void enviarResposta(Message response, DataOutputStream out) {
        writeLock.lock();
        try {
            proto.enviar(response, out);
        } catch (IOException e) {
            System.err.println("Erro ao enviar resposta: " + e.getMessage());
        } finally {
            writeLock.unlock();
        }
    }
    
    private void enviarErro(long tag, String mensagem, DataOutputStream out) {
        RespostaDTO erro = new RespostaDTO(false, "Erro: " + mensagem);
        enviarResposta(Message.response(tag, erro), out);
    }
    
    private void encerrar() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignora
        }
        
        // Notificar servidor sobre desconexão
        if (onClientDisconnect != null) {
            onClientDisconnect.accept(socket);
        }
    }
}