package client;

import client.connection.ConnectionPool;
import client.connection.PooledConnection;
import common.dto.RespostaDTO;
import middleware.Message;
import middleware.Protocolo;

import java.io.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Middleware do cliente com suporte a pool de conexões e shutdown gracioso
 */
public class ClienteMiddleware {
    private final String host;
    private final int porta;
    private final Protocolo protocolo;
    private final ReentrantLock lockEscrita;
    private final AtomicLong contadorPedidos;
    
    // Pool de conexões compartilhado
    private ConnectionPool connectionPool;
    private boolean usePool;
    
    // Conexão dedicada (modo antigo, sem pool)
    private PooledConnection dedicatedConnection;
    private Demultiplexer demux;
    private Thread threadDemux;
    private ClientShutdownHandler shutdownHandler;
    
    private volatile boolean conectado;
    private volatile boolean serverShutdown;
    
    /**
     * Construtor padrão (sem pool)
     */
    public ClienteMiddleware(String host, int porta) {
        this(host, porta, false, 1);
    }
    
    /**
     * Construtor com opção de usar pool
     * @param usePool se true, usa ConnectionPool; se false, usa conexão dedicada
     * @param maxConnections número máximo de conexões no pool (ignorado se usePool=false)
     */
    public ClienteMiddleware(String host, int porta, boolean usePool, int maxConnections) {
        this.host = host;
        this.porta = porta;
        this.protocolo = new Protocolo();
        this.lockEscrita = new ReentrantLock();
        this.contadorPedidos = new AtomicLong(0);
        this.conectado = false;
        this.serverShutdown = false;
        this.usePool = usePool;
        
        // Criar shutdown handler
        this.shutdownHandler = new ClientShutdownHandler(() -> {
            serverShutdown = true;
            conectado = false;
        });
        
        if (usePool) {
            this.connectionPool = new ConnectionPool(host, porta, maxConnections);
            System.out.println("ClienteMiddleware configurado com ConnectionPool (max=" + maxConnections + ")");
        } else {
            System.out.println("ClienteMiddleware configurado com conexão dedicada");
        }
    }
    
    public void conectar() throws IOException {
        lockEscrita.lock();
        try {
            if (conectado) return;
            
            if (serverShutdown) {
                throw new IOException("Servidor foi encerrado. Não é possível reconectar.");
            }
            
            if (!usePool) {
                // Modo antigo: conexão dedicada com demultiplexer
                conectarDedicado();
            } else {
                // Modo pool: conexões são obtidas sob demanda
                conectado = true;
                System.out.println("ConnectionPool pronto para uso");
            }
            
        } finally {
            lockEscrita.unlock();
        }
    }
    
    private void conectarDedicado() throws IOException {
        try {
            dedicatedConnection = new PooledConnection(host, porta, null);
            DataInputStream entrada = dedicatedConnection.getInputStream();
            
            demux = new Demultiplexer(entrada, shutdownHandler);
            threadDemux = new Thread(demux, "Demux-" + host + ":" + porta);
            threadDemux.start();
            
            conectado = true;
            System.out.println("Conectado ao servidor " + host + ":" + porta + " (dedicado)");
            
        } catch (IOException e) {
            if (dedicatedConnection != null) {
                dedicatedConnection.closePhysical();
            }
            throw e;
        }
    }
    
    public void desconectar() {
        lockEscrita.lock();
        try {
            conectado = false;
            
            if (usePool) {
                if (connectionPool != null) {
                    connectionPool.close();
                    System.out.println("ConnectionPool fechado");
                }
            } else {
                if (demux != null) {
                    demux.parar();
                }
                if (dedicatedConnection != null) {
                    dedicatedConnection.closePhysical();
                }
            }
            
            System.out.println("Desconectado do servidor");
            
        } finally {
            lockEscrita.unlock();
        }
    }
    
    public RespostaDTO invocar(byte serviceId, byte methodId, Object parametros) throws IOException {
        if (serverShutdown) {
            throw new IOException("Servidor foi encerrado. Operação não disponível.");
        }
        
        garantirConexao();
        
        try {
            long tag = contadorPedidos.incrementAndGet();
            Message pedido = Message.request(tag, serviceId, methodId, parametros);
            
            if (usePool) {
                return invocarComPool(pedido);
            } else {
                return invocarDedicado(pedido);
            }
            
        } catch (Exception e) {
            if (serverShutdown) {
                throw new IOException("Servidor encerrado: " + e.getMessage(), e);
            }
            throw new IOException("Erro ao invocar: " + e.getMessage(), e);
        }
    }
    
    /**
     * Invocação usando pool de conexões
     */
    private RespostaDTO invocarComPool(Message pedido) throws IOException, InterruptedException {
        PooledConnection conn = null;
        try {
            conn = connectionPool.getConnection();
            
            DataOutputStream out = conn.getOutputStream();
            lockEscrita.lock();
            try {
                protocolo.enviar(pedido, out);
                out.flush();
            } finally {
                lockEscrita.unlock();
            }
            
            DataInputStream in = conn.getInputStream();
            Message resposta = (Message) protocolo.receber(in);
            
            if (!resposta.isResponse() || resposta.getTag() != pedido.getTag()) {
                throw new IOException("Resposta inválida recebida");
            }
            
            // Verificar se é mensagem de shutdown
            Object payload = resposta.getPayload();
            if (shutdownHandler.processMessage(payload)) {
                throw new IOException("Servidor encerrado");
            }
            
            return (RespostaDTO) payload;
            
        } catch (ClassNotFoundException e) {
            throw new IOException("Erro ao deserializar resposta", e);
        } catch (IOException e) {
            if (conn != null) {
                conn.invalidate();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }
    
    /**
     * Invocação usando conexão dedicada
     */
    private RespostaDTO invocarDedicado(Message pedido) throws IOException {
        try {
            DataOutputStream out = dedicatedConnection.getOutputStream();
            lockEscrita.lock();
            try {
                protocolo.enviar(pedido, out);
                out.flush();
            } finally {
                lockEscrita.unlock();
            }
            
            Object resposta = demux.aguardar(pedido.getTag());
            return (RespostaDTO) resposta;
            
        } catch (Exception e) {
            throw new IOException("Erro ao invocar: " + e.getMessage(), e);
        }
    }
    
    public boolean isConectado() {
        return conectado && !serverShutdown;
    }
    
    public boolean isServerShutdown() {
        return serverShutdown;
    }
    
    public boolean isUsePool() {
        return usePool;
    }
    
    public String getConnectionStats() {
        if (usePool && connectionPool != null) {
            return connectionPool.getStats();
        } else {
            return "Conexão dedicada: " + (dedicatedConnection != null ? "ativa" : "inativa");
        }
    }
    
    private void garantirConexao() throws IOException {
        if (serverShutdown) {
            throw new IOException("Servidor foi encerrado");
        }
        
        if (!isConectado()) {
            lockEscrita.lock();
            try {
                if (!isConectado()) {
                    desconectar();
                    conectar();
                }
            } finally {
                lockEscrita.unlock();
            }
        }
    }
}