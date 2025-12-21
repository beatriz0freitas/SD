package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import server.business.services.*;
import server.presentation.handlers.*;
import server.presentation.skeleton.*;

/**
 * Ponto de entrada do servidor
 */
public class Servidor {
    private final int porta;
    private final int D; // Dias a considerar
    private final int S; // Séries em memória
    private ServerSocket serverSocket;
    private final ExecutorService threadPool;
    private volatile boolean ativo;
    
    // Services
    private ServicoAutenticacao servicoAuth;
    private ServicoEventos servicoEventos;
    private ServicoAgregacoes servicoAgregacoes;
    private ServicoAdmin servicoAdmin;
    
    // Dispatcher
    private RequestDispatcher dispatcher;
    
    public Servidor(int porta, int D, int S) {
        this.porta = porta;
        this.D = D;
        this.S = S;
        this.threadPool = Executors.newCachedThreadPool(); // em vez de se criar uma thread por cliente 
        this.ativo = false;
        
        inicializarServicos();
    }
    
    private void inicializarServicos() {
        // 1. Criar serviços de negócio
        servicoAuth = new ServicoAutenticacao();
        servicoEventos = new ServicoEventos(D);
        servicoAgregacoes = new ServicoAgregacoes(servicoEventos, D);
        servicoAdmin = new ServicoAdmin();
        
        // 2. Criar skeletons
        ServicoAutenticacaoSkeleton skeletonAuth = 
            new ServicoAutenticacaoSkeleton(servicoAuth);
        ServicoEventosSkeleton skeletonEventos = 
            new ServicoEventosSkeleton(servicoEventos);
        ServicoAgregacoesSkeleton skeletonAgregacoes = 
            new ServicoAgregacoesSkeleton(servicoAgregacoes);
        ServicoAdminSkeleton skeletonAdmin = 
            new ServicoAdminSkeleton(servicoAdmin);
        
        // 3. Criar dispatcher
        dispatcher = new RequestDispatcher(
            skeletonAuth, skeletonEventos, skeletonAgregacoes, skeletonAdmin);
    }
    
    public void iniciar() {
        try {
            serverSocket = new ServerSocket(porta);
            ativo = true;
            
            System.out.println("========================================");
            System.out.println("  SERVIÇO DE GESTÃO DE VENDAS - servidor");
            System.out.println("========================================");
            System.out.println("Servidor iniciado na porta: " + porta);
            System.out.println("Dias anteriores (D): " + D);
            System.out.println("Séries em memória (S): " + S);
            System.out.println("Aguardando conexões...\n");
            
            // Loop principal - aceitar conexões
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
        System.out.println("\nA encerrar servidor...");
        ativo = false;
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Erro ao fechar servidor: " + e.getMessage());
        }
        
        threadPool.shutdown();
        System.out.println("Servidor encerrado.");
    }
    
    public static void main(String[] args) {
        int porta = 5001;
        int D = 30;
        int S = 5;
        
        // Processar argumentos
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
        
        // Validar S < D
        if (S >= D) {
            System.err.println("ERRO: S deve ser menor que D!");
            S = Math.max(1, D / 2);
            System.err.println("Ajustando S para: " + S);
        }
        
        Servidor servidor = new Servidor(porta, D, S);
        
        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            servidor.parar();
        }));
        
        servidor.iniciar();
    }
}