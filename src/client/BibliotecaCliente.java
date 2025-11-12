package src.client;

import src.common.Mensagem;
import src.common.Evento;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Biblioteca de comunicação com o servidor.
 * Suporta múltiplas threads enviando pedidos em paralelo.
 */
public class BibliotecaCliente {
    private String host;
    private int porta;
    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;
    private ReentrantLock lock; // Para sincronizar envio/recepção
    private boolean conectado;

    public BibliotecaCliente(String host, int porta) {
        this.host = host;
        this.porta = porta;
        this.lock = new ReentrantLock(true); // Lock justo (FIFO)
        this.conectado = false;
    }

    /**
     * Estabelece conexão com o servidor
     */
    public void conectar() throws IOException {
        if (conectado) {
            return;
        }

        socket = new Socket(host, porta);
        input = new DataInputStream(socket.getInputStream());
        output = new DataOutputStream(socket.getOutputStream());
        conectado = true;
    }

    /**
     * Fecha a conexão com o servidor
     */
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

    /**
     * Regista um novo utilizador
     */
    public boolean registar(String username, String password) throws IOException {
        lock.lock();
        try {
            Mensagem pedido = Mensagem.criarRegistarUtilizador(username, password);
            enviarMensagem(pedido);

            Mensagem resposta = receberMensagem();
            return resposta.isSuccesso();

        } finally {
            lock.unlock();
        }
    }

    /**
     * Autentica um utilizador
     */
    public boolean autenticar(String username, String password) throws IOException {
        lock.lock();
        try {
            Mensagem pedido = Mensagem.criarAutenticar(username, password);
            enviarMensagem(pedido);

            Mensagem resposta = receberMensagem();
            return resposta.isSuccesso();

        } finally {
            lock.unlock();
        }
    }

    /**
     * Regista um evento de venda no dia corrente
     */
    // public boolean registarEvento(String produto, int quantidade, double preco) throws IOException {
    //     lock.lock();
    //     try {
    //         Evento evento = new Evento(produto, quantidade, preco);
    //         Mensagem pedido = Mensagem.criarRegistarEvento(evento);
    //         enviarMensagem(pedido);

    //         Mensagem resposta = receberMensagem();
    //         return resposta.isSuccesso();

    //     } finally {
    //         lock.unlock();
    //     }
    // }

    /**
     * Consulta agregação sobre dias anteriores
     * @param dias Número de dias anteriores (1 a D)
     * @param produto Nome do produto (vazio para todos)
     * @param tipo Tipo de agregação (1-4)
     * @return Resultado da agregação
     */
    // public double consultarAgregacao(int dias, String produto, int tipo) throws IOException {
    //     lock.lock();
    //     try {
    //         Mensagem pedido = Mensagem.criarConsultarAgregacao(dias, produto, tipo);
    //         enviarMensagem(pedido);

    //         Mensagem resposta = receberMensagem();
    //         if (!resposta.isSuccesso()) {
    //             throw new IOException("Erro no servidor: " + resposta.getMensagemErro());
    //         }

    //         return resposta.getResultadoAgregacao();

    //     } finally {
    //         lock.unlock();
    //     }
    // }

    /**
     * Filtra eventos de produtos específicos num dia anterior
     * @param dias Número de dias anteriores
     * @param produtos Array de nomes de produtos
     * @return String com eventos formatados
     */
    // public String filtrarEventos(int dias, String[] produtos) throws IOException {
    //     lock.lock();
    //     try {
    //         Mensagem pedido = Mensagem.criarFiltrarEventos(dias, produtos);
    //         enviarMensagem(pedido);

    //         Mensagem resposta = receberMensagem();
    //         if (!resposta.isSuccesso()) {
    //             throw new IOException("Erro no servidor: " + resposta.getMensagemErro());
    //         }

    //         List<Evento> eventos = resposta.getEventos();
    //         if (eventos == null || eventos.isEmpty()) {
    //             return "Nenhum evento encontrado.";
    //         }

    //         StringBuilder sb = new StringBuilder();
    //         sb.append(String.format("Total de eventos: %d\n\n", eventos.size()));

    //         int i = 1;
    //         for (Evento evento : eventos) {
    //             sb.append(String.format("%d. %s\n", i++, evento.toString()));
    //         }

    //         return sb.toString();

    //     } finally {
    //         lock.unlock();
    //     }
    // }

    /**
     * Notificação bloqueante: vendas simultâneas
     * Bloqueia até dois produtos serem vendidos no mesmo dia ou o dia terminar
     */
    // public boolean notificarVendasSimultaneas(String produto1, String produto2) throws IOException {
    //     lock.lock();
    //     try {
    //         Mensagem pedido = Mensagem.criarNotificarSimultaneas(produto1, produto2);
    //         enviarMensagem(pedido);

    //         // Recebe resposta (bloqueia até condição satisfeita)
    //         Mensagem resposta = receberMensagem();
    //         return resposta.isSuccesso();

    //     } finally {
    //         lock.unlock();
    //     }
    // }

    /**
     * Notificação bloqueante: vendas consecutivas
     * Bloqueia até um produto ter n vendas consecutivas ou o dia terminar
     * @return Nome do produto ou null se dia terminou
     */
    // public String notificarVendasConsecutivas(int n) throws IOException {
    //     lock.lock();
    //     try {
    //         Mensagem pedido = Mensagem.criarNotificarConsecutivas(n);
    //         enviarMensagem(pedido);

    //         // Recebe resposta (bloqueia até condição satisfeita)
    //         Mensagem resposta = receberMensagem();
    //         if (resposta.isSuccesso()) {
    //             return resposta.getProdutoConsecutivo();
    //         } else {
    //             return null; // Dia terminou
    //         }

    //     } finally {
    //         lock.unlock();
    //     }
    // }

    /**
     * Obtém informações gerais do servidor
     */
    // public String obterInformacoesServidor() throws IOException {
    //     lock.lock();
    //     try {
    //         Mensagem pedido = Mensagem.criarInfoServidor();
    //         enviarMensagem(pedido);

    //         Mensagem resposta = receberMensagem();
    //         if (!resposta.isSuccesso()) {
    //             return "Servidor não disponível";
    //         }

    //         return resposta.getInfoServidor();

    //     } finally {
    //         lock.unlock();
    //     }
    // }

    /**
     * Envia uma mensagem para o servidor
     */
    private void enviarMensagem(Mensagem msg) throws IOException {
        msg.serializar(output);
        output.flush();
    }

    /**
     * Recebe uma mensagem do servidor
     */
    private Mensagem receberMensagem() throws IOException {
        return Mensagem.deserializar(input);
    }

    /**
     * Verifica se está conectado
     */
    public boolean isConectado() {
        return conectado && socket != null && socket.isConnected() && !socket.isClosed();
    }
}