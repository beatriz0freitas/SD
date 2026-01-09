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

public class ClienteMiddleware {
    private final String host;
    private final int porta;
    private final Protocolo protocolo;

    // Lock de estado + escrita no socket dedicado (mantém simples)
    private final ReentrantLock lockEscrita = new ReentrantLock();

   
    private final ReentrantLock lockTags = new ReentrantLock();
    private long contadorPedidos = 0;

    private ConnectionPool connectionPool;
    private final boolean usePool;

    private PooledConnection dedicatedConnection;
    private Demultiplexer demux;
    private Thread threadDemux;

    private final ClientShutdownHandler shutdownHandler;

    private volatile boolean conectado;
    private volatile boolean serverShutdown;

    public ClienteMiddleware(String host, int porta) {
        this(host, porta, false, 1);
    }

    public ClienteMiddleware(String host, int porta, boolean usePool, int maxConnections) {
        this.host = host;
        this.porta = porta;
        this.protocolo = new Protocolo();
        this.usePool = usePool;

        this.shutdownHandler = new ClientShutdownHandler(() -> {
            serverShutdown = true;
            conectado = false;
        });

        if (usePool) {
            this.connectionPool = new ConnectionPool(host, porta, maxConnections);
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
            if (conectado) return;
            if (serverShutdown) throw new IOException("Servidor foi encerrado. Não é possível reconectar.");

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

            conectado = true;
        } finally {
            lockEscrita.unlock();
        }
    }

    public void desconectar() {
        lockEscrita.lock();
        try {
            conectado = false;

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
        if (serverShutdown) throw new IOException("Servidor foi encerrado. Operação não disponível.");

        garantirConexao();

        long tag = nextTag();
        byte[] payload = encodeParametros(parametros);
        Message pedido = Message.request(tag, serviceId, methodId, payload);

        try {
            return usePool ? invocarComPool(pedido) : invocarDedicado(pedido);
        } catch (Exception e) {
            if (serverShutdown) throw new IOException("Servidor encerrado: " + e.getMessage(), e);
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
                serverShutdown = true;
                conectado = false;
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

            // DEDICADO: lock obrigatório
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
        if (serverShutdown) throw new IOException("Servidor foi encerrado");

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
        return conectado && !serverShutdown;
    }
}