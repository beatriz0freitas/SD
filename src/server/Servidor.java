package src.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servidor principal que aceita conexões de clientes
 */
public class Servidor {
    
    private int porta;
    private ServerSocket serverSocket;
    private GestorUtilizadores gestorUtilizadores;
    private ExecutorService threadPool;
    private boolean ativo;
    
    public Servidor(int porta) {
        this.porta = porta;
        this.gestorUtilizadores = new GestorUtilizadores();
        this.threadPool = Executors.newCachedThreadPool(); // Pool de threads dinâmico
        this.ativo = false;
    }
    
    /**
     * Inicia o servidor
     */
    public void iniciar() {
        try {
            serverSocket = new ServerSocket(porta);
            ativo = true;
            
            System.out.println("====================================");
            System.out.println("   SERVIDOR DE GESTÃO DE VENDAS    ");
            System.out.println("====================================");
            System.out.println("Servidor iniciado na porta: " + porta);
            System.out.println("Utilizadores registados: " + gestorUtilizadores.getNumUtilizadores());
            System.out.println("Aguardando conexões...\n");
            
            // Loop de aceitação de clientes
            while (ativo) {
                try {
                    // Aceitar nova conexão
                    Socket clienteSocket = serverSocket.accept();
                    
                    // Criar e submeter worker ao thread pool
                    WorkerCliente worker = new WorkerCliente(clienteSocket, gestorUtilizadores);
                    threadPool.execute(worker);
                    
                } catch (IOException e) {
                    if (ativo) {
                        System.err.println("Erro ao aceitar conexão: " + e.getMessage());
                    }
                }
            }
            
        } catch (IOException e) {
            System.err.println("Erro ao iniciar servidor: " + e.getMessage());
        } finally {
            shutdown();
        }
    }
    
    /**
     * Para o servidor de forma controlada
     */
    public void shutdown() {
        System.out.println("\nA encerrar servidor...");
        ativo = false;
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Erro ao fechar ServerSocket: " + e.getMessage());
        }
        
        // Encerrar thread pool
        threadPool.shutdown();
        System.out.println("Servidor encerrado.");
    }
    
    /**
     * Verifica se o servidor está ativo
     */
    public boolean isAtivo() {
        return ativo;
    }
    
    /**
     * Método main para iniciar o servidor
     */
    public static void main(String[] args) {
        int porta = 5000; // Porta padrão
        
        // Permitir especificar porta por argumento
        if (args.length > 0) {
            try {
                porta = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Porta inválida, usando padrão: " + porta);
            }
        }
        
        Servidor servidor = new Servidor(porta);
        
        // Adicionar shutdown hook para encerramento gracioso
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            servidor.shutdown();
        }));
        
        // Iniciar servidor
        servidor.iniciar();
    }
}