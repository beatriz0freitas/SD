package src.servidor;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import src.uteis.Evento;
import src.uteis.Mensagem;
import src.uteis.PayloadParser;
import src.uteis.Protocolo; 
import src.uteis.ThreadPool;

/**
 * Thread que processa pedidos de um cliente específico
 * 
 * 1. ThreadPool configurável e com shutdown correto
 * 2. Ordem de locks bem definida (evita deadlocks)
 * 3. Separação clara entre I/O e processamento
 * 4. Gestão de recursos com try-with-resources onde possível
 */
public class SessaoCliente implements Runnable {
    
    private GestorUtilizadores gestorUtilizadores;
    private GestorEventos gestorEventos; 
    private Socket clienteSocket;

    private DataInputStream input;
    private DataOutputStream output;

    // Estado da sessão (acesso sincronizado via volatile ou locks)
    private String username; // null = não autenticado
    private boolean ativo;   // true = conexão ativa
    private boolean isAdmin ;

    // ThreadPool para processar pedidos concorrentemente
    private final ThreadPool workers;
    
    // Lock APENAS para output do socket (respostas concorrentes)
    private final Lock outputLock = new ReentrantLock();
    
    private static final String ADMIN_PASSWORD = "admin123";
    private static final int N_THREADS_WORKER = 4; // Reduzido para evitar explosão de threads
    
    public SessaoCliente(Socket clienteSocket, GestorUtilizadores gestorUtilizadores, GestorEventos gestorEventos) {
        
        this.clienteSocket = clienteSocket;
        this.gestorUtilizadores = gestorUtilizadores;
        this.gestorEventos = gestorEventos;
        this.username = null;
        this.isAdmin = false;
        this.ativo = true;
        
        // ThreadPool dedicada a este cliente
        // Alternativa: usar pool partilhada entre clientes (mais eficiente)
        this.workers = new ThreadPool(N_THREADS_WORKER, 50);
    }
    
    public boolean isAutenticado() {
        return username != null;
    }
    
    public String getUsername() {
        return username;
    }

    public void run() {
        String enderecoCliente = clienteSocket.getInetAddress().toString();
        
        try {
            input = new DataInputStream(new BufferedInputStream(clienteSocket.getInputStream()));
            output = new DataOutputStream(new BufferedOutputStream(clienteSocket.getOutputStream()));
            
            System.out.println("Nova conexão de: " + enderecoCliente);
            
            // Loop principal: lê pedidos e submete ao threadpool
            while (ativo) {
                try {
                    // Lê pedido do socket (operação bloqueante)
                    Mensagem pedido = Protocolo.lerMensagem(input);
                    
                    // Submete para processamento assíncrono
                    // NOTA: se pool estiver cheia, bloqueia aqui (backpressure)
                    boolean submetido = workers.submit(() -> processarPedidoAssinc(pedido));
                    if (!submetido) {
                        // Pool em shutdown
                        System.out.println("ThreadPool em shutdown, fechando conexão");
                        break;
                    }
                    
                } catch (EOFException e) {
                    System.out.println("Cliente desconectado: " + (username != null ? username : enderecoCliente));
                    break;
                    
                } catch (IOException e) {
                    if (ativo) {
                        System.err.println("Erro na comunicação com " + enderecoCliente + ": " + e.getMessage());
                    }
                    break;
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Thread interrompida");
                    break;
                }
            }     
        } catch (IOException e) {
            System.err.println("Erro ao inicializar ligação com " + enderecoCliente + ": " + e.getMessage());
        } finally {
            fecharConexao();
        }
    }
    
    /**
     * Processa pedido de forma assíncrona (corre em thread do pool).
     * Garante que resposta é enviada de forma thread-safe.
     */
    private void processarPedidoAssinc(Mensagem pedido) {
        try {
            Mensagem resposta = processarPedido(pedido);
            
            // Escrita da resposta é secção crítica
            outputLock.lock();
            try {
                Protocolo.escreverMensagem(resposta, output);
                output.flush();
            } finally {
                outputLock.unlock();
            }
            
        } catch (IOException e) {
            System.err.println("Erro ao processar/enviar pedido: " + e.getMessage());
            // Não fecha conexão aqui - thread principal que decide
        }
    }

    /**
     * Processa pedido e retorna resposta.
     * Executado em thread do pool (não é thread principal).
     */
    private Mensagem processarPedido(Mensagem pedido) {
        try {
            Mensagem.TipoOperacao tipo = pedido.getTipo();

            if (tipo == Mensagem.TipoOperacao.REGISTO)
                return processarRegisto(pedido);
        
            if (tipo == Mensagem.TipoOperacao.LOGIN)
                return processarLogin(pedido);

            if (tipo == Mensagem.TipoOperacao.LOGIN_ADMIN)
                return processarLoginAdmin(pedido);

            if (!isAutenticado())
                return Mensagem.criarRespostaErro("Operação requer autenticação");

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
                    return processarQuantidadeVendas(pedido);
                    
                case VOLUME_VENDAS:
                    return processarVolumeVendas(pedido);
                    
                case PRECO_MEDIO:
                    return processarPrecoMedio(pedido);
                    
                case PRECO_MAXIMO:
                    return processarPrecoMaximo(pedido);
                    
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

    private Mensagem processarRegistarEvento(Mensagem pedido) throws IOException {
        Evento evento = PayloadParser.lerEvento(pedido.getPayload());
        if (evento == null) {
            return Mensagem.criarRespostaErro("Payload de evento inválido");
        }

        gestorEventos.adicionarEvento(evento.getProdutoID(), evento.getQuantidade(), evento.getPreco() );
        return Mensagem.criarRespostaOk("Evento registado com sucesso");
    }
    
    private Mensagem processarNovoDia() throws IOException {
        gestorEventos.iniciarNovoDia();
        return Mensagem.criarRespostaOk("Novo dia iniciado com sucesso");
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
        if (payload == null || payload.length < Integer.BYTES * 2) {
            return Mensagem.criarRespostaErro("Payload inválido para quantidade de vendas");
        }

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

    /**
     * Fecha conexão e liberta recursos.
     * Chamado apenas uma vez, no finally do run().
     */
    private void fecharConexao() {
        ativo = false;
        
        // Shutdown do threadpool (aguarda tarefas pendentes)
        workers.shutdown();
        
        try {
            if (output != null) output.close();
            if (input != null) input.close();
            if (clienteSocket != null && !clienteSocket.isClosed()) {
                clienteSocket.close();
            }
            
            System.out.println("Conexão fechada: " + 
                (username != null ? username : clienteSocket.getInetAddress()));
                
        } catch (IOException e) {
            System.err.println("Erro ao fechar conexão: " + e.getMessage());
        }
    }
}