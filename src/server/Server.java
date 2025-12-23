package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import server.config.ServerConfig;
import server.presentation.handlers.ClientHandler;
import server.presentation.skeleton.RequestDispatcher;

public class Server {
    private final int porta;
    private final int D;
    private ServerSocket serverSocket;
    private final ExecutorService threadPool;
    private volatile boolean ativo;
    private final RequestDispatcher dispatcher;
    
    public Server(int porta, int D) {
        this.porta = porta;
        this.D = D;
        this.threadPool = Executors.newCachedThreadPool();
        this.ativo = false;
        this.dispatcher = RequestDispatcher.criar(D);
    }
    
    public void iniciar() {
        try {
            serverSocket = new ServerSocket(porta);
            ativo = true;
            
            System.out.println("========================================");
            System.out.println("  SERVIÇO DE GESTÃO DE VENDAS");
            System.out.println("========================================");
            System.out.println("Porta: " + porta);
            System.out.println("Dias (D): " + D);
            System.out.println("Aguardando conexões...\n");
            
            while (ativo) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.execute(new ClientHandler(clientSocket, dispatcher));
                } catch (IOException e) {
                    if (ativo) {
                        System.err.println("Erro ao aceitar conexão: " + e.getMessage());
                    }
                }
            }
            
        } catch (IOException e) {
            System.err.println("Erro ao iniciar servidor: " + e.getMessage());
        }
    }
    
    public void parar() {
        System.out.println("\nEncerrando servidor...");
        ativo = false;
        
        fecharSocket();
        encerrarThreadPool();
        
        System.out.println("Servidor encerrado.");
    }
    
    private void fecharSocket() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Erro ao fechar socket: " + e.getMessage());
        }
    }
    
    private void encerrarThreadPool() {
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    public static void main(String[] args) {
        int porta = ServerConfig.DEFAULT_PORT;
        int D = ServerConfig.DEFAULT_D;

        
        if (args.length > 0) {
            porta = parseIntOuPadrao(args[0], porta, "Porta");
        }
        
        if (args.length > 1) {
            D = parseIntOuPadrao(args[1], D, "D");
        }
        
        Server servidor = new Server(porta, D);
        Runtime.getRuntime().addShutdownHook(new Thread(servidor::parar));
        servidor.iniciar();
    }
    
    private static int parseIntOuPadrao(String valor, int padrao, String nome) {
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            System.err.println(nome + " inválido, usando padrão: " + padrao);
            return padrao;
        }
    }
}