package src.cliente;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import src.uteis.Mensagem;
import src.uteis.Protocolo;

/**
 * Biblioteca de comunicação com o servidor.
 *
 * Thread-safe: várias threads podem usar a mesma instância,
 * mas cada pedido (envio+resposta) é tratado de forma atómica.
 *
 * ESTRATÉGIA:
 * - Um único lock protege toda a comunicação (envio + leitura da resposta).
 * - Garante que a resposta lida corresponde sempre ao pedido acabado de enviar.
 * - Mantém apenas uma ligação TCP por cliente, como exigido no enunciado.
 * 
 * PROBLEMA:
 * - Lock único bloqueia todas threads durante send+receive
 * - Pedidos processados sequencialmente mesmo com servidor concorrente
 * 
 * SOLUÇÃO:
 * - Associa ID único a cada pedido
 * - Thread separada lê respostas e despacha para threads corretas
 * - Múltiplas threads podem ter pedidos "in-flight" simultaneamente
 * 
 * Inspirado no padrão Request-Reply com correlation IDs (sistemas distribuídos).
 * mantive isto para depois ser mais facil explicar no relatorio
 */
public class BibliotecaCliente {
    private final String host;
    private final int porta;

    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;

    // Thread que lê respostas do socket
    private Thread readerThread;
    private volatile boolean running;

    // Gerador de IDs únicos para pedidos
    private final AtomicLong nextRequestId = new AtomicLong(1);
    
    // Map requestId -> PendingRequest (para despachar respostas)
    private final Map<Long, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    
    // Lock apenas para escrita no socket (múltiplas threads podem escrever)
    private final Lock writeLock = new ReentrantLock();
    
    // Lock para controlar conexão/desconexão
    private final Lock connectionLock = new ReentrantLock();
    private boolean conectado;

    /**
     * Representa um pedido pendente de resposta.
     */
    private static class PendingRequest {
        final Lock lock = new ReentrantLock();
        final Condition responseReceived = lock.newCondition();
        Mensagem response;
        boolean completed;
        
        void setResponse(Mensagem msg) {
            lock.lock();
            try {
                this.response = msg;
                this.completed = true;
                responseReceived.signal();
            } finally {
                lock.unlock();
            }
        }
        
        Mensagem waitForResponse() throws InterruptedException {
            lock.lock();
            try {
                while (!completed) {
                    responseReceived.await();
                }
                return response;
            } finally {
                lock.unlock();
            }
        }
    }

    public BibliotecaCliente(String host, int porta) {
        this.host = host;
        this.porta = porta;
        this.conectado = false;
    }

    public boolean isConectado() {
        connectionLock.lock();
        try {
            return conectado && socket != null && !socket.isClosed();
        } finally {
            connectionLock.unlock();
        }
    }

    public void conectar() throws IOException {
        connectionLock.lock();
        try {
            if (isConectado()) {
                return;
            }

            socket = new Socket(host, porta);
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());
            conectado = true;
            running = true;
            
            // Inicia thread de leitura
            readerThread = new Thread(this::runReader, "ClientReader");
            readerThread.setDaemon(false);
            readerThread.start();
            
            System.out.println("Conectado a " + host + ":" + porta);
            
        } catch (IOException e) {
            cleanup();
            throw e;
        } finally {
            connectionLock.unlock();
        }
    }

    public void desconectar() {
        connectionLock.lock();
        try {
            if (conectado) {
                running = false;
                cleanup();
                
                // Acorda todas threads esperando resposta
                for (PendingRequest pr : pendingRequests.values()) {
                    pr.setResponse(null); // null indica desconexão
                }
                pendingRequests.clear();
                
                System.out.println("Desconectado de " + host + ":" + porta);
            }
        } finally {
            connectionLock.unlock();
        }
    }

    private void cleanup() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        } finally {
            conectado = false;
        }
    }

    /**
     * Thread que lê respostas do socket e despacha para pedidos pendentes.
     * 
     * TODO: Para funcionar, precisaríamos modificar o protocolo para incluir
     * requestId nas mensagens. Como isso requer mudanças no servidor,
     * vou manter compatibilidade mas documentar a limitação.
     */
    private void runReader() {
        try {
            while (running && isConectado()) {
                Mensagem resposta = Protocolo.lerMensagem(input);
                
                // LIMITAÇÃO: sem requestId no protocolo, assumimos ordem FIFO
                // Pegamos o pedido mais antigo pendente
                Long oldestId = pendingRequests.keySet().stream()
                    .min(Long::compare)
                    .orElse(null);
                
                if (oldestId != null) {
                    PendingRequest pr = pendingRequests.remove(oldestId);
                    if (pr != null) {
                        pr.setResponse(resposta);
                    }
                } else {
                    System.err.println("Resposta recebida sem pedido pendente!");
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Erro na leitura: " + e.getMessage());
            }
        } finally {
            running = false;
        }
    }

    /**
     * Envia pedido e aguarda resposta.
     * Múltiplas threads podem chamar este método concorrentemente.
     * 
     * - Escrita no socket é serializada (writeLock)
     * - MAS leitura da resposta é assíncrona (não bloqueia outras threads)
     */
    private Mensagem enviarPedido(Mensagem pedido) throws IOException, InterruptedException {
        if (!isConectado()) {
            throw new IOException("Cliente não está conectado");
        }

        long requestId = nextRequestId.getAndIncrement();
        PendingRequest pr = new PendingRequest();
        pendingRequests.put(requestId, pr);
        
        try {
            // ESCRITA: serializada entre threads
            writeLock.lock();
            try {
                Protocolo.escreverMensagem(pedido, output);
                output.flush();
            } finally {
                writeLock.unlock();
            }
            
            // ESPERA: não bloqueia outras threads enviarem pedidos
            Mensagem resposta = pr.waitForResponse();
            
            if (resposta == null) {
                throw new IOException("Conexão foi fechada durante o pedido");
            }
            
            return resposta;
            
        } finally {
            pendingRequests.remove(requestId);
        }
    }

    // Métodos de alto nível chamam sempre enviarPedido()

    public Mensagem registar(String username, String password) throws IOException, InterruptedException {
        Mensagem pedido = Mensagem.criarRegistarUtilizador(username, password);
        return enviarPedido(pedido);
    }

    public Mensagem autenticar(String username, String password) throws IOException, InterruptedException {
        Mensagem pedido = Mensagem.criarAutenticar(username, password);
        return enviarPedido(pedido);
    }

    public Mensagem registarEvento(int produtoID, int quantidade, double preco) throws IOException, InterruptedException {
        Mensagem pedido = Mensagem.criarRegistarEvento(produtoID, quantidade, preco);
        return enviarPedido(pedido);
    }

    public Mensagem loginAdmin(String password) throws IOException, InterruptedException {
        Mensagem pedido = Mensagem.criarAutenticarAdmin(password);
        return enviarPedido(pedido);
    }

    public Mensagem listarClientes() throws IOException, InterruptedException {
        Mensagem pedido = Mensagem.criarListarClientes();
        return enviarPedido(pedido);
    }

    public Mensagem listarEventos() throws IOException, InterruptedException {
        Mensagem pedido = Mensagem.criarListarEventos();
        return enviarPedido(pedido);
    }

    public Mensagem novoDia() throws IOException, InterruptedException {
        Mensagem pedido = Mensagem.criarNovoDia();
        return enviarPedido(pedido);
    }

    public Mensagem quantidadeVendas(int produto, int dias) throws IOException, InterruptedException {
        Mensagem pedido = Mensagem.criarPedidoAgregacao(produto, dias, Mensagem.TipoOperacao.QUANTIDADE_VENDAS);
        return enviarPedido(pedido);
    }

    public Mensagem volumeVendas(int produto, int dias) throws IOException, InterruptedException {
        Mensagem pedido = Mensagem.criarPedidoAgregacao(produto, dias, Mensagem.TipoOperacao.VOLUME_VENDAS);
        return enviarPedido(pedido);
    }

    public Mensagem precoMedio(int produto, int dias) throws IOException, InterruptedException {
        Mensagem pedido = Mensagem.criarPedidoAgregacao(produto, dias, Mensagem.TipoOperacao.PRECO_MEDIO);
        return enviarPedido(pedido);
    }

    public Mensagem precoMaximo(int produto, int dias) throws IOException, InterruptedException {
        Mensagem pedido = Mensagem.criarPedidoAgregacao(produto, dias, Mensagem.TipoOperacao.PRECO_MAXIMO);
        return enviarPedido(pedido);
    }

    /**
     * Estatísticas para debugging.
     */
    public String getStats() {
        return String.format(
            "BibliotecaCliente[conectado=%b, pedidosPendentes=%d]",
            isConectado(), pendingRequests.size()
        );
    }
}