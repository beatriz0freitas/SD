package src.servidor;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import src.uteis.Evento;
import src.uteis.Mensagem;
import src.uteis.Protocolo;

/**
 * Thread que processa pedidos de um cliente específico
 * 
 * CONCORRÊNCIA:
 * - Thread principal: lê pedidos do socket (loop while)
 * - ThreadPool: processa cada pedido em thread separada
 * - Lock de escrita: garante que respostas não se misturam
 */
public class WorkerCliente implements Runnable {
    
    private Socket clienteSocket;
    private GestorUtilizadores gestorUtilizadores;
    private DataInputStream input;
    private DataOutputStream output;
    private String username; // null = não autenticado
    private boolean ativo; // true = conexão ativa

    // Concorrência
    private ExecutorService threadPool; 
    private final ReentrantLock outputLock;

    private GestorEventos gestorEventos; 
    
    public WorkerCliente(Socket clienteSocket, GestorUtilizadores gestorUtilizadores, GestorEventos gestorEventos) {
        this.clienteSocket = clienteSocket;
        this.gestorUtilizadores = gestorUtilizadores;
        this.username = null;
        this.ativo = true;
        this.threadPool = Executors.newCachedThreadPool(); // CachedThreadPool: cria threads sob demanda, reutiliza quando disponíveis
        this.outputLock = new ReentrantLock();
        this.gestorEventos = gestorEventos;
    }
    
    @Override
    public void run() {
        try {
            // Inicializar streams de entrada/saída
            input = new DataInputStream(clienteSocket.getInputStream());
            output = new DataOutputStream(clienteSocket.getOutputStream());
            
            System.out.println("Nova conexão de: " + clienteSocket.getInetAddress());
            
            // Loop de processamento de mensagens
            while (ativo) {
                try {
                    // Receber mensagem do cliente (bloqueante)
                    Mensagem pedido = Mensagem.ler(input);
                    
                    // Processar pedido em thread separada (para permitir varios pedidos concorrentes)
                    threadPool.execute(() -> processarPedidoAsync(pedido));
                    
                } catch (EOFException e) {
                    // Cliente fechou conexão
                    System.out.println("Cliente desconectado: " + 
                        (username != null ? username : clienteSocket.getInetAddress()));
                    break;
                } catch (IOException e) {
                    // Erro na comunicação (rede caiu, socket fechado, etc)
                    System.err.println("Erro na comunicação: " + e.getMessage());
                    break;
                }
            }
            
        } catch (IOException e) {
            System.err.println("Erro ao inicializar conexão: " + e.getMessage());
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
    private void processarPedidoAsync(Mensagem pedido) {
        try {
            // Processar o pedido (pode demorar tempo)
            Mensagem resposta = processarPedido(pedido);
            
            // Enviar resposta (com exclusão mútua)
            // SECÇÃO CRITICA: múltiplas threads podem tentar escrever ao mesmo tempo
            outputLock.lock();
            try {
                resposta.escrever(output);
                output.flush(); // Garantir que dados são enviados imediatamente
            } finally {
                outputLock.unlock();
            }
            
        } catch (IOException e) {
            System.err.println("[ERRO] Erro ao enviar resposta: " + e.getMessage());
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
    
    
   /**
     * Processa pedido de registo de novo utilizador.
     * 
     * VALIDAÇÕES:
     * - Username não vazio
     * - Password com mínimo 4 caracteres
     * - Username não existe já
     * 
     * @param pedido Mensagem REGISTO com username e password
     * @return RESPOSTA_OK ou RESPOSTA_ERRO
     */
    private Mensagem processarRegisto(Mensagem pedido) throws IOException {
        // Extrair credenciais
        String[] credenciais = pedido.extrairDadosAutenticacao();
        String username = credenciais[0];
        String password = credenciais[1];
        
        // Validar dados
        if (username == null || username.trim().isEmpty()) {
            return Mensagem.criarRespostaErro("Username inválido");
        }
        if (password == null || password.length() < 4) {
            return Mensagem.criarRespostaErro("Password deve ter pelo menos 4 caracteres");
        }
        
        // Tentar registar
        boolean sucesso = gestorUtilizadores.registar(username, password);
        
        if (sucesso) {
            System.out.println("Novo utilizador registado: " + username);
            return Mensagem.criarRespostaOk("Utilizador registado com sucesso");
        } else {
            return Mensagem.criarRespostaErro("Username já existe");
        }
    }
    
    /**
     * Processa pedido de autenticação (login)
     */
    private Mensagem processarLogin(Mensagem pedido) throws IOException {
        // Extrair credenciais
        String[] credenciais = pedido.extrairDadosAutenticacao();
        String username = credenciais[0];
        String password = credenciais[1];
        
        // Tentar autenticar
        boolean sucesso = gestorUtilizadores.autenticar(username, password);
        
        if (sucesso) {
            this.username = username; // Marcar como autenticado
            System.out.println("Utilizador autenticado: " + username);
            return Mensagem.criarRespostaOk("Autenticação bem-sucedida");
        } else {
            return Mensagem.criarRespostaErro("Credenciais inválidas");
        }
    }
    
    private Mensagem processarRegistarEvento(Mensagem pedido) throws IOException {
        // Extrair o evento diretamente do payload
        Evento evento = Protocolo.deserializarPayloadEvento(pedido.getPayload());

        gestorEventos.adicionarEvento(
            evento.getProdutoID(),
            evento.getQuantidade(),
            evento.getPreco()
        );

        return Mensagem.criarRespostaOk("Evento registado com sucesso");
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
            if (output != null) output.close();
            if (input != null) input.close();
            if (clienteSocket != null) clienteSocket.close();
        } catch (IOException e) {
            System.err.println("Erro ao fechar conexão: " + e.getMessage());
        }
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
}

//=================================TIRAR DEPOIS================================//

/**
 * ============================================================================
 * FLUXO DE EXECUÇÃO:
 * ============================================================================
 * 
 * 1. Servidor aceita conexão → cria WorkerCliente
 * 2. WorkerCliente.run() inicia
 * 3. Loop principal lê mensagens do socket
 * 4. Cada mensagem é processada em thread separada (threadPool)
 * 5. Resposta é enviada com lock (evita mistura de respostas)
 * 6. Cliente fecha conexão → loop termina → recursos libertados
 * 
 * ============================================================================
 * EXEMPLO DE CONCORRÊNCIA:
 * ============================================================================
 * 
 * Cliente envia 3 pedidos rápidos:
 *   Thread Main: recebe pedido1 → submete ao pool
 *   Thread Main: recebe pedido2 → submete ao pool
 *   Thread Main: recebe pedido3 → submete ao pool
 * 
 * ThreadPool processa concorrentemente:
 *   Thread-1: processa pedido1 (pode demorar 5s)
 *   Thread-2: processa pedido2 (pode demorar 1s) ← termina primeiro!
 *   Thread-3: processa pedido3 (pode demorar 2s)
 * 
 * Respostas são enviadas com lock:
 *   Thread-2: lock → envia resposta2 → unlock
 *   Thread-3: lock → envia resposta3 → unlock
 *   Thread-1: lock → envia resposta1 → unlock
 * 
 * ⚠️ IMPORTANTE: Respostas podem chegar fora de ordem!
 *    Solução futura: adicionar requestId para correlação
 * ============================================================================
 */
