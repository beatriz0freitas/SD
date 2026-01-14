package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

import client.connection.ConnectionPool;
import client.connection.PooledConnection;
import common.dto.AgregacaoDTO;
import common.dto.AgregacaoRequestDTO;
import common.dto.EventoDTO;
import common.dto.EventosFiltradosDTO;
import common.dto.FiltrarEventosDTO;
import common.dto.NotificacaoDTO;
import common.dto.RespostaDTO;
import common.dto.UsuarioDTO;
import middleware.Message;
import middleware.Protocolo;

/**
 * Middleware de comunicação do cliente.
 * 
 * Responsabilidades:
 * 1. Gerir conexão ao servidor (dedicada ou pool)
 * 2. Enviar requests e aguardar respostas
 * 3. Demultiplexar respostas assíncronas (por tag)
 * 4. Garantir thread-safety em operações de rede
 * 
 * Fluxo:
 * - Cliente chama enviar(serviceId, methodId, parametros)
 * - Middleware gera tag única, serializa parametros
 * - Envia Message ao servidor
 * - Demultiplexer (thread separada) lê respostas em loop
 * - Quando resposta chega, notifica thread que aguarda (por tag)
 */
public class ClienteMiddleware {
    private final String host;
    private final int porta;
    private final Protocolo protocolo;

    // Locks para segurança concorrente
    private final ReentrantLock lockEscrita = new ReentrantLock();     // Protege escrita ao socket
    private final ReentrantLock lockTags = new ReentrantLock();        // Protege gerador de tags
    private long contadorPedidos = 0;                                  // Tag global

    // Conexões
    private ConnectionPool connectionPool;          // Se usePool=true
    private PooledConnection dedicatedConnection;   // Se usePool=false
    private final boolean usePool;

    // Demultiplexer para respostas assíncronas
    private Demultiplexer demux;
    private Thread threadDemux;

    private final ClientShutdownHandler shutdownHandler;

    private final ReentrantLock stateLock = new ReentrantLock();
    private boolean conectado;
    private boolean serverShutdown;

    public ClienteMiddleware(String host, int porta) {
        this(host, porta, false, 1);
    }

    public ClienteMiddleware(String host, int porta, boolean usePool, int maxConnections) {
        this.host = host;
        this.porta = porta;
        this.protocolo = new Protocolo();
        this.usePool = usePool;

        this.shutdownHandler = new ClientShutdownHandler(() -> {
            setServerShutdown(true);
            setConectado(false);
        });

        if (usePool) {
            int poolSize = maxConnections;
            if (poolSize != 1) poolSize = 1;
            this.connectionPool = new ConnectionPool(host, porta, poolSize);
        }
    }

    private long nextTag() {
        lockTags.lock();
        try {
            return ++contadorPedidos;
        } finally {
            lockTags.unlock();
        }
    }

    public void conectar() throws IOException {
        lockEscrita.lock();
        try {
            if (isConectado()) return;
            if (isServerShutdown()) throw new IOException("Servidor foi encerrado. Não é possível reconectar.");

            if (usePool) {
                if (connectionPool == null) {
                    throw new IOException("ConnectionPool não inicializado");
                }
            } else {
                dedicatedConnection = new PooledConnection(host, porta, null);
                DataInputStream entrada = dedicatedConnection.getInputStream();

                demux = new Demultiplexer(entrada, shutdownHandler);
                threadDemux = new Thread(demux, "Demux-" + host + ":" + porta);
                threadDemux.start();
            }

            setConectado(true);
        } finally {
            lockEscrita.unlock();
        }
    }

    public void desconectar() {
        lockEscrita.lock();
        try {
            setConectado(false);

            if (usePool) {
                if (connectionPool != null) connectionPool.close();
            } else {
                if (demux != null) demux.parar();
                if (dedicatedConnection != null) dedicatedConnection.closePhysical();
            }
        } finally {
            lockEscrita.unlock();
        }
    }

    public RespostaDTO invocar(byte serviceId, byte methodId, Object parametros) throws IOException {
        if (isServerShutdown()) throw new IOException("Servidor foi encerrado. Operação não disponível.");

        garantirConexao();

        long tag = nextTag();
        byte[] payload = encodeParametros(parametros);
        Message pedido = Message.request(tag, serviceId, methodId, payload);

        try {
            return usePool ? invocarComPool(pedido) : invocarDedicado(pedido);
        } catch (Exception e) {
            if (isServerShutdown()) throw new IOException("Servidor encerrado: " + e.getMessage(), e);
            throw new IOException("Erro ao invocar: " + e.getMessage(), e);
        }
    }

    private byte[] encodeParametros(Object parametros) throws IOException {
        if (parametros == null) return null;

        if (parametros instanceof UsuarioDTO) return ((UsuarioDTO) parametros).serialize();
        if (parametros instanceof EventoDTO) return ((EventoDTO) parametros).serialize();
        if (parametros instanceof NotificacaoDTO) return ((NotificacaoDTO) parametros).serialize();
        if (parametros instanceof FiltrarEventosDTO) return ((FiltrarEventosDTO) parametros).serialize();
        if (parametros instanceof EventosFiltradosDTO) return ((EventosFiltradosDTO) parametros).serialize();
        if (parametros instanceof AgregacaoRequestDTO) return ((AgregacaoRequestDTO) parametros).serialize();
        if (parametros instanceof AgregacaoDTO) return ((AgregacaoDTO) parametros).serialize();

        throw new IOException("Tipo de parâmetros não suportado (use um DTO): " +
                parametros.getClass().getName());
    }

    private RespostaDTO invocarComPool(Message pedido) throws IOException, InterruptedException {
        if (connectionPool == null) throw new IOException("Pool não inicializado");

        PooledConnection conn = null;
        try {
            conn = connectionPool.getConnection();

            DataOutputStream out = conn.getOutputStream();
            protocolo.enviar(pedido, out);

            DataInputStream in = conn.getInputStream();
            Message resposta = protocolo.receber(in);

            if (!resposta.isResponse() || resposta.getTag() != pedido.getTag()) {
                throw new IOException("Resposta inválida recebida (tag mismatch)");
            }

            if (resposta.getTag() == -1) {
                setServerShutdown(true);
                setConectado(false);
                shutdownHandler.onShutdown();
                throw new IOException("Servidor encerrado");
            }

            byte[] payload = resposta.getPayload();
            if (payload == null) throw new IOException("Resposta sem payload (esperado RespostaDTO)");

            return RespostaDTO.deserialize(payload);

        } catch (IOException e) {
            if (conn != null) conn.invalidate();
            throw e;
        } finally {
            if (conn != null) conn.close();
        }
    }

    private RespostaDTO invocarDedicado(Message pedido) throws IOException {
        if (dedicatedConnection == null) throw new IOException("Conexão dedicada não inicializada");
        if (demux == null) throw new IOException("Demultiplexer não inicializado");

        try {
            DataOutputStream out = dedicatedConnection.getOutputStream();

            
            lockEscrita.lock();
            try {
                protocolo.enviar(pedido, out);
            } finally {
                lockEscrita.unlock();
            }

            byte[] respostaBytes = demux.aguardar(pedido.getTag());
            if (respostaBytes == null) throw new IOException("Resposta vazia recebida (payload null)");

            return RespostaDTO.deserialize(respostaBytes);

        } catch (Exception e) {
            throw new IOException("Erro ao invocar: " + e.getMessage(), e);
        }
    }

    private void garantirConexao() throws IOException {
        if (isServerShutdown()) throw new IOException("Servidor foi encerrado");

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

    public boolean isConectado() {
        stateLock.lock();
        try {
            return conectado && !serverShutdown;
        } finally {
            stateLock.unlock();
        }
    }

    private boolean isServerShutdown() {
        stateLock.lock();
        try {
            return serverShutdown;
        } finally {
            stateLock.unlock();
        }
    }

    private void setServerShutdown(boolean value) {
        stateLock.lock();
        try {
            serverShutdown = value;
        } finally {
            stateLock.unlock();
        }
    }

    private void setConectado(boolean value) {
        stateLock.lock();
        try {
            conectado = value;
        } finally {
            stateLock.unlock();
        }
    }
}
