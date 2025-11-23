package src.cliente;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
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
 */
public class BibliotecaCliente {
    private final String host;
    private final int porta;

    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;

    // Lock único para proteger socket/input/output
    private final ReentrantLock lock;
    private boolean conectado;

    public BibliotecaCliente(String host, int porta) {
        this.host = host;
        this.porta = porta;
        this.lock = new ReentrantLock(true); 
        this.conectado = false;
    }

    public boolean isConectado() {
        lock.lock();
        try {
            return conectado
                && socket != null
                && socket.isConnected()
                && !socket.isClosed();
        } finally {
            lock.unlock();
        }
    }

    //locks em conectar e desconectar nao são estritamente necessários, só existe uma ligacao por cliente
    // mas é boa prática proteger o estado de conexão (robustez)
    public void conectar() throws IOException {
        lock.lock();
        try {
            if (isConectado()) {
                return;
            }

            Socket novoSocket = null;
            DataInputStream novoInput = null;
            DataOutputStream novoOutput = null;
            
            try {
                // Criar recursos localmente
                novoSocket = new Socket(host, porta);
                novoInput = new DataInputStream(novoSocket.getInputStream());
                novoOutput = new DataOutputStream(novoSocket.getOutputStream());
                
                // Só atribuir aos campos se tudo correu bem para nao deixar a instância em estado inconsistent
                this.socket = novoSocket;
                this.input = novoInput;
                this.output = novoOutput;
                this.conectado = true;
                
            } catch (IOException e) {
                // Cleanup: fechar recursos se algo falhou
                if (novoSocket != null && !novoSocket.isClosed()) {
                    try {
                        novoSocket.close();
                    } catch (IOException ignored) {
                        // Ignorar erros ao fechar
                    }
                }
                // Re-lançar a exceção original
                throw e;
            }
        } finally {
            lock.unlock();
        }
    }

    public void desconectar() {
        lock.lock();
        try {
            if (conectado) {
                try {
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException e) {
                    System.err.println("Erro ao fechar socket: " + e.getMessage());
                } finally {
                    conectado = false;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Envia um pedido e recebe a resposta de forma atómica.
     * Várias threads podem chamar este método, mas os pedidos
     * são enviados e respondidos um de cada vez pela mesma ligação.
     */

    //TODO: thread blocking ou seja se varias threads chamam este metodo, elas vao ficar bloqueadas ate a thread que esta a usar o lock terminar 
    // SOLUCAO: usar ids de pedido para identificar respostas assim nao precisam de 
    private Mensagem enviarPedido(Mensagem pedido) throws IOException {
        lock.lock();
        try {
            if (!conectado) {
                throw new IOException("Cliente não está conectado ao servidor.");
            }

            Protocolo.escreverMensagem(pedido, output);
            output.flush();
            return Protocolo.lerMensagem(input);

        } finally {
            lock.unlock();
        }
    }

    // Métodos de alto nível chamam sempre enviarPedido()

    public Mensagem registar(String username, String password) throws IOException {
        Mensagem pedido = Mensagem.criarRegistarUtilizador(username, password);
        return enviarPedido(pedido);
    }

    public Mensagem autenticar(String username, String password) throws IOException {
        Mensagem pedido = Mensagem.criarAutenticar(username, password);
        return enviarPedido(pedido);
    }

    public Mensagem registarEvento(int produtoID, int quantidade, double preco) throws IOException {
        Mensagem pedido = Mensagem.criarRegistarEvento(produtoID, quantidade, preco);
        return enviarPedido(pedido);
    }

    public Mensagem loginAdmin(String password) throws IOException {
        Mensagem pedido = Mensagem.criarAutenticarAdmin(password);
        return enviarPedido(pedido);
    }

    public Mensagem listarClientes() throws IOException {
        Mensagem pedido = Mensagem.criarListarClientes();
        return enviarPedido(pedido);
    }

    public Mensagem listarEventos() throws IOException {
        Mensagem pedido = Mensagem.criarListarEventos();
        return enviarPedido(pedido);
    }

    public Mensagem novoDia() throws IOException {
        Mensagem pedido = Mensagem.criarNovoDia();
        return enviarPedido(pedido);
    }

    public Mensagem quantidadeVendas(int produto, int dias) throws IOException {
        Mensagem pedido = Mensagem.criarQuantidadeVendas(produto, dias);
        return enviarPedido(pedido);
    }
}