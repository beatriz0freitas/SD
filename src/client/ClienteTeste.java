package client;

import client.stub.*;
import common.dto.*;
import common.interfaces.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Classe de teste que simula múltiplos clientes concorrentes
 * Usa a arquitetura real: Middleware + Stubs + Interfaces
 */
public class ClienteTeste {
    private static final String HOST = "localhost";
    private static final int PORTA = 5001;
    private static final Random random = new Random();
    private static final AtomicInteger contadorEventos = new AtomicInteger(0);
    private static final AtomicInteger contadorConsultas = new AtomicInteger(0);
    
    public static void main(String[] args) {
        int numClientes = 10;
        String host = HOST;
        int porta = PORTA;
        
        // Processar argumentos: [numClientes] [host] [porta]
        if (args.length > 0) {
            try {
                numClientes = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Número de clientes inválido, usando padrão: " + numClientes);
            }
        }
        if (args.length > 1) {
            host = args[1];
        }
        if (args.length > 2) {
            try {
                porta = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                System.err.println("Porta inválida, usando padrão: " + porta);
            }
        }
        
        System.out.println("========================================");
        System.out.println("  TESTE DE CLIENTES CONCORRENTES");
        System.out.println("========================================");
        System.out.println("Número de clientes: " + numClientes);
        System.out.println("Servidor: " + host + ":" + porta);
        System.out.println("========================================\n");
        
        ExecutorService executor = Executors.newFixedThreadPool(numClientes);
        List<ClienteSimulado> clientes = new ArrayList<>();
        
        // Criar e executar clientes
        for (int i = 1; i <= numClientes; i++) {
            ClienteSimulado cliente = new ClienteSimulado(i, host, porta);
            clientes.add(cliente);
            executor.execute(cliente);
        }
        
        // Aguardar conclusão
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
                System.err.println("Timeout - forçando encerramento...");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Estatísticas finais
        System.out.println("\n========================================");
        System.out.println("  ESTATÍSTICAS FINAIS");
        System.out.println("========================================");
        
        int sucessos = 0;
        int falhas = 0;
        int totalOperacoes = 0;
        long tempoTotal = 0;
        
        for (ClienteSimulado cliente : clientes) {
            if (cliente.isSucesso()) {
                sucessos++;
            } else {
                falhas++;
            }
            totalOperacoes += cliente.getNumOperacoes();
            tempoTotal += cliente.getTempoExecucao();
        }
        
        System.out.println("Clientes bem-sucedidos: " + sucessos);
        System.out.println("Clientes com falhas: " + falhas);
        System.out.println("Total de operações: " + totalOperacoes);
        System.out.println("  - Eventos registados: " + contadorEventos.get());
        System.out.println("  - Consultas realizadas: " + contadorConsultas.get());
        if (sucessos > 0) {
            System.out.printf("Tempo médio por cliente: %.2f ms%n", (double) tempoTotal / sucessos);
        }
        System.out.println("========================================");
    }
    
    /**
     * Classe interna que simula um cliente individual
     * Usa a arquitetura real do sistema
     */
    static class ClienteSimulado implements Runnable {
        private final int id;
        private final String host;
        private final int porta;
        private boolean sucesso = false;
        private int numOperacoes = 0;
        private long tempoExecucao = 0;
        
        // Componentes do cliente real
        private ClienteMiddleware middleware;
        private StubFactory stubFactory;
        private IServicoAutenticacao servicoAuth;
        private IServicoEventos servicoEventos;
        private IServicoAgregacoes servicoAgregacoes;
        
        public ClienteSimulado(int id, String host, int porta) {
            this.id = id;
            this.host = host;
            this.porta = porta;
        }
        
        @Override
        public void run() {
            long inicio = System.currentTimeMillis();
            System.out.println("[Cliente " + id + "] Iniciando...");
            
            try {
                // 1. Criar middleware e conectar
                middleware = new ClienteMiddleware(host, porta);
                middleware.conectar();
                
                // 2. Criar factory de stubs
                stubFactory = new StubFactory(middleware);
                servicoAuth = stubFactory.criarStubAutenticacao();
                servicoEventos = stubFactory.criarStubEventos();
                servicoAgregacoes = stubFactory.criarStubAgregacoes();
                
                // 3. Autenticar
                if (!autenticar()) {
                    System.err.println("[Cliente " + id + "] Falha na autenticação");
                    return;
                }
                
                // 4. Simular diferentes tipos de clientes
                int tipo = id % 3;
                
                switch (tipo) {
                    case 0:
                        simularClienteVendedor();
                        break;
                    case 1:
                        simularClienteConsultor();
                        break;
                    case 2:
                        simularClienteMisto();
                        break;
                }
                
                sucesso = true;
                tempoExecucao = System.currentTimeMillis() - inicio;
                System.out.println("[Cliente " + id + "] Concluído com sucesso (" + 
                    numOperacoes + " operações em " + tempoExecucao + " ms)");
                
            } catch (Exception e) {
                System.err.println("[Cliente " + id + "] ERRO: " + e);
                e.printStackTrace();
                sucesso = false;
            } finally {
                // Sempre desconectar
                if (middleware != null) {
                    middleware.desconectar();
                }
            }
        }
        
        /**
         * Autentica o cliente
         */
        private boolean autenticar() {
            try {
                String username = "user" + id;
                String password = "pass" + id;
                
                // Tentar registar primeiro
                UsuarioDTO usuario = new UsuarioDTO(username, password);
                
                try {
                    RespostaDTO respostaReg = servicoAuth.registrar(usuario);
                    if (respostaReg.isSucesso()) {
                        System.out.println("[Cliente " + id + "] Registado: " + username);
                    }
                } catch (Exception e) {
                    // Já existe, tudo bem
                }
                
                // Autenticar
                RespostaDTO respostaAuth = servicoAuth.autenticar(usuario);
                if (respostaAuth.isSucesso()) {
                    System.out.println("[Cliente " + id + "] Autenticado: " + username);
                    numOperacoes++;
                    return true;
                }
                
                return false;
                
            } catch (Exception e) {
                System.err.println("[Cliente " + id + "] Erro na autenticação: " + e.getMessage());
                return false;
            }
        }
        
        /**
         * Cliente que apenas registra eventos
         */
        private void simularClienteVendedor() throws Exception {
            System.out.println("[Cliente " + id + "] Modo: VENDEDOR");
            
            int numVendas = 5 + random.nextInt(6); // 5-10 vendas
            
            for (int i = 0; i < numVendas; i++) {
                int produtoID = random.nextInt(10) + 1;
                int quantidade = random.nextInt(50) + 1;
                double preco = 10.0 + random.nextDouble() * 90.0;
                
                EventoDTO evento = new EventoDTO(produtoID, quantidade, preco);
                RespostaDTO resposta = servicoEventos.registrarEvento(evento);
                
                if (resposta.isSucesso()) {
                    System.out.printf("[Cliente %d] ✓ Evento registado: Produto=%d, Qtd=%d, Preço=%.2f€%n",
                        id, produtoID, quantidade, preco);
                    contadorEventos.incrementAndGet();
                } else {
                    System.err.printf("[Cliente %d] ✗ Falha: %s%n", id, resposta.getMensagem());
                }
                
                numOperacoes++;
                
                // Simular tempo entre vendas
                try {
                    Thread.sleep(random.nextInt(500) + 100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        
        /**
         * Cliente que apenas consulta dados
         */
        private void simularClienteConsultor() throws Exception {
            System.out.println("[Cliente " + id + "] Modo: CONSULTOR");
            
            int numConsultas = 5 + random.nextInt(6); // 5-10 consultas
            
            for (int i = 0; i < numConsultas; i++) {
                int operacao = random.nextInt(5);
                RespostaDTO resposta = null;
                String descricao = "";
                
                try {
                    switch (operacao) {
                        case 0:
                            resposta = servicoEventos.listarEventosDiaAtual();
                            descricao = "Listar eventos do dia";
                            break;
                        case 1:
                            int prodID1 = random.nextInt(10) + 1;
                            int dias1 = random.nextInt(10) + 1;
                            resposta = servicoAgregacoes.obterQuantidadeVendas(prodID1, dias1);
                            descricao = String.format("Quantidade vendas (Produto %d, %d dias)", prodID1, dias1);
                            break;
                        case 2:
                            int prodID2 = random.nextInt(10) + 1;
                            int dias2 = random.nextInt(10) + 1;
                            resposta = servicoAgregacoes.obterVolumeVendas(prodID2, dias2);
                            descricao = String.format("Volume vendas (Produto %d, %d dias)", prodID2, dias2);
                            break;
                        case 3:
                            int prodID3 = random.nextInt(10) + 1;
                            int dias3 = random.nextInt(10) + 1;
                            resposta = servicoAgregacoes.obterPrecoMedio(prodID3, dias3);
                            descricao = String.format("Preço médio (Produto %d, %d dias)", prodID3, dias3);
                            break;
                        case 4:
                            int prodID4 = random.nextInt(10) + 1;
                            int dias4 = random.nextInt(10) + 1;
                            resposta = servicoAgregacoes.obterPrecoMaximo(prodID4, dias4);
                            descricao = String.format("Preço máximo (Produto %d, %d dias)", prodID4, dias4);
                            break;
                    }
                    
                    if (resposta != null && resposta.isSucesso()) {
                        System.out.println("[Cliente " + id + "] ✓ " + descricao);
                        contadorConsultas.incrementAndGet();
                    } else {
                        System.err.println("[Cliente " + id + "] ✗ Falha em " + descricao);
                    }
                    
                } catch (Exception e) {
                    System.err.println("[Cliente " + id + "] ✗ Erro em " + descricao + ": " + e.getMessage());
                }
                
                numOperacoes++;
                try {
                    Thread.sleep(random.nextInt(1000) + 300);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        
        /**
         * Cliente que faz um mix de operações
         */
        private void simularClienteMisto() throws Exception {
            System.out.println("[Cliente " + id + "] Modo: MISTO");
            
            // Registrar alguns eventos
            int numEventos = 2 + random.nextInt(4); // 2-5 eventos
            for (int i = 0; i < numEventos; i++) {
                int produtoID = random.nextInt(10) + 1;
                int quantidade = random.nextInt(30) + 1;
                double preco = 15.0 + random.nextDouble() * 85.0;
                
                EventoDTO evento = new EventoDTO(produtoID, quantidade, preco);
                RespostaDTO resposta = servicoEventos.registrarEvento(evento);
                
                if (resposta.isSucesso()) {
                    System.out.printf("[Cliente %d] ✓ Evento: Produto=%d%n", id, produtoID);
                    contadorEventos.incrementAndGet();
                }
                
                numOperacoes++;
                try {
                    Thread.sleep(random.nextInt(300) + 100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            
            // Fazer algumas consultas
            int numConsultas = 2 + random.nextInt(4); // 2-5 consultas
            for (int i = 0; i < numConsultas; i++) {
                int produtoID = random.nextInt(10) + 1;
                int dias = random.nextInt(5) + 1;
                
                try {
                    RespostaDTO resposta = servicoAgregacoes.obterQuantidadeVendas(produtoID, dias);
                    if (resposta.isSucesso()) {
                        System.out.printf("[Cliente %d] ✓ Consulta: Produto=%d (%d dias)%n", 
                            id, produtoID, dias);
                        contadorConsultas.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("[Cliente " + id + "] ✗ Erro na consulta");
                }
                
                numOperacoes++;
                try {
                    Thread.sleep(random.nextInt(500) + 200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        
        public boolean isSucesso() {
            return sucesso;
        }
        
        public int getNumOperacoes() {
            return numOperacoes;
        }
        
        public long getTempoExecucao() {
            return tempoExecucao;
        }
    }
}