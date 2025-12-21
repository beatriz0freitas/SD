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
    private final ProtocoloHandler protocoloHandler;
    
    public ClientHandler(Socket socket, RequestDispatcher dispatcher) {
        this.socket = socket;
        this.dispatcher = dispatcher;
        this.protocoloHandler = new ProtocoloHandler();
    }
    
    @Override
    public void run() {
        System.out.println("Nova conexão: " + socket.getInetAddress());
        
        try (DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            
            while (!socket.isClosed()) {
                try {
                    // 1. Receber requisição
                    Requisicao requisicao = (Requisicao) protocoloHandler.receber(in);
                    
                    System.out.println("Processando: " + requisicao.getOperacao());
                    
                    // 2. Processar requisição
                    RespostaDTO resposta = dispatcher.despachar(requisicao);
                    
                    // 3. Enviar resposta
                    protocoloHandler.enviar(resposta, out);
                    
                } catch (EOFException e) {
                    System.out.println("Cliente desconectou: " + socket.getInetAddress());
                    break;
                    
                } catch (Exception e) {
                    System.err.println("Erro ao processar requisição: " + e.getMessage());
                    e.printStackTrace();
                    
                    // Tentar enviar erro ao cliente
                    try {
                        RespostaDTO erro = RespostaDTO.erro("Erro no servidor: " + e.getMessage());
                        protocoloHandler.enviar(erro, out);
                    } catch (IOException ignored) {
                        // Cliente já desconectou
                    }
                }
            }
            
        } catch (IOException e) {
            System.err.println("Erro na conexão: " + e.getMessage());
            
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Erro ao fechar socket: " + e.getMessage());
            }
        }
    }
}
