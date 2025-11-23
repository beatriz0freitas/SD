package src.cliente;

import java.io.IOException;
import java.util.Scanner;
import src.uteis.Mensagem;
import src.uteis.PayloadParser;

public class InterfaceUtilizador {
    private BibliotecaCliente bibliotecaCliente;
    private Scanner scanner;

    private boolean autenticado;
    private String nomeUtilizador;
    private boolean isAdmin;

    public InterfaceUtilizador(String host, int porta) {
        this.bibliotecaCliente = new BibliotecaCliente(host, porta);
        this.scanner = new Scanner(System.in);
        this.autenticado = false;
        this.nomeUtilizador = null;
        this.isAdmin = false;
    }

    public void iniciar() {
        try {
            bibliotecaCliente.conectar();
            correrLoopPrincpal();
        } catch (IOException e) {
            System.err.println("Erro ao conectar: " + e.getMessage());
        } finally {
            bibliotecaCliente.desconectar();
            scanner.close();
        }
    }

    private void correrLoopPrincpal() {
        mostrarHeader();
        while (!autenticado) {
            mostrarMenuAutenticacao();
            int opt = lerOpcao();
            processarMenuAutenticacao(opt);
        }

        while (autenticado) {
            mostrarMenuPrincipal();
            int opt = lerOpcao();
            processarMenuPrincipal(opt);
        }
    }

    private void mostrarHeader() {
        System.out.println("========================================");
        System.out.println("  SERVIÇO DE GESTÃO DE VENDAS - cliente ");
        System.out.println("========================================");
    }

    // ============ MENUS =============

    private void mostrarMenuAutenticacao() {
        limparEcrã();
        System.out.println("\n------ MENU DE AUTENTICAÇÃO ------");
        System.out.println("1. Registar novo utilizador (SignUp)");
        System.out.println("2. Autenticar (Login)");
        System.out.println("3. Login Administrador"); 
        System.out.println("0. Sair");
        System.out.print("Escolha uma opção: ");
    }

    private void mostrarMenuPrincipal() {
        limparEcrã();
        System.out.println("\n=== MENU PRINCIPAL [" + nomeUtilizador + "] ===");
        
        if (isAdmin) {
            System.out.println("1. Listar clientes registados");
            System.out.println("2. Listar eventos");
            System.out.println("3. Avançar dia");
        } else {
            System.out.println("1. Registar evento de venda");
            System.out.println("2. Quantidade de Vendas");
        }
        
        System.out.println("0. Logout e Sair");
        System.out.print("Escolha uma opção: ");
    }



    // ============ PROCESSAR MENUS =============

    private void processarMenuAutenticacao(int opcao) {
        switch (opcao) {
            case 1:
                registarUtilizador();
                break;
            case 2:
                autenticarUtilizador();
                break;
            case 3: 
                autenticarAdmin();
                break;
            case 0:
                sairPrograma();
                break;
            default:
                System.out.println("Opção inválida!");
        }
    }
    
    private void processarMenuPrincipal(int opcao) {
        if (isAdmin) {
            switch (opcao) {
                case 1:
                    listarClientes();
                    break;
                case 2:
                    listarEventos();
                    break;
                case 3:
                    novoDia();
                    break;
                case 0:
                    logout();
                    break;
                default:
                    System.out.println("Opção inválida!");
            }
        } else {
            switch (opcao) {
                case 1:
                    registarEvento();
                    break;
                case 2:
                    quantidadeVendas();
                case 0:
                    logout();
                    break;
                default:
                    System.out.println("Opção inválida!");
            }
        }
    }


    // ============ AÇÕES MENUS =============

    private void registarUtilizador() {
        System.out.print("\nNome de utilizador: ");
        String nome = scanner.nextLine();
        
        System.out.print("Palavra-passe: ");
        String password = scanner.nextLine();
    
        try {
            Mensagem resposta = bibliotecaCliente.registar(nome, password);
            mostrarResposta(resposta);
        } catch (IOException e) {
            System.err.println("✗ Erro de comunicação: " + e.getMessage());
        } finally {
            esperaEnter();
        }
    }
    

    private void autenticarUtilizador() {
        System.out.print("\nNome de utilizador: ");
        String nome = scanner.nextLine();
        
        System.out.print("Palavra-passe: ");
        String password = scanner.nextLine();
    
        try {
            Mensagem resposta = bibliotecaCliente.autenticar(nome, password);
            
            if (resposta.isSuccesso()) {
                this.autenticado = true;
                this.nomeUtilizador = nome;
                System.out.println("Bem-vindo, " + nome + "!");
            }
            mostrarResposta(resposta);

        } catch (IOException e) {
            System.err.println("✗ Erro de comunicação: " + e.getMessage());
        } finally {
            esperaEnter();
        }
    }

    private void autenticarAdmin() {
        System.out.print("\nSenha de administrador: ");
        String password = scanner.nextLine();
    
        try {
            Mensagem resposta = bibliotecaCliente.loginAdmin(password);
            
            if (resposta.isSuccesso()) {
                this.autenticado = true;
                this.isAdmin = true;
                this.nomeUtilizador = "ADMIN";
                System.out.println("Acesso de administrador concedido!");
            }
            mostrarResposta(resposta);

        } catch (IOException e) {
            System.err.println("✗ Erro de comunicação: " + e.getMessage());
        } finally {
            esperaEnter();
        }
    }

    private void sairPrograma() {
        System.out.println("\nA sair do programa...");
        bibliotecaCliente.desconectar();
        System.exit(0);
    }

    private void registarEvento() {
        System.out.print("ID do produto: ");
        int produtoID = lerInteiro();
        
        System.out.print("Quantidade: ");
        int quantidade = lerInteiro();
        
        System.out.print("Preço unitário: ");
        double preco = lerDouble();
    
        try {
            Mensagem resposta = bibliotecaCliente.registarEvento(produtoID, quantidade, preco);
            mostrarResposta(resposta);
        } catch (IOException e) {
            System.err.println("✗ Erro de comunicação: " + e.getMessage());
        } finally {
            esperaEnter();
        }
    }
    
    private void listarClientes() {
        try {
            Mensagem resposta = bibliotecaCliente.listarClientes();
            mostrarResposta(resposta);
        } catch (IOException e) {
            System.err.println("✗ Erro de comunicação: " + e.getMessage());
        } finally {
            esperaEnter();
        }
    }
    
    private void listarEventos() {
        try {
            Mensagem resposta = bibliotecaCliente.listarEventos();
            mostrarResposta(resposta);
        } catch (IOException e) {
            System.err.println("✗ Erro de comunicação: " + e.getMessage());
        } finally {
            esperaEnter();
        }
    }
    
    // TODO: Remover confirmação do novoDia() (admin não precisa confirmar) - nao sei ao certo o que querem dizer
    private void novoDia() {        
        try {
            Mensagem resposta = bibliotecaCliente.novoDia();
            mostrarResposta(resposta);
        } catch (IOException e) {
            System.err.println("✗ Erro de comunicação: " + e.getMessage());
        } finally {
            esperaEnter();
        }
    }

    private void quantidadeVendas() {
        System.out.print("ID do produto: ");
        int produtoID = lerInteiro();

        System.out.print("Últimos N dias: ");
        int dias = lerInteiro();

        try {
            Mensagem resposta = bibliotecaCliente.quantidadeVendas(produtoID, dias);
            mostrarResposta(resposta);
        } catch (IOException e) {
            System.err.println("✗ Erro de comunicação: " + e.getMessage());
        } finally {
            esperaEnter();
        }
    }

    private void logout() {
        System.out.println("\nA terminar sessão...");
        autenticado = false;
        isAdmin = false;
        bibliotecaCliente.desconectar();
        System.out.println("\nSessão terminada...");
        esperaEnter();
        System.exit(0);
    }


    // =========== UTEIS =============

    private void esperaEnter() {
        System.out.println("\nPressione ENTER para continuar...");
        scanner.nextLine();
    }

    private void limparEcrã() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }
    
    private int lerOpcao() {
        try {
            return Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private int lerInteiro() {
        try {
            return Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            System.out.println("Valor inválido, usando 0");
            return 0;
        }
    }

    private double lerDouble() {
        while (true) {
            try {
                return Double.parseDouble(scanner.nextLine());
            } catch (NumberFormatException e) {
                System.out.println("Valor inválido. Tente novamente.");
            }
        }
    }
    
    /**
     * Helper para mostrar resposta do servidor com formatação
     */
    private void mostrarResposta(Mensagem resposta) {
        try {
            String mensagem = PayloadParser.lerResposta(resposta.getPayload());
            String icone = resposta.isSuccesso() ? "✓" : "✗";
            System.out.println("\n" + icone + " " + mensagem);
        } catch (IOException e) {
            System.out.println("Erro ao processar a resposta do servidor: " + e.getMessage());
        }
    }

}