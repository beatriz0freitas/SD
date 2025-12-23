package server.presentation.handlers;

import common.dto.RespostaDTO;
import java.io.*;
import java.net.Socket;
import middleware.proto.*;
import server.presentation.skeleton.RequestDispatcher;

/**
 * Handler para cada cliente conectado
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final RequestDispatcher dispatcher;
    private final ProtocoloHandler proto;

    public ClientHandler(Socket socket, RequestDispatcher dispatcher) {
        this.socket = socket;
        this.dispatcher = dispatcher;
        this.proto = new ProtocoloHandler();
    }

    @Override
    public void run() {
        System.out.println("Cliente conectado: " + socket.getInetAddress());
        
        try (DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            
            while (true) {
                Requisicao req = (Requisicao) proto.receber(in);
                RespostaDTO resp = dispatcher.despachar(req);
                TaggedResponse tagged = new TaggedResponse(req.getTag(), resp);
                proto.enviar(tagged, out);
            }
            
        } catch (EOFException e) {
            System.out.println("Cliente desconectado: " + socket.getInetAddress());
        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
        } finally {
            fecharSocket();
        }
    }

    private void fecharSocket() {
        try {
            socket.close();
        } catch (IOException e) {
            
        }
    }
}
