package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import common.concurrency.*;
import server.config.ServerConfig;
import server.presentation.handlers.ClientHandler;
import server.presentation.skeleton.RequestDispatcher;

public class Server {
    private final int porta;
    private final int D;
    private final int S;
    private ServerSocket serverSocket;
        
    // Lock para proteger a coleção de sockets ativos
    private final Lock l = new ReentrantLock();
    private final Set<Socket> clientesAtivos = new HashSet<>();
    
    // Pool para threads de I/O dos clientes (1 thread por cliente)
    private final ThreadPool clientHandlerPool;
    
    // Pool para processar requests (partilhado por todos os clientes)
    private final ThreadPool requestPool;
    
    private volatile boolean ativo;
    private final RequestDispatcher dispatcher;
    
    public Server(int porta, int D, int S) {
        this.porta = porta;
        this.D = D;
        this.S = S;
        
        // Pool para threads de I/O dos clientes
        // Limita número de clientes simultâneos
        this.clientHandlerPool = new ThreadPoolImpl(
            ServerConfig.N_CLIENT_HANDLERS,
            ServerConfig.N_CLIENT_HANDLERS  // fila = maxThreads (rejeita se cheio)
        );
        
        // Pool para processar requests (CPU/I/O bound)
        this.requestPool = new ThreadPoolImpl(
            ServerConfig.N_WORKERS_SERVER,
            ServerConfig.REQUEST_QUEUE_SIZE
        );
        
        this.ativo = false;
        this.dispatcher = RequestDispatcher.criar(D, S);
    }
    
    public void iniciar() {
        try {
            serverSocket = new ServerSocket(porta);
            ativo = true;
            
            imprimirBanner();
            
            while (ativo) {
                try {
                    // Bloqueia até aceitar uma conexão
                    Socket clientSocket = serverSocket.accept();
                    
                    // Adiciona à lista de ativos
                    adicionarCliente(clientSocket);
                    
                    // Cria handler com callback de remoção
                    ClientHandler handler = new ClientHandler(
                        clientSocket,
                        dispatcher,
                        requestPool
                    );
                    
                    
                    // Submete à pool (em vez de criar thread manualmente)
                    if (!clientHandlerPool.submit(handler)) {
                        // Pool recusou: está cheia ou em shutdown
                        System.err.println("Pool de handlers cheia! Rejeitando cliente " + 
                                         clientSocket.getInetAddress());
                        
                        // Fecha socket e remove da lista
                        try {
                            clientSocket.close();
                        } catch (IOException e) {
                            // Ignora
                        }
                        removerCliente(clientSocket);
                    }
                    
                } catch (IOException e) {
                    if (ativo) {
                        System.err.println("Erro ao aceitar conexão: " + e.getMessage());
                    }
                    // Se ativo=false, é shutdown normal
                }
            }
            
        } catch (IOException e) {
            System.err.println("Erro ao iniciar servidor: " + e.getMessage());
        }
    }
    
    public void parar() {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("  ENCERRANDO SERVIDOR");
        System.out.println("=".repeat(50));
    
        // Passo 1: Para de aceitar novas conexões
        ativo = false;
        fecharServerSocket();
    
        // Passo 2: Fecha sockets de clientes
        // (acorda threads bloqueadas em I/O)
        System.out.println("\n[1/4] Fechando sockets de clientes...");
        fecharSocketsClientes();
    
        // Passo 3: Encerra pool de client handlers
        System.out.println("\n[2/4] Encerrando client handlers...");
        encerrarPoolComTimeout(
            clientHandlerPool,
            "Client Handler Pool",
            5
        );
    
        // Passo 4: Encerra pool de requests
        System.out.println("\n[3/4] Encerrando request pool...");
        encerrarPoolComTimeout(
            requestPool,
            "Request Pool",
            10
        );
    
        System.out.println("\n[4/4] Cleanup concluído.");
        System.out.println("=".repeat(50));
        System.out.println("  SERVIDOR ENCERRADO COM SUCESSO");
        System.out.println("=".repeat(50) + "\n");
    }
    
    
    private void imprimirBanner() {
        System.out.println("=".repeat(50));
        System.out.println("  SERVIÇO DE GESTÃO DE VENDAS");
        System.out.println("=".repeat(50));
        System.out.println("Porta: " + porta);
        System.out.println("Dias (D): " + D);
        System.out.println("Séries (S): " + S);
        System.out.println();
        System.out.println("Thread Pools:");
        System.out.println("  - Client Handlers: " + clientHandlerPool.getMaxThreads() + " threads");
        System.out.println("  - Request Workers: " + requestPool.getMaxThreads() + " threads");
        System.out.println("=".repeat(50));
        System.out.println("Aguardando conexões...\n");
    }
    
    private void adicionarCliente(Socket socket) {
        l.lock();
        try {
            clientesAtivos.add(socket);
            System.out.println("[Server] Cliente conectado: " + socket.getInetAddress() + 
                             " (Total: " + clientesAtivos.size() + ")");
        } finally {
            l.unlock();
        }
    }
    
    private void removerCliente(Socket socket) {
        l.lock();
        try {
            if (clientesAtivos.remove(socket)) {
                System.out.println("[Server] Cliente removido: " + socket.getInetAddress() + 
                                 " (Restantes: " + clientesAtivos.size() + ")");
            }
        } finally {
            l.unlock();
        }
    }

    private void fecharServerSocket() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                System.out.println("ServerSocket fechado.");
            }
        } catch (IOException e) {
            System.err.println("Erro ao fechar ServerSocket: " + e.getMessage());
        }
    }

    private void fecharSocketsClientes() {
        l.lock();
        try {
            int count = 0;
            for (Socket s : clientesAtivos) {
                try {
                    if (!s.isClosed()) {
                        s.close();
                        count++;
                    }
                } catch (IOException e) {
                    System.err.println("Erro ao fechar socket: " + e.getMessage());
                }
            }
            
            if (count > 0) {
                System.out.println("Fechados " + count + " socket(s) de cliente(s).");
            } else {
                System.out.println("Nenhum socket de cliente para fechar.");
            }
            
            clientesAtivos.clear();
        } finally {
            l.unlock();
        }
    }
    
    private void encerrarPoolComTimeout(ThreadPool pool, String nome, int timeoutSegundos) {
        System.out.println("  Chamando shutdown em " + nome + "...");
        pool.shutdown();
        
        try {
            System.out.println("  Aguardando término de " + nome + 
                             " (timeout: " + timeoutSegundos + "s)...");
            
            if (pool.awaitTermination(timeoutSegundos, TimeUnit.SECONDS)) {
                System.out.println("  ✓ " + nome + " encerrada com sucesso.");
            } else {
                System.err.println("  ✗ " + nome + " não terminou a tempo. Forçando...");
                pool.shutdownNow();
                
                // Dá mais um tempo após shutdownNow
                if (pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.out.println("  ✓ " + nome + " forçadamente encerrada.");
                } else {
                    System.err.println("  ✗ " + nome + " ainda tem threads ativas!");
                }
            }
        } catch (InterruptedException e) {
            System.err.println("  ✗ Interrompido ao esperar " + nome);
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    public static void main(String[] args) {
        int porta = ServerConfig.DEFAULT_PORT;
        int D = ServerConfig.DEFAULT_D;
        int S = ServerConfig.DEFAULT_S;
        
        // Parse argumentos
        if (args.length > 0) {
            porta = parseIntOuPadrao(args[0], porta, "Porta");
        }
        if (args.length > 1) {
            D = parseIntOuPadrao(args[1], D, "D");
        }
        if (args.length > 2) {
            S = parseIntOuPadrao(args[2], S, "S");
        }
        
        // Validação
        if (S > D) {
            System.err.println("AVISO: S (" + S + ") > D (" + D + "), ajustando S = D");
            S = D;
        }
        
        // Cria e inicia servidor
        Server servidor = new Server(porta, D, S);
        
        // Registra shutdown hook para Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            servidor.parar();
        }, "Shutdown-Hook"));
        
        servidor.iniciar();
    }
    
    private static int parseIntOuPadrao(String valor, int padrao, String nome) {
        try {
            int parsed = Integer.parseInt(valor);
            if (parsed <= 0) {
                System.err.println(nome + " deve ser positivo, usando padrão: " + padrao);
                return padrao;
            }
            return parsed;
        } catch (NumberFormatException e) {
            System.err.println(nome + " inválido, usando padrão: " + padrao);
            return padrao;
        }
    }
}