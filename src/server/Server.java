    package server;

    import java.io.IOException;
    import java.net.ServerSocket;
    import java.net.Socket;
    import java.util.HashSet;
    import java.util.Set;
    import java.util.concurrent.TimeUnit;
    import java.util.concurrent.locks.Lock;
    import java.util.concurrent.locks.ReentrantLock;

    import server.config.ServerConfig;
    import server.presentation.handlers.ClientHandler;
    import server.presentation.skeleton.RequestDispatcher;
    import common.concurrency.*;

    public class Server {
        private final int porta;
        private final int D;
        private final int S;
        private ServerSocket serverSocket;
        
        // colocamos uma thread handler por cliente (I/O) que usa um pool partilhado
        // guardam-se os sockets para fechar no shutdown
        private Lock l = new ReentrantLock();
        private Set<Socket> clientesAtivos = new HashSet<>();
        
        // uma pool para todos os clientes
        private final ThreadPool requestPool;
        
        private volatile boolean ativo;
        private final RequestDispatcher dispatcher;
        
        public Server(int porta, int D, int S) {
            this.porta = porta;
            this.D = D;
            this.S = S;
            
            // Pool maior para processar requests (pode ser CPU-bound ou I/O-bound)
            this.requestPool = new ThreadPoolImpl(ServerConfig.N_WORKERS_SERVER);
            
            this.ativo = false;
            this.dispatcher = RequestDispatcher.criar(D, S);
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
                System.out.println("Request Pool: " + requestPool.getMaxThreads() + " threads");
                System.out.println("Aguardando conexões...\n");
                
                while (ativo) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        
                        // adiciona socket de cliente para no shutdown o server poder fechar a conexao
                        // e terminar as threads de I/O
                        l.lock();
                        try {
                            clientesAtivos.add(clientSocket);
                        } finally {
                            l.unlock();
                        }
                        
                        // Passa o pool partilhado para o handler
                        ClientHandler handler = new ClientHandler(
                            clientSocket, 
                            dispatcher, 
                            requestPool
                        );
                        
                        Thread t = new Thread(handler);
                        t.start();
                        
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
            
            fecharSockets();
            encerrarPool();
            
            System.out.println("Servidor encerrado.");
        }

        private void fecharSockets() {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }

                l.lock();
                try {
                    for(Socket s : clientesAtivos) {
                        if (!s.isClosed()) {
                            s.close();
                        
                        }
                    }
                    clientesAtivos.clear();
                } finally {
                    l.unlock();
                }
            } catch (IOException e) {
                System.err.println("Erro ao fechar socket: " + e.getMessage());
            }
        }
        
        private void encerrarPool() {
            // Encerra pool de requests primeiro (processa pendentes)
            requestPool.shutdown();
            try {
                if (!requestPool.awaitTermination(10, TimeUnit.SECONDS)) {
                    requestPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                requestPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        public static void main(String[] args) {
            int porta = ServerConfig.DEFAULT_PORT;
            int D = ServerConfig.DEFAULT_D;
            int S = ServerConfig.DEFAULT_S;
            
            if (args.length > 0) {
                porta = parseIntOuPadrao(args[0], porta, "Porta");
            }
            
            if (args.length > 1) {
                D = parseIntOuPadrao(args[1], D, "D");
            }
            
            if (args.length > 2) {
                S = parseIntOuPadrao(args[2], S, "S");
            }
            
            // Validar S <= D
            if (S > D) {
                System.err.println("AVISO: S (" + S + ") maior que D (" + D + "), ajustando S = D");
                S = D;
            }
            
            Server servidor = new Server(porta, D, S);
            Runtime.getRuntime().addShutdownHook(new Thread(servidor::parar));
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