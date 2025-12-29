package client.ui;

import client.ClienteMiddleware;
import client.stub.StubFactory;
import common.dto.*;
import common.interfaces.*;
import java.util.Scanner;

/**
 * Interface de utilizador do cliente - Versão refatorada
 */
public class InterfaceUtilizador {
    // Constantes
    private static final String SIMBOLO_SUCESSO = "✓";
    private static final String SIMBOLO_ERRO = "✗";
    private static final String CLEAR_SCREEN = "\033[H\033[2J";
    
    private final ClienteMiddleware middleware;
    private final StubFactory stubFactory;
    private final Scanner scanner;
    
    // Stubs
    private final IServicoAutenticacao servicoAuth;
    private final IServicoEventos servicoEventos;
    private final IServicoAgregacoes servicoAgregacoes;
    private final IServicoAdmin servicoAdmin;
    
    // Estado
    private boolean autenticado;
    private String username;
    private boolean isAdmin;
    private volatile boolean executando;
    private final boolean clearScreenEnabled = true;
    
    public InterfaceUtilizador(ClienteMiddleware middleware, StubFactory stubFactory) {
        this.middleware = middleware;
        this.stubFactory = stubFactory;
        this.scanner = new Scanner(System.in);
        
        // Criar stubs
        this.servicoAuth = stubFactory.criarStubAutenticacao();
        this.servicoEventos = stubFactory.criarStubEventos();
        this.servicoAgregacoes = stubFactory.criarStubAgregacoes();
        this.servicoAdmin = stubFactory.criarStubAdmin();
        
        this.autenticado = false;
        this.isAdmin = false;
        this.executando = true;
    }
    
    public void iniciar() {
        try {
            mostrarHeader();
            
            while (executando) {
                if (!autenticado) {
                    mostrarMenuAutenticacao();
                    processarMenuAutenticacao();
                } else {
                    mostrarMenuPrincipal();
                    processarMenuPrincipal();
                }
            }
            
        } catch (Exception e) {
            if (executando) {
                System.err.println("\nErro fatal na interface: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            encerrar();
        }
    }
    
    public void encerrar() {
        executando = false;
        try {
            if (scanner != null) {
                scanner.close();
            }
        } catch (Exception e) {
            // Ignorar
        }
        System.out.println("\nInterface encerrada.");
    }
    
    private void mostrarHeader() {
        System.out.println("========================================");
        System.out.println("  SERVIÇO DE GESTÃO DE VENDAS - cliente");
        System.out.println("========================================");
    }
    
    private void mostrarMenuAutenticacao() {
        limparEcra();
        System.out.println("\n------ MENU DE AUTENTICAÇÃO ------");
        System.out.println("1. Registar novo utilizador");
        System.out.println("2. Autenticar (Login)");
        System.out.println("3. Login Administrador");
        System.out.println("0. Sair");
        System.out.print("Escolha uma opção: ");
    }
    
    private void mostrarMenuPrincipal() {
        limparEcra();
        System.out.println("\n=== MENU PRINCIPAL [" + username + "] ===");
        
        if (isAdmin) {
            System.out.println("1. Listar clientes registados");
            System.out.println("2. Listar eventos do dia");
            System.out.println("3. Avançar dia");
            System.out.println("4. Logout");
        } else {
            System.out.println("1. Registar evento de venda");
            System.out.println("2. Quantidade de Vendas");
            System.out.println("3. Volume de Vendas");
            System.out.println("4. Preço Médio");
            System.out.println("5. Preço Máximo");
            System.out.println("6. Notificar Venda Específica");
            System.out.println("7. Notificar Venda Consecutiva (TODO)");
            System.out.println("8. Logout");
        }
        
        System.out.println("0. Sair");
        System.out.print("Escolha uma opção: ");
    }
    
    private void processarMenuAutenticacao() {
        int opcao = lerOpcao();
        
        try {
            switch (opcao) {
                case 1: registar(); break;
                case 2: login(); break;
                case 3: loginAdmin(); break;
                case 0: sair(); break;
                default: System.out.println("Opção inválida!");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("\nOperação cancelada.");
            executando = false;
        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
        }
        
        if (executando) esperarEnter();
    }
    
    private void processarMenuPrincipal() {
        int opcao = lerOpcao();
        
        try {
            if (isAdmin) {
                processarMenuAdmin(opcao);
            } else {
                processarMenuCliente(opcao);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("\nOperação cancelada.");
            executando = false;
        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
        }
        
        if (executando) esperarEnter();
    }
    
    private void processarMenuAdmin(int opcao) throws Exception {
        switch (opcao) {
            case 1: listarClientes(); break;
            case 2: listarEventos(); break;
            case 3: novoDia(); break;
            case 4: logout(); break;
            case 0: sair(); break;
            default: System.out.println("Opção inválida!");
        }
    }
    
    private void processarMenuCliente(int opcao) throws Exception {
        switch (opcao) {
            case 1: registarEvento(); break;
            case 2: quantidadeVendas(); break;
            case 3: volumeVendas(); break;
            case 4: precoMedio(); break;
            case 5: precoMaximo(); break;
            case 6: notificarVendaEspecifica(); break;
            //case 7: notificarVendaConsecutiva(); break;
            case 8: logout(); break;
            case 0: sair(); break;
            default: System.out.println("Opção inválida!");
        }
    }
    
    // === AUTENTICAÇÃO ===
    
    private void registar() throws Exception {
        System.out.print("\nUsername: ");
        String user = lerString();
        System.out.print("Password: ");
        String pass = lerString();
        
        UsuarioDTO dto = new UsuarioDTO(user, pass);
        RespostaDTO resposta = servicoAuth.registrar(dto);
        mostrarResposta(resposta);
    }
    
    private void login() throws Exception {
        System.out.print("\nUsername: ");
        String user = lerString();
        System.out.print("Password: ");
        String pass = lerString();
        
        UsuarioDTO dto = new UsuarioDTO(user, pass);
        RespostaDTO resposta = servicoAuth.autenticar(dto);
        
        if (resposta.isSucesso()) {
            autenticado = true;
            username = user;
        }
        
        mostrarResposta(resposta);
    }
    
    private void loginAdmin() throws Exception {
        System.out.print("\nSenha de administrador: ");
        String pass = lerString();
        
        RespostaDTO resposta = servicoAuth.autenticarAdmin(pass);
        
        if (resposta.isSucesso()) {
            autenticado = true;
            isAdmin = true;
            username = "ADMIN";
        }
        
        mostrarResposta(resposta);
    }
    
    // === AÇÕES CLIENTE ===
    
    private void registarEvento() throws Exception {
        System.out.print("ID do produto: ");
        Integer produtoID = lerInteiroOpt();
        if (produtoID == null) { 
            System.out.println("Operação cancelada."); 
            return; 
        }
        
        System.out.print("Quantidade: ");
        Integer quantidade = lerInteiroOpt();
        if (quantidade == null) { 
            System.out.println("Operação cancelada."); 
            return; 
        }
        
        System.out.print("Preço unitário: ");
        Double preco = lerDoubleOpt();
        if (preco == null) { 
            System.out.println("Operação cancelada."); 
            return; 
        }
        
        EventoDTO dto = new EventoDTO(produtoID, quantidade, preco);
        RespostaDTO resposta = servicoEventos.registrarEvento(dto);
        mostrarResposta(resposta);
    }

    private void notificarVendaEspecifica() throws Exception {
        System.out.print("ID do produto 1: ");
        Integer produtoID1 = lerInteiroOpt();
        if (produtoID1 == null) { 
            System.out.println("Operação cancelada."); 
            return; 
        }

        System.out.print("ID do produto 2: ");
        Integer produtoID2 = lerInteiroOpt();
        if (produtoID2 == null) { 
            System.out.println("Operação cancelada."); 
            return; 
        }

        RespostaDTO resposta = servicoEventos.notificarVendaEspecifica(new NotificacaoDTO(produtoID1, produtoID2));
        mostrarResposta(resposta);
    }
    
    private void quantidadeVendas() throws Exception {
        consultarAgregacao("quantidade");
    }
    
    private void volumeVendas() throws Exception {
        consultarAgregacao("volume");
    }
    
    private void precoMedio() throws Exception {
        consultarAgregacao("medio");
    }
    
    private void precoMaximo() throws Exception {
        consultarAgregacao("maximo");
    }
    
    /**
     * Método genérico para consultas de agregação
     */
    private void consultarAgregacao(String tipo) throws Exception {
        System.out.print("ID do produto: ");
        Integer produtoID = lerInteiroOpt();
        if (produtoID == null) { 
            System.out.println("Operação cancelada."); 
            return; 
        }
        
        System.out.print("Últimos N dias: ");
        Integer dias = lerInteiroOpt();
        if (dias == null) { 
            System.out.println("Operação cancelada."); 
            return; 
        }
        
        RespostaDTO resposta;
        switch (tipo) {
            case "quantidade":
                resposta = servicoAgregacoes.obterQuantidadeVendas(produtoID, dias);
                break;
            case "volume":
                resposta = servicoAgregacoes.obterVolumeVendas(produtoID, dias);
                break;
            case "medio":
                resposta = servicoAgregacoes.obterPrecoMedio(produtoID, dias);
                break;
            case "maximo":
                resposta = servicoAgregacoes.obterPrecoMaximo(produtoID, dias);
                break;
            default:
                throw new IllegalArgumentException("Tipo de agregação inválido");
        }
        
        mostrarResposta(resposta);
    }
    
    // === AÇÕES ADMIN ===
    
    private void listarClientes() throws Exception {
        RespostaDTO resposta = servicoAdmin.listarClientes();
        mostrarResposta(resposta);
    }
    
    private void listarEventos() throws Exception {
        RespostaDTO resposta = servicoEventos.listarEventosDiaAtual();
        mostrarResposta(resposta);
    }
    
    private void novoDia() throws Exception {
        RespostaDTO resposta = servicoEventos.novoDia();
        mostrarResposta(resposta);
    }
    
    // === CONTROLE ===
    
    private void logout() {
        autenticado = false;
        isAdmin = false;
        username = null;
        System.out.println("\n" + SIMBOLO_SUCESSO + " Logout efetuado.");
    }
    
    private void sair() {
        System.out.println("\nA encerrar aplicação...");
        executando = false;
    }
    
    // === UTILITÁRIOS ===
    
    private void mostrarResposta(RespostaDTO resposta) {
        String icone = resposta.isSucesso() ? SIMBOLO_SUCESSO : SIMBOLO_ERRO;
        System.out.println("\n" + icone + " " + resposta.getMensagem());
    }
    
    private void limparEcra() {
        if (!clearScreenEnabled) return;
        System.out.print(CLEAR_SCREEN);
        System.out.flush();
    }
    
    private void esperarEnter() {
        if (!executando) return;
        
        try {
            System.out.println("\nPressione ENTER para continuar...");
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            scanner.nextLine();
        } catch (Exception e) {
            if (executando) {
                System.err.println("Erro ao ler input: " + e.getMessage());
            }
        }
    }
    
    private int lerOpcao() {
        return lerInteiro();
    }
    
    private String lerString() {
        try {
            if (!executando || Thread.currentThread().isInterrupted()) {
                return "";
            }
            if (scanner.hasNextLine()) {
                return scanner.nextLine().trim();
            }
            return "";
        } catch (Exception e) {
            System.err.println("Erro ao ler entrada: " + e.getMessage());
            return "";
        }
    }
    
    private int lerInteiro() {
        while (executando && !Thread.currentThread().isInterrupted()) {
            try {
                String input = lerString();
                if (input.isEmpty()) return 0;
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.print("Valor inválido! Digite um número inteiro: ");
            }
        }
        return 0;
    }
    
    private Integer lerInteiroOpt() {
        while (executando && !Thread.currentThread().isInterrupted()) {
            try {
                String input = lerString();
                if (input.isEmpty()) return null;
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.print("Valor inválido! Digite um número inteiro: ");
            }
        }
        return null;
    }
    
    private Double lerDoubleOpt() {
        while (executando && !Thread.currentThread().isInterrupted()) {
            try {
                String input = lerString();
                if (input.isEmpty()) return null;
                return Double.parseDouble(input);
            } catch (NumberFormatException e) {
                System.out.print("Valor inválido! Digite um número: ");
            }
        }
        return null;
    }
}