package src.servidor;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

import src.uteis.Mensagem;

/**
 * Thread que processa pedidos de um cliente específico
 */
public class WorkerCliente implements Runnable {
    
    private Socket clienteSocket;
    private GestorUtilizadores gestorUtilizadores;
    private DataInputStream input;
    private DataOutputStream output;
    private String usernameCliente; // username do cliente autenticado (null se não autenticado)
    private boolean ativo;
    private ExecutorService threadPool;
    
    public WorkerCliente(Socket clienteSocket, GestorUtilizadores gestorUtilizadores) {
        this.clienteSocket = clienteSocket;
        this.gestorUtilizadores = gestorUtilizadores;
        this.usernameCliente = null;
        this.ativo = true;
        //todo: inicializar threadPool se necessário
    }
    
    @Override
    public void run() {
        try {
            input = new DataInputStream(clienteSocket.getInputStream());
            output = new DataOutputStream(clienteSocket.getOutputStream());
            System.out.println("Nova conexão de: " + clienteSocket.getInetAddress());
            
            // Loop de processamento de mensagens
            while (ativo) {
                try {
                    Mensagem pedido = Mensagem.ler(input);
                    Mensagem resposta = processarPedido(pedido);
                    resposta.escrever(output);
                    output.flush();
                } catch (EOFException e) {
                    // Cliente fechou conexão
                    System.out.println("Cliente desconectado: " + 
                        (usernameCliente != null ? usernameCliente : clienteSocket.getInetAddress()));
                    break;
                } catch (IOException e) {
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
     * Processa um pedido do cliente e retorna a resposta
     */
    //todo: terminar restantes tipos de operacao
    private Mensagem processarPedido(Mensagem pedido) {
        try {
            switch (pedido.getTipoOperacao()) {
                case REGISTO:
                    return processarRegisto(pedido);
                    
                case LOGIN:
                    return processarLogin(pedido);
                    
                case REG_EVENTO:
                    // TODO: Implementar mais tarde
                    return Mensagem.criarResposta(false, "Funcionalidade ainda não implementada");
                    
                case AGREGACAO_INFO:
                    // TODO: Implementar mais tarde
                    return Mensagem.criarResposta(false, "Funcionalidade ainda não implementada");
                    
                default:
                    return Mensagem.criarResposta(false, "Operação desconhecida");
            }
        } catch (IOException e) {
            try {
                return Mensagem.criarResposta(false, "Erro ao processar pedido: " + e.getMessage());
            } catch (IOException ex) {
                System.err.println("Erro crítico ao criar resposta de erro");
                return null;
            }
        }
    }
    
    /**
     * Processa pedido de registo de novo utilizador (sign up)
     */
    private Mensagem processarRegisto(Mensagem pedido) throws IOException {
        String[] credenciais = pedido.extrairDadosAutenticacao();
        String usernameCliente = credenciais[0];
        String password = credenciais[1];
        
        // Validar dados
        if (usernameCliente == null || usernameCliente.trim().isEmpty()) {
            return Mensagem.criarResposta(false, "username inválido");
        }
        if (password == null || password.length() < 4) {
            return Mensagem.criarResposta(false, "Password deve ter pelo menos 4 caracteres");
        }
        
        // Tenta proceder registo utilizador
        boolean sucesso = gestorUtilizadores.registar(usernameCliente, password);
        if (sucesso) {
            System.out.println("Novo utilizador registado: " + usernameCliente);
            return Mensagem.criarResposta(true, "Utilizador registado com sucesso");
        } else {
            return Mensagem.criarResposta(false, "usernameCliente já existe");
        }
    }
    
    /**
     * Processa pedido de autenticação (login)
     */
    private Mensagem processarLogin(Mensagem pedido) throws IOException {
        // Extrair credenciais
        String[] credenciais = pedido.extrairDadosAutenticacao();
        String usernameCliente = credenciais[0];
        String password = credenciais[1];
        
        // Tentar autenticar
        boolean sucesso = gestorUtilizadores.autenticar(usernameCliente, password);
        if (sucesso) {
            this.usernameCliente = usernameCliente; 
            System.out.println("Utilizador autenticado: " + usernameCliente);
            return Mensagem.criarResposta(true, "Autenticação bem-sucedida");
        } else {
            return Mensagem.criarResposta(false, "Credenciais inválidas");
        }
    }
    
    /**
     * Fecha a conexão com o cliente
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
        return usernameCliente != null;
    }
    
    /**
     * Obtém usernameCliente do cliente (null se não autenticado)
     */
    public String getusernameCliente() {
        return usernameCliente;
    }
}