package src.servidor;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import src.uteis.ThreadPool;

/**
 * Lógica principal do servidor de gestão de vendas
 * 
 * - ThreadPool otimizada (com Condition, sem espera ativa)
 * - Shutdown gracioso (aguarda tarefas pendentes)
 * - Validação de parâmetros
 * - Logs estruturados
 */
public class ServidorLogica {

    private int porta;
    private int D; 
    private int S;  

    private ServerSocket serverSocket;
    private GestorUtilizadores gestorUtilizadores;
    private GestorEventos gestorEventos;

    private static final int N_THREADS = 20;  // Threads para aceitar conexões
    private static final int MAX_QUEUE = 100; // Fila de conexões pendentes
    private final ThreadPool workers;

    private volatile boolean ativo;                               // Flag de estado do servidor
    
    public ServidorLogica(int porta, int D, int S) {  
        if (porta < 1024 || porta > 65535) {
            throw new IllegalArgumentException("Porta deve estar entre 1024-65535");
        }
        if (D <= 0) {
            throw new IllegalArgumentException("D (dias histórico) deve ser positivo");
        }
        if (S <= 0) {
            throw new IllegalArgumentException("S (séries em memória) deve ser positivo");
        }
        if (S >= D) {
            throw new IllegalArgumentException("S deve ser menor que D");
        }
        
        this.porta = porta;
        this.D = D;
        this.S = S;
        
        // Inicializa gestores (com ReadWriteLock otimizado)
        this.gestorUtilizadores = new GestorUtilizadores();
        this.gestorEventos = new GestorEventos(D, S);
        
        // Inicializa ThreadPool otimizada
        this.workers = new ThreadPool(N_THREADS, MAX_QUEUE);
        
        this.ativo = false;
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
            serverSocket.setReuseAddress(true);  // Permite restart rápido
            ativo = true;
            
            imprimirBanner();

            // Loop principal 
            while (ativo) {
                try {
                    Socket clienteSocket = serverSocket.accept();                                        // Aceitar nova conexão
                    // Cria handler e submete ao pool
                    SessaoCliente handler = new SessaoCliente(clienteSocket, gestorUtilizadores, gestorEventos);
                    
                    try {
                        workers.submit(handler);                                                          // Submete ao pool de threads
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.err.println("Servidor interrompido durante submit");
                        break;
                    }
                    
                } catch (IOException e) {
                    if (ativo) {
                        System.err.println("Erro ao aceitar conexão: " + e.getMessage());
                    }
                    // Não quebra o loop - continua a aceitar outras conexões
                }
            }
            
        } catch (IOException e) {
            System.err.println("Erro ao iniciar servidor: " + e.getMessage());
        } finally {
        // o shutdown será tratado pelo shutdown hook
        }
    }
    
    /**
     * Desliga o servidor de forma ordenada.
     * Aguarda conclusão de tarefas pendentes.
     */
    public void shutdown() {
        if (!ativo) {
            System.out.println("Servidor já estava desligado");
            return;
        }
        System.out.println("\nA encerrar servidor...");
        System.out.println("Thread: " + Thread.currentThread().getName());

        ativo = false;

        // 1. Fecha ServerSocket (para de aceitar novas conexões)
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();       
            }
        } catch (IOException e) {
            System.err.println("Erro ao fechar ServerSocket: " + e.getMessage());
        }
        
        // 2. Shutdown do ThreadPool (aguarda tarefas pendentes)
        System.out.println("Aguardando conclusão de tarefas pendentes...");
        workers.shutdown();
        System.out.println("✓ ThreadPool encerrada");

        // 3. Estatísticas finais
        imprimirEstatisticas();
        
        System.out.println("\n✓ Servidor encerrado com sucesso");
    }

    /**
     * Imprime banner de inicialização
     */
    private void imprimirBanner() {
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════╗");
        System.out.println("║                                                ║");
        System.out.println("║   SERVIÇO DE GESTÃO DE VENDAS - SERVIDOR       ║");
        System.out.println("║                                                ║");
        System.out.println("╚════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("┌─ CONFIGURAÇÃO ─────────────────────────────────┐");
        System.out.println("│ Porta:                " + porta);
        System.out.println("│ Dias anteriores (D):  " + D);
        System.out.println("│ Séries memória (S):   " + S);
        System.out.println("│ Threads (pool):       " + N_THREADS);
        System.out.println("│ Fila máxima:          " + MAX_QUEUE);
        System.out.println("└────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("┌─ ESTADO INICIAL ───────────────────────────────┐");
        System.out.println("│ " + gestorUtilizadores.getStats());
        System.out.println("│ " + gestorEventos.getStats());
        System.out.println("└────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("✓ Servidor iniciado com sucesso!");
    }

    /**
     * Imprime estatísticas finais
     */
    private void imprimirEstatisticas() {
        System.out.println("\n┌─ ESTATÍSTICAS FINAIS ──────────────────────────┐");
        System.out.println("│ " + gestorUtilizadores.getStats());
        System.out.println("│ " + gestorEventos.getStats());
        System.out.println("│ " + workers.getStats());
        System.out.println("└────────────────────────────────────────────────┘");
    }   
}