package src.servidor;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import src.uteis.Evento;
import src.uteis.Mensagem;
import src.uteis.PayloadParser;
import src.uteis.Protocolo; 
import src.uteis.ThreadPool;

/**
 * Thread que processa pedidos de um cliente específico
 * 
 * CONCORRÊNCIA:
 * - Thread principal: lê pedidos do socket (loop while)
 * - ThreadPool: processa cada pedido em thread separada
 * - Lock de escrita: garante que respostas não se misturam
 */
public class ClienteHandler implements Runnable {
    
    
    private GestorUtilizadores gestorUtilizadores;
    private GestorEventos gestorEventos; 

    private Socket clienteSocket;
    private DataInputStream input;
    private DataOutputStream output;

    private String username; // null = não autenticado
    private boolean ativo;   // true = conexão ativa

    //private ExecutorService threadPool; 
    private int N_THREADS = 10;                            // digamos 1 aceptor e 10 handlers
    private ThreadPool workers = new ThreadPool(N_THREADS);

    // Lock de escrita apenas para o output do socket.
    // Necessário porque vários pedidos deste cliente são processados
    // em paralelo pelo threadPool e podem tentar escrever ao mesmo tempo.
    // Não usamos ReadWriteLock porque só há escritas concorrentes no socket.
    private final ReentrantLock outputLock;
    private boolean isAdmin ;
    private static final String ADMIN_PASSWORD = "admin123"; 
    
    public ClienteHandler(Socket clienteSocket, GestorUtilizadores gestorUtilizadores, GestorEventos gestorEventos) {
        this.clienteSocket = clienteSocket;
        this.gestorUtilizadores = gestorUtilizadores;
        this.gestorEventos = gestorEventos;
        this.username = null;
        this.ativo = true;
        //this.threadPool = Executors.newCachedThreadPool();
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
                    
                    Mensagem pedido = Protocolo.lerMensagem(input);
                    // threadPool.execute(() -> processarPedidoAssinc(pedido));
                    workers.submit(() -> processarPedidoAssinc(pedido));
                                        
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
            
            // Secção crítica de escrita: garante que apenas uma thread de pedido
            // escreve no DataOutputStream de cada vez, evitando mistura de respostas.
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
                case QUANTIDADE_VENDAS: return processarQuantidadeVendas(pedido);
                case VOLUME_VENDAS: return processarVolumeVendas(pedido);
                case PRECO_MEDIO: return processarPrecoMedio(pedido);
                case PRECO_MAXIMO: return processarPrecoMaximo(pedido);
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
        String listaEventos = gestorEventos.listarEventosDiaAtual();
        return Mensagem.criarRespostaOk(listaEventos);
    }

    private Mensagem processarQuantidadeVendas(Mensagem pedido) throws IOException {
        byte[] payload = pedido.getPayload();
        ByteBuffer bb = ByteBuffer.wrap(payload);
        int produto = bb.getInt();
        int dias = bb.getInt();

        // atualiza a cache se nao estiver registado
        int res = gestorEventos.getCacheQuantidade(produto, dias);

        return Mensagem.criarRespostaOk("Quantidade de Vendas nos últimos " + dias + " dias: " + res);
    }

    private Mensagem processarVolumeVendas(Mensagem pedido) throws IOException {
        byte[] payload = pedido.getPayload();
        ByteBuffer bb = ByteBuffer.wrap(payload);
        int produto = bb.getInt();
        int dias = bb.getInt();

        double res = gestorEventos.getCacheVolume(produto, dias);

        return Mensagem.criarRespostaOk("Volume de Vendas nos últimos " + dias + " dias: " + res);
    }

    private Mensagem processarPrecoMedio(Mensagem pedido) throws IOException {
        byte[] payload = pedido.getPayload();
        ByteBuffer bb = ByteBuffer.wrap(payload);
        int produto = bb.getInt();
        int dias = bb.getInt();

        double res = gestorEventos.getCachePrecoMedio(produto, dias);

        return Mensagem.criarRespostaOk("Preço Médio de Vendas nos últimos " + dias + " dias: " + res);
    }

    private Mensagem processarPrecoMaximo(Mensagem pedido) throws IOException {
        byte[] payload = pedido.getPayload();
        ByteBuffer bb = ByteBuffer.wrap(payload);
        int produto = bb.getInt();
        int dias = bb.getInt();

        double res = gestorEventos.getCachePrecoMaximo(produto, dias);

        return Mensagem.criarRespostaOk("Preço Máximo de Vendas nos últimos " + dias + " dias: " + res);
    }

    private void fecharConexao() {
        ativo = false;
        try {
            //threadPool.shutdownNow();
            // TODO
            if (output != null) output.close();
            if (input != null) input.close();
            if (clienteSocket != null) clienteSocket.close();
        } catch (IOException e) {
            System.err.println("Erro ao fechar conexão: " + e.getMessage());
        }
    }
}