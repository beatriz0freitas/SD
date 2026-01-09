package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import common.concurrency.ThreadPool;
import common.concurrency.ThreadPoolImpl;
import server.config.ServerConfig;
import server.presentation.handlers.ClientHandler;
import server.presentation.skeleton.RequestDispatcher;

public class Server {
    private final int porta;
    private final int D;
    private final int S;
    private ServerSocket serverSocket;

    private final ReentrantLock clientesLock = new ReentrantLock();
    private final Set<Socket> clientesAtivos = new HashSet<>();

    private final ThreadPool clientHandlerPool;
    private final ThreadPool requestPool;

    private final ReentrantLock stateLock = new ReentrantLock();
    private boolean ativo;
    private final RequestDispatcher dispatcher;

    private final DeadlockMonitor deadlockMonitor;

    public Server(int porta, int D, int S) {
        this.porta = porta;
        this.D = D;
        this.S = S;

        this.clientHandlerPool = new ThreadPoolImpl(
                ServerConfig.N_CLIENT_HANDLERS,
                ServerConfig.N_CLIENT_HANDLERS
        );

        this.requestPool = new ThreadPoolImpl(
                ServerConfig.N_WORKERS_SERVER,
                ServerConfig.REQUEST_QUEUE_SIZE
        );

        this.ativo = false;
        this.dispatcher = RequestDispatcher.criar(D, S);
        this.deadlockMonitor = new DeadlockMonitor();
    }

    public void iniciar() {
        try {
            serverSocket = new ServerSocket(porta);
            setAtivo(true);

            imprimirBanner();
            deadlockMonitor.start(30);

            while (isAtivo()) {
                try {
                    Socket clientSocket = serverSocket.accept();

                    adicionarCliente(clientSocket);

                    ClientHandler handler = new ClientHandler(
                            clientSocket,
                            dispatcher,
                            requestPool,
                            this::removerCliente
                    );

                    if (!clientHandlerPool.submit(handler)) {
                        System.err.println("Pool de handlers cheia! Rejeitando cliente " +
                                clientSocket.getInetAddress());

                        try { clientSocket.close(); } catch (IOException ignored) {}
                        removerCliente(clientSocket);
                    }

                } catch (IOException e) {
                    if (isAtivo()) {
                        System.err.println("Erro ao aceitar conexão: " + e.getMessage());
                    }
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

    
        setAtivo(false);
        fecharServerSocket();

    
        System.out.println("\n[1/4] Fechando sockets de clientes...");
        fecharSocketsClientes();

    
        System.out.println("\n[2/4] Encerrando pools...");
        clientHandlerPool.shutdownNow();   
        requestPool.shutdown();            

    
        System.out.println("\n[3/4] Aguardando término das threads...");
        try {
            clientHandlerPool.awaitTermination();
            requestPool.awaitTermination();
            System.out.println("✓ Pools encerradas.");
        } catch (InterruptedException e) {
            System.err.println("Interrompido durante shutdown. Forçando encerramento...");
            Thread.currentThread().interrupt();
            clientHandlerPool.shutdownNow();
            requestPool.shutdownNow();
        }

        System.out.println("\n[4/4] Cleanup concluído.");
        System.out.println("=".repeat(50));
        System.out.println("  SERVIDOR ENCERRADO COM SUCESSO");
        System.out.println("=".repeat(50) + "\n");

        deadlockMonitor.stop();
        dispatcher.shutdown();
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

    private boolean isAtivo() {
        stateLock.lock();
        try {
            return ativo;
        } finally {
            stateLock.unlock();
        }
    }

    private void setAtivo(boolean value) {
        stateLock.lock();
        try {
            ativo = value;
        } finally {
            stateLock.unlock();
        }
    }

    private void adicionarCliente(Socket socket) {
        clientesLock.lock();
        try {
            clientesAtivos.add(socket);
            System.out.println("[Server] Cliente conectado: " + socket.getInetAddress() +
                    " (Total: " + clientesAtivos.size() + ")");
        } finally {
            clientesLock.unlock();
        }
    }

    private void removerCliente(Socket socket) {
        clientesLock.lock();
        try {
            if (clientesAtivos.remove(socket)) {
                System.out.println("[Server] Cliente removido: " + socket.getInetAddress() +
                        " (Restantes: " + clientesAtivos.size() + ")");
            }
        } finally {
            clientesLock.unlock();
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
        clientesLock.lock();
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

            if (count > 0) System.out.println("Fechados " + count + " socket(s) de cliente(s).");
            else System.out.println("Nenhum socket de cliente para fechar.");

            clientesAtivos.clear();
        } finally {
            clientesLock.unlock();
        }
    }

    public static void main(String[] args) {
        int porta = ServerConfig.DEFAULT_PORT;
        int D = ServerConfig.DEFAULT_D;
        int S = ServerConfig.DEFAULT_S;

        if (args.length > 0) porta = parseIntOuPadrao(args[0], porta, "Porta");
        if (args.length > 1) D = parseIntOuPadrao(args[1], D, "D");
        if (args.length > 2) S = parseIntOuPadrao(args[2], S, "S");

        if (D <= 0) {
            System.err.println("ERRO: D deve ser positivo. Valor: " + D);
            System.exit(1);
        }

        if (S <= 0) {
            System.err.println("ERRO: S deve ser positivo. Valor: " + S);
            System.exit(1);
        }

        if (S > D) {
            System.out.println("AVISO: S (" + S + ") > D (" + D + "), ajustando S = D");
            S = D;
        }

        Server servidor = new Server(porta, D, S);

        Runtime.getRuntime().addShutdownHook(new Thread(servidor::parar, "Shutdown-Hook"));

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
