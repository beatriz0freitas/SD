package src.servidor;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lógica principal do servidor de gestão de vendas
 */
public class ServidorLogica {

    private int porta;
    private ServerSocket serverSocket;
    private GestorUtilizadores gestorUtilizadores;
    private GestorEventos gestorEventos;
    private ExecutorService threadPool;                  // Pool de threads dinâmico
    private volatile boolean ativo;                               // Flag de estado do servidor
    
    public ServidorLogica(int porta) {
        this.porta = porta; 
        this.gestorUtilizadores = new GestorUtilizadores(); 
        this.gestorEventos = new GestorEventos();
        this.threadPool = Executors.newCachedThreadPool();
        this.ativo = false;                             // Servidor inicialmente inativo
    }
    
    /**
     * Verifica se o servidor está ativo
     */
    public boolean isAtivo() {
        return ativo;
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
                    ClienteHandler worker = new ClienteHandler(clienteSocket, gestorUtilizadores, gestorEventos);         // Criar e submeter worker ao thread pool (cria uma nova thread se necessário e reutiliza threads)
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
        // o shutdown será tratado pelo shutdown hook
        }
    }
    
    //Desliga o servidor
    public void shutdown() {
        System.out.println("\nA encerrar servidor...");
        System.out.println("Shutdown invoked by thread: " + Thread.currentThread().getName());
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
}