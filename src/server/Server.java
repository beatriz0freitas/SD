package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import server.presentation.handlers.ClientHandler;
import server.presentation.skeleton.RequestDispatcher;

public class Server {
    private final int porta;
    private final int D;
    private final int S;
    private ServerSocket serverSocket;
    private final ExecutorService threadPool;
    private volatile boolean ativo;
    
    private RequestDispatcher dispatcher;
    
    public Server(int porta, int D, int S) {
        this.porta = porta;
        this.D = D;
        this.S = S;
        this.threadPool = Executors.newFixedThreadPool(100);
        this.ativo = false;
        
        inicializarServicos();
    }
    
    private void inicializarServicos() {
        // RequestDispatcher encapsula toda a criação de serviços e skeletons
        dispatcher = RequestDispatcher.criar(D);
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
            System.out.println("Séries (S): " + S);
            System.out.println("Aguardando conexões...\n");
            
            while (ativo) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(clientSocket, dispatcher);
                    threadPool.execute(handler);
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
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Erro ao fechar socket: " + e.getMessage());
        }
        
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
        }
        
        System.out.println("Servidor encerrado.");
    }
    
    public static void main(String[] args) {
        int porta = 5001;
        int D = 30;
        int S = 5;
        
        if (args.length > 0) {
            try {
                porta = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Porta inválida, usando padrão: " + porta);
            }
        }
        
        if (args.length > 1) {
            try {
                D = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("D inválido, usando padrão: " + D);
            }
        }
        
        if (args.length > 2) {
            try {
                S = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                System.err.println("S inválido, usando padrão: " + S);
            }
        }
        
        if (S >= D) {
            System.err.println("ERRO: S deve ser menor que D!");
            S = Math.max(1, D / 2);
            System.err.println("Ajustando S para: " + S);
        }
        
        Server servidor = new Server(porta, D, S);
        
        Runtime.getRuntime().addShutdownHook(new Thread(servidor::parar));
        
        servidor.iniciar();
    }
}