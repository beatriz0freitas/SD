package server.presentation.handlers;

import common.dto.RespostaDTO;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import middleware.proto.*;
import server.presentation.skeleton.RequestDispatcher;

/**
 * Handler para cada cliente conectado
 * Suporta processamento concorrente de múltiplos pedidos do mesmo cliente
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final RequestDispatcher dispatcher;
    private final ProtocoloHandler proto;
    private final ExecutorService requestExecutor;
    private final Lock writeLock;
    
    public ClientHandler(Socket socket, RequestDispatcher dispatcher) {
        this.socket = socket;
        this.dispatcher = dispatcher;
        this.proto = new ProtocoloHandler();
        this.requestExecutor = Executors.newCachedThreadPool();
        this.writeLock = new ReentrantLock();
    }
    
    @Override
    public void run() {
        System.out.println("Cliente conectado: " + socket.getInetAddress());
        
        try (DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            
            while (true) {
                Requisicao req = (Requisicao) proto.receber(in);
                requestExecutor.execute(new RequestProcessor(req, out));
            }
            
        } catch (EOFException e) {
            System.out.println("Cliente desconectado: " + socket.getInetAddress());
        } catch (Exception e) {
            System.err.println("Erro ao receber pedido: " + e.getMessage());
        } finally {
            encerrar();
        }
    }
    
    private class RequestProcessor implements Runnable {
        private final Requisicao req;
        private final DataOutputStream out;
        
        public RequestProcessor(Requisicao req, DataOutputStream out) {
            this.req = req;
            this.out = out;
        }
        
        @Override
        public void run() {
            processarPedido(req, out);
        }
    }
    
    private void processarPedido(Requisicao req, DataOutputStream out) {
        try {
            RespostaDTO resp = dispatcher.despachar(req);
            enviarResposta(new TaggedResponse(req.getTag(), resp), out);
            
        } catch (Exception e) {
            System.err.println("Erro ao processar pedido: " + e.getMessage());
            enviarErro(req.getTag(), e.getMessage(), out);
        }
    }
    
    private void enviarResposta(TaggedResponse response, DataOutputStream out) {
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
        enviarResposta(new TaggedResponse(tag, erro), out);
    }
    
    private void encerrar() {
        requestExecutor.shutdown();
        try {
            socket.close();
        } catch (IOException e) {
            // Ignora
        }
    }
}