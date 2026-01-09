package server.presentation.handlers;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import common.ErrorLogger;
import common.concurrency.ThreadPool;
import common.dto.RespostaDTO;
import middleware.Message;
import middleware.Protocolo;
import server.presentation.skeleton.RequestDispatcher;

/**
 * Handler por cliente.
 * Lê requests sequencialmente do socket e processa em paralelo via requestExecutor.
 * Escrita no socket é serializada via writeLock para não intercalar respostas.
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

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {

            while (true) {
                Message msg = proto.receber(in);

                if (!msg.isRequest()) {
                    System.err.println("Servidor recebeu response (inesperado): tag=" + msg.getTag());
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
            ErrorLogger.getInstance().logError(
                    "ClientHandler[cliente=" + socket.getInetAddress() + "]", e);
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

            // 10/10: response payload são bytes do RespostaDTO
            Message response = Message.response(msg.getTag(), resp.serialize());
            enviarResposta(response, out);

        } catch (Exception e) {
            ErrorLogger.getInstance().logError(
                    "ClientHandler.processarPedido[tag=" + msg.getTag() +
                            ", service=" + safeService(msg) +
                            ", method=" + safeMethod(msg) + "]", e);

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
        try {
            RespostaDTO erro = RespostaDTO.erro("Erro: " + (mensagem != null ? mensagem : "desconhecido"));
            Message response = Message.response(tag, erro.serialize());
            enviarResposta(response, out);
        } catch (IOException e) {
            // Se nem conseguimos serializar/enviar, a conexão provavelmente vai morrer
            System.err.println("Erro fatal ao enviar erro: " + e.getMessage());
        }
    }

    private void encerrar() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}

        if (onClientDisconnect != null) onClientDisconnect.accept(socket);
    }

    private byte safeService(Message msg) {
        try { return msg.getServiceId(); } catch (Exception e) { return (byte) -1; }
    }

    private byte safeMethod(Message msg) {
        try { return msg.getMethodId(); } catch (Exception e) { return (byte) -1; }
    }
}