package src.cliente;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.locks.ReentrantLock;
import src.uteis.Mensagem;
import src.uteis.Protocolo;  // ← ADICIONAR

/**
 * Biblioteca de comunicação com o servidor.
 * OTIMIZADO: Suporta múltiplas threads enviando pedidos concorrentemente.
 * 
 * ESTRATÉGIA:
 * - Lock separado para ESCRITA (evita mistura de pedidos)
 * - Lock separado para LEITURA (evita mistura de respostas)
 * - Locks NÃO abrangem a espera (permite concorrência real)
 */
public class BibliotecaCliente {
    private final String host;
    private final int porta;

    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;
    
    private final ReentrantLock writeLock;
    private final ReentrantLock readLock;
    private boolean conectado;

    public BibliotecaCliente(String host, int porta) {
        this.host = host;
        this.porta = porta;
        this.writeLock = new ReentrantLock(true);
        this.readLock = new ReentrantLock(true);
        this.conectado = false;
    }

    public boolean isConectado() {
        return conectado && socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void conectar() throws IOException {
        if (conectado) return;
        socket = new Socket(host, porta);
        input = new DataInputStream(socket.getInputStream());
        output = new DataOutputStream(socket.getOutputStream());
        conectado = true;
    }

    public void desconectar() {
        conectado = false;
        try {
            if (output != null) output.close();
            if (input != null) input.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Erro ao fechar conexão: " + e.getMessage());
        }
    }

    private Mensagem enviarPedido(Mensagem pedido) throws IOException {
        // FASE 1: Enviar
        writeLock.lock();
        try {
            // ← MUDANÇA: usar Protocolo em vez de Mensagem
            Protocolo.escreverMensagem(pedido, output);
        } finally {
            writeLock.unlock();
        }
        
        // FASE 2: Receber
        readLock.lock();
        try {
            // ← MUDANÇA: usar Protocolo em vez de Mensagem
            return Protocolo.lerMensagem(input);
        } finally {
            readLock.unlock();
        }
    }

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
    
    public Mensagem novoDia() throws IOException {
        Mensagem pedido = Mensagem.criarNovoDia();
        return enviarPedido(pedido);
    }
}