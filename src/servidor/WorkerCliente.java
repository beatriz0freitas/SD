package src.servidor;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import src.uteis.Evento;
import src.uteis.Mensagem;
import src.uteis.PayloadParser; 

/**
 * Thread que processa pedidos de um cliente específico
 * 
 * CONCORRÊNCIA:
 * - Thread principal: lê pedidos do socket (loop while)
 * - ThreadPool: processa cada pedido em thread separada
 * - Lock de escrita: garante que respostas não se misturam
 */
//todo: nome da classe parece gpt as well (true aqui)
public class WorkerCliente implements Runnable {
    
    private Socket clienteSocket;
    private GestorUtilizadores gestorUtilizadores;
    private GestorEventos gestorEventos; 

    private DataInputStream input;
    private DataOutputStream output;

    private String username;// null = não autenticado
    private boolean ativo; // true = conexão ativa

    // Concorrência
    private ExecutorService threadPool; 
    private final ReentrantLock outputLock;
    
    public WorkerCliente(Socket clienteSocket, GestorUtilizadores gestorUtilizadores, GestorEventos gestorEventos) {
        this.clienteSocket = clienteSocket;
        this.gestorUtilizadores = gestorUtilizadores;
        this.gestorEventos = gestorEventos;
        this.username = null;
        this.ativo = true;
        this.threadPool = Executors.newCachedThreadPool();// CachedThreadPool: cria threads sob demanda, reutiliza quando disponíveis
        this.outputLock = new ReentrantLock();
    }
    
     /**
     * Verifica se o cliente está autenticado
     */
    public boolean isAutenticado() {
        return username != null;
    }
    
     /**
     * Obtém username do cliente (null se não autenticado)
     */
    public String getUsername() {
        return username;
    }

    @Override
    public void run() {
        try {
            input = new DataInputStream(clienteSocket.getInputStream());
            output = new DataOutputStream(clienteSocket.getOutputStream());
            System.out.println("Nova conexão de: " + clienteSocket.getInetAddress());
            
            while (ativo) {
                try {
                    Mensagem pedido = Mensagem.ler(input); 
                     // Processar pedido em thread separada (para permitir varios pedidos concorrentes)
                     threadPool.execute(() -> processarPedidoAssinc(pedido));
                    
                } catch (EOFException e) {
                    System.out.println("Cliente desconectado: " + (username != null ? username : clienteSocket.getInetAddress()));
                    break;

                } catch (IOException e) {
                    System.err.println("Erro na comunicação: " + e.getMessage());
                    break;
                }
            }
            
        } catch (IOException e) {
            System.err.println("Erro ao inicializar ligação: " + e.getMessage());
        } finally {
            fecharConexao();
        }
    }
    
    /**
     * Processa um pedido de forma assíncrona e envia a resposta.
     * Este método é executado numa thread do pool.
     * 
     * @param pedido Mensagem recebida do cliente
     */
    private void processarPedidoAssinc(Mensagem pedido) {
        try {
            Mensagem resposta = processarPedido(pedido);
            
            outputLock.lock();
            try {
                resposta.escrever(output);
                output.flush();
            } finally {
                outputLock.unlock();
            }
            
        } catch (IOException e) {
            System.err.println("Erro ao enviar resposta: " + e.getMessage());
        }
    }

     /**
     * Processa um pedido do cliente e retorna a resposta
     * 
     * @param pedido Mensagem com o pedido
     * @return Mensagem com a resposta
     */
    private Mensagem processarPedido(Mensagem pedido) {
        try {
            Mensagem.TipoOperacao tipo = pedido.getTipoOperacao();

            if (tipo == Mensagem.TipoOperacao.REGISTO) {
                return processarRegisto(pedido);
            }
            if (tipo == Mensagem.TipoOperacao.LOGIN) {
                return processarLogin(pedido);
            }

            if (!isAutenticado()) {
                return Mensagem.criarRespostaErro("Operação requer autenticação");
            }

            switch (tipo) {
                case REG_EVENTO:
                    return processarRegistarEvento(pedido);
                case NOVO_DIA:
                    return processarNovoDia();
                case QUANTIDADE_VENDAS:
                case VOLUME_VENDAS:
                case PRECO_MEDIO:
                case PRECO_MAXIMO:
                case FILTRAR_EVENTOS:
                case VENDAS_SIMULTANEAS:
                case VENDAS_CONSECUTIVAS:
                    return Mensagem.criarRespostaErro("Funcionalidade ainda não implementada");
                default:
                    return Mensagem.criarRespostaErro("Operação desconhecida");
            }
        } catch (IOException e) {
            try {
                return Mensagem.criarRespostaErro("Erro ao processar pedido: " + e.getMessage());
            } catch (IOException ex) {
                System.err.println("Erro crítico ao criar resposta de erro");
                return null;
            }
        }
    }
    
    private Mensagem processarRegisto(Mensagem pedido) throws IOException {
        String[] credenciais = PayloadParser.lerAutenticacao(pedido.getPayload());
        String username = credenciais[0];
        String password = credenciais[1];
        
        if (username == null || username.isBlank())
            return Mensagem.criarRespostaErro("Username inválido");
        
        if (password == null || password.length() < 4) 
            return Mensagem.criarRespostaErro("Password deve ter pelo menos 4 caracteres");
        
        if (!gestorUtilizadores.registar(username, password))
            return Mensagem.criarRespostaErro("Username já existe.");

        System.out.println("Novo utilizador registado: " + username);
        return Mensagem.criarRespostaOk("Utilizador registado com sucesso");
    }
    
    private Mensagem processarLogin(Mensagem pedido) throws IOException {
        String[] credenciais = PayloadParser.lerAutenticacao(pedido.getPayload());
        String username = credenciais[0];
        String password = credenciais[1];
        
        boolean sucesso = gestorUtilizadores.autenticar(username, password);
        
        if (sucesso) {
            this.username = username;
            System.out.println("Utilizador autenticado: " + username);
            return Mensagem.criarRespostaOk("Autenticação bem-sucedida");
        } else {
            return Mensagem.criarRespostaErro("Credenciais inválidas");
        }
    }
    
    private Mensagem processarRegistarEvento(Mensagem pedido) throws IOException {
        Evento evento = PayloadParser.lerEvento(pedido.getPayload());
    
        gestorEventos.adicionarEvento(
            evento.getProdutoID(),
            evento.getQuantidade(),
            evento.getPreco()
        );
    
        return Mensagem.criarRespostaOk("Evento registado com sucesso");
    }
    
    private Mensagem processarNovoDia() throws IOException {
        gestorEventos.iniciarNovoDia();
        return Mensagem.criarRespostaOk("Novo dia iniciado com sucesso");
    }

     /**
     * Fecha a conexão com o cliente e liberta recursos.
     * 
     * ORDEM DE FECHO:
     * 1. Parar o loop principal (ativo = false)
     * 2. Desligar threadPool (não aceita novos pedidos)
     * 3. Fechar streams
     * 4. Fechar socket
     */
    private void fecharConexao() {
        ativo = false;
        try {
            threadPool.shutdownNow();
            if (output != null) output.close();
            if (input != null) input.close();
            if (clienteSocket != null) clienteSocket.close();
        } catch (IOException e) {
            System.err.println("Erro ao fechar conexão: " + e.getMessage());
        }
    }
}
