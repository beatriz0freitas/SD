package src.servidor;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servidor principal que aceita conexões de clientes
 */
//todo: acho que prefiro deixar apenas a main neste ficheiro e meter o restante raciocínio numa classe separada ServidorApp ou algo do género
public class Servidor {
    
    private int porta;
    private ServerSocket serverSocket;
    private GestorUtilizadores gestorUtilizadores;
    private ExecutorService threadPool;                  // Pool de threads dinâmico
    private boolean ativo;                               // Flag de estado do servidor
    
    public Servidor(int porta) {
        this.porta = porta; 
        this.gestorUtilizadores = new GestorUtilizadores(); 
        this.threadPool = Executors.newCachedThreadPool();
        this.ativo = false;
    }
    
    // Inicia o servidor
    public void iniciar() {
        try {
            serverSocket = new ServerSocket(porta); 
            ativo = true;
            
            System.out.println("========================================");
            System.out.println(" SERVIÇO DE GESTÃO DE VENDAS - servidor ");
            System.out.println("========================================");
            System.out.println("Servidor iniciado na porta: " + porta);
            System.out.println("Utilizadores registados: " + gestorUtilizadores.getNumUtilizadores());
            System.out.println("Aguardando conexões...\n");
            
            // Loop principal 
            while (ativo) {
                try {
                    Socket clienteSocket = serverSocket.accept();                                        // Aceitar nova conexão
                    WorkerCliente worker = new WorkerCliente(clienteSocket, gestorUtilizadores);         // Criar e submeter worker ao thread pool (cria uma nova thread se necessário e reutiliza threads)
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
    
    //Desliga o servidor
    public void shutdown() {
        System.out.println("\nA encerrar servidor...");
        ativo = false;

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();        // Fechar ServerSocket
            }
        } catch (IOException e) {
            System.err.println("Erro ao fechar ServerSocket: " + e.getMessage());
        }
        
        threadPool.shutdown();               // Encerrar thread pool
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
        if (args.length > 0) {
            try {
                porta = Integer.parseInt(args[0]);          // Permitir especificar porta por argumento
            } catch (NumberFormatException e) {
                System.err.println("Porta inválida, usando padrão: " + porta);
            }
        }
        
        Servidor servidor = new Servidor(porta);
        // Adiciona um shutdown hook (quando o programa fechar corre automaticamente servidor.shutdown())
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            servidor.shutdown();
        }));
        servidor.iniciar();
    }
}