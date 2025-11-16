package src.servidor;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import src.uteis.Evento;
import src.uteis.Mensagem;
import src.uteis.PayloadParser;
import src.uteis.Protocolo;  // ← ADICIONAR

/**
 * Thread que processa pedidos de um cliente específico
 * 
 * CONCORRÊNCIA:
 * - Thread principal: lê pedidos do socket (loop while)
 * - ThreadPool: processa cada pedido em thread separada
 * - Lock de escrita: garante que respostas não se misturam
 */
public class ClienteHandler implements Runnable {
    
    private Socket clienteSocket;
    private GestorUtilizadores gestorUtilizadores;
    private GestorEventos gestorEventos; 

    private DataInputStream input;
    private DataOutputStream output;

    private String username; // null = não autenticado
    private boolean ativo;   // true = conexão ativa

    private ExecutorService threadPool; 
    private final ReentrantLock outputLock;
    private boolean isAdmin ;
    private static final String ADMIN_PASSWORD = "admin123"; 
    
    public ClienteHandler(Socket clienteSocket, GestorUtilizadores gestorUtilizadores, GestorEventos gestorEventos) {
        this.clienteSocket = clienteSocket;
        this.gestorUtilizadores = gestorUtilizadores;
        this.gestorEventos = gestorEventos;
        this.username = null;
        this.ativo = true;
        this.threadPool = Executors.newCachedThreadPool();
        this.outputLock = new ReentrantLock();
        this.isAdmin = false;
    }
    
    public boolean isAutenticado() {
        return username != null;
    }
    
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
                    // ← MUDANÇA: usar Protocolo em vez de Mensagem
                    Mensagem pedido = Protocolo.lerMensagem(input);
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
    
    private void processarPedidoAssinc(Mensagem pedido) {
        try {
            Mensagem resposta = processarPedido(pedido);
            
            outputLock.lock();
            try {
               
                Protocolo.escreverMensagem(resposta, output);
            } finally {
                outputLock.unlock();
            }
            
        } catch (IOException e) {
            System.err.println("Erro ao enviar resposta: " + e.getMessage());
        }
    }

    private Mensagem processarPedido(Mensagem pedido) {
        try {

            Mensagem.TipoOperacao tipo = pedido.getTipo();

            if (tipo == Mensagem.TipoOperacao.REGISTO) {
                return processarRegisto(pedido);
            }
            if (tipo == Mensagem.TipoOperacao.LOGIN) {
                return processarLogin(pedido);
            }

            if (tipo == Mensagem.TipoOperacao.LOGIN_ADMIN) {
                return processarLoginAdmin(pedido);
            }
            

            if (!isAutenticado()) {
                return Mensagem.criarRespostaErro("Operação requer autenticação");
            }

            switch (tipo) {
                case REG_EVENTO:
                    return processarRegistarEvento(pedido);
                case NOVO_DIA:
                    if (!isAdmin) {
                        return Mensagem.criarRespostaErro("Apenas administrador pode avançar o dia");
                    }
                    return processarNovoDia();
                
                case LISTAR_CLIENTES:
                    if (!isAdmin) {
                        return Mensagem.criarRespostaErro("Acesso negado");
                    }
                    return processarListarClientes();

                case LISTAR_EVENTOS:
                    if (!isAdmin) {
                        return Mensagem.criarRespostaErro("Acesso negado");
                    }
                    return processarListarEventos();
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
            return Mensagem.criarRespostaErro("Username já existe");

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

    private Mensagem processarLoginAdmin(Mensagem pedido) throws IOException {
        String password = PayloadParser.lerPasswordAdmin(pedido.getPayload());
        
        if (ADMIN_PASSWORD.equals(password)) {
            this.isAdmin = true;
            this.username = "ADMIN";
            System.out.println("Administrador autenticado");
            return Mensagem.criarRespostaOk("Login de administrador bem-sucedido");
        } else {
            return Mensagem.criarRespostaErro("Senha de administrador inválida");
        }
    }

    private Mensagem processarListarClientes() throws IOException {
        String listaClientes = gestorUtilizadores.listarUtilizadores();
        return Mensagem.criarRespostaOk(listaClientes);
    }
    
    private Mensagem processarListarEventos() throws IOException {
        String listaEventos = gestorEventos.listarEventos();
        return Mensagem.criarRespostaOk(listaEventos);
    }

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