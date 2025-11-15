package src.cliente;

import java.io.IOException;
import java.util.Scanner;

/**
* Interface de utilizador para o cliente da aplicação de gestão de vendas. 
* Interface de linha de comando para o utilizador interagir com o bibliotecaCliente (menu, comandos, input/output).
*/
public class InterfaceUtilizador {
    private BibliotecaCliente bibliotecaCliente;
    private Scanner scanner;
    private boolean autenticado;
    private String nomeUtilizador;

    public InterfaceUtilizador(String host, int porta) {
        this.bibliotecaCliente = new BibliotecaCliente(host, porta);
        this.scanner = new Scanner(System.in);
        this.autenticado = false;
    }

  

    private void limparEcrã() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }
    
    public void iniciar() {
        try {
            bibliotecaCliente.conectar();
            System.out.println("========================================");
            System.out.println("  SERVIÇO DE GESTÃO DE VENDAS - cliente ");
            System.out.println("========================================");
            System.out.println("Conectado ao servidor!\n");

            // Menu de autenticação
            while (!autenticado) {
                mostrarMenuAutenticacao();
                int opcao = lerOpcao();
                processarAutenticacao(opcao);
            }

            // Menu principal após autenticação
            while (autenticado) {
                mostrarMenuPrincipal();
                int opcao = lerOpcao();
                processarOpcaoPrincipal(opcao);
            }

        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
        } finally {
            bibliotecaCliente.desconectar();
            scanner.close();
        }
    }

    private void mostrarMenuAutenticacao() {
        limparEcrã();
        System.out.println("\n------ MENU DE AUTENTICAÇÃO ------");
        System.out.println("1. Registar novo utilizador (SignUp)");
        System.out.println("2. Autenticar (Login)");
        System.out.println("0. Sair");
        System.out.print("Escolha uma opção: ");
    }

    private void processarAutenticacao(int opcao) {
        switch (opcao) {
            case 1:
                registarUtilizador();
                break;
            case 2:
                autenticarUtilizador();
                break;
            case 0:
                System.out.println("A sair...");
                System.exit(0);
                break;
            default:
                System.out.println("Opção inválida!");
        }
    }


    private void registarUtilizador() {
        System.out.print("\nNome de utilizador: ");
        String nome = scanner.nextLine();
        System.out.print("Palavra-passe (mínimo 4 caracteres): ");
        String password = scanner.nextLine();

        try {
            boolean sucesso = bibliotecaCliente.registar(nome, password);

            if (sucesso) {
                System.out.println("✓ Utilizador registado com sucesso!");
            } else {
                System.out.println("✗ Erro ao registar utilizador (username já existe ou password inválida)");
            }

        } catch (IOException e) {
            System.err.println("✗ Erro de comunicação com o servidor: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("✗ Ocorreu um erro inesperado: " + e.getMessage());
        }
    }

    //fix: depois de autenticar está em loop nao passa para o menu prinicpal
    private void autenticarUtilizador() {
        System.out.print("\nNome de utilizador: ");
        String nome = scanner.nextLine();
        System.out.print("Palavra-passe: ");
        String password = scanner.nextLine();

        try {
            boolean sucesso = bibliotecaCliente.autenticar(nome, password);
            
            if (sucesso) {
                this.autenticado = true;
                this.nomeUtilizador = nome;
                System.out.println("Autenticação bem-sucedida! Bem-vindo, " + nome + "!");
            } else {
                System.out.println("Credenciais inválidas");
            }
        } catch (Exception e) {
            System.err.println("✗ Erro: " + e.getMessage());
        }
    }


    private void mostrarMenuPrincipal() {
        limparEcrã();
        System.out.println("\n=== MENU PRINCIPAL [" + nomeUtilizador + "] ===");
        System.out.println("1. Registar evento de venda");
        System.out.println("2. Consultar agregações");
        System.out.println("3. Filtrar eventos por produtos");
        System.out.println("4. Notificação: Vendas simultâneas");
        System.out.println("5. Notificação: Vendas consecutivas");
        System.out.println("6. Informações do servidor");
        System.out.println("0. Logout e Sair");
        System.out.print("Escolha uma opção: ");
    }


    private void processarOpcaoPrincipal(int opcao) {
        switch (opcao) {
            case 1:
                // registarEvento();
                break;
            case 2:
                // consultarAgregacoes();
                break;
            case 3:
                // filtrarEventos();
                break;
            case 4:
                // notificarVendasSimultaneas();
                break;
            case 5:
                // notificarVendasConsecutivas();
                break;
            case 6:
                // informacoesServidor();
                break;
            case 0:
                logout();
                break;
            default:
                System.out.println("Opção inválida!");
        }
    }

    // private void registarEvento() {
    //     System.out.println("\n--- Registar Evento de Venda ---");
    //     System.out.print("Nome do produto: ");
    //     String produto = scanner.nextLine();
    //     System.out.print("Quantidade: ");
    //     int quantidade = lerInteiro();
    //     System.out.print("Preço unitário: ");
    //     double preco = lerDouble();

    //     try {
    //         boolean sucesso = bibliotecacliente.registarEvento(produto, quantidade, preco);
    //         if (sucesso) {
    //             System.out.println("✓ Evento registado com sucesso!");
    //         } else {
    //             System.out.println("✗ Erro ao registar evento");
    //         }
    //     } catch (Exception e) {
    //         System.err.println("✗ Erro: " + e.getMessage());
    //     }
    // }

    // private void consultarAgregacoes() {
    //     System.out.println("\n--- Consultar Agregações ---");
    //     System.out.print("Dias anteriores (1 a D): ");
    //     int dias = lerInteiro();
    //     System.out.print("Nome do produto (ou vazio para todos): ");
    //     String produto = scanner.nextLine();

    //     System.out.println("\nTipo de agregação:");
    //     System.out.println("1. Quantidade total vendida");
    //     System.out.println("2. Volume total (quantidade × preço)");
    //     System.out.println("3. Preço médio de venda");
    //     System.out.println("4. Preço máximo de venda");
    //     System.out.print("Escolha: ");
    //     int tipo = lerInteiro();

    //     try {
    //         double resultado = bibliotecacliente.consultarAgregacao(dias, produto, tipo);
    //         String tipoStr = switch (tipo) {
    //             case 1 -> "Quantidade total";
    //             case 2 -> "Volume total";
    //             case 3 -> "Preço médio";
    //             case 4 -> "Preço máximo";
    //             default -> "Desconhecido";
    //         };
    //         System.out.printf("\n✓ %s: %.2f%n", tipoStr, resultado);
    //     } catch (Exception e) {
    //         System.err.println("✗ Erro: " + e.getMessage());
    //     }
    // }

    // private void filtrarEventos() {
    //     System.out.println("\n--- Filtrar Eventos ---");
    //     System.out.print("Dias anteriores (1 a D): ");
    //     int dias = lerInteiro();
    //     System.out.print("Produtos (separados por vírgula): ");
    //     String produtosStr = scanner.nextLine();
    //     String[] produtos = produtosStr.split(",");
        
    //     // Limpar espaços
    //     for (int i = 0; i < produtos.length; i++) {
    //         produtos[i] = produtos[i].trim();
    //     }

    //     try {
    //         String eventos = bibliotecacliente.filtrarEventos(dias, produtos);
    //         System.out.println("\n✓ Eventos encontrados:");
    //         System.out.println(eventos);
    //     } catch (Exception e) {
    //         System.err.println("✗ Erro: " + e.getMessage());
    //     }
    // }

    // private void notificarVendasSimultaneas() {
    //     System.out.println("\n--- Notificação: Vendas Simultâneas ---");
    //     System.out.print("Produto 1: ");
    //     String produto1 = scanner.nextLine();
    //     System.out.print("Produto 2: ");
    //     String produto2 = scanner.nextLine();

    //     System.out.println("\n⏳ Aguardando vendas simultâneas...");
    //     System.out.println("(Esta operação bloqueia até a condição ser satisfeita ou o dia terminar)");

    //     try {
    //         boolean ocorreu = bibliotecacliente.notificarVendasSimultaneas(produto1, produto2);
    //         if (ocorreu) {
    //             System.out.println("✓ Vendas simultâneas detectadas!");
    //         } else {
    //             System.out.println("✗ Dia terminou sem vendas simultâneas");
    //         }
    //     } catch (Exception e) {
    //         System.err.println("✗ Erro: " + e.getMessage());
    //     }
    // }

    // private void notificarVendasConsecutivas() {
    //     System.out.println("\n--- Notificação: Vendas Consecutivas ---");
    //     System.out.print("Número de vendas consecutivas (n): ");
    //     int n = lerInteiro();

    //     System.out.println("\n⏳ Aguardando " + n + " vendas consecutivas...");
    //     System.out.println("(Esta operação bloqueia até a condição ser satisfeita ou o dia terminar)");

    //     try {
    //         String produto = bibliotecacliente.notificarVendasConsecutivas(n);
    //         if (produto != null && !produto.isEmpty()) {
    //             System.out.println("✓ Produto com vendas consecutivas: " + produto);
    //         } else {
    //             System.out.println("✗ Dia terminou sem vendas consecutivas suficientes");
    //         }
    //     } catch (Exception e) {
    //         System.err.println("✗ Erro: " + e.getMessage());
    //     }
    // }

    // private void informacoesServidor() {
    //     System.out.println("\n--- Informações do Servidor ---");
    //     try {
    //         String info = bibliotecacliente.obterInformacoesServidor();
    //         System.out.println(info);
    //     } catch (Exception e) {
    //         System.err.println("✗ Erro: " + e.getMessage());
    //     }
    // }

    private void logout() {
        System.out.println("\nA terminar sessão...");
        autenticado = false;
        bibliotecaCliente.desconectar();
        System.exit(0);
    }

    private int lerOpcao() {
        try {
            return Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // private int lerInteiro() {
    //     try {
    //         return Integer.parseInt(scanner.nextLine());
    //     } catch (NumberFormatException e) {
    //         System.out.println("Valor inválido, usando 0");
    //         return 0;
    //     }
    // }

    // private double lerDouble() {
    //     try {
    //         return Double.parseDouble(scanner.nextLine());
    //     } catch (NumberFormatException e) {
    //         System.out.println("Valor inválido, usando 0.0");
    //         return 0.0;
    //     }
    // }

    
}
