package testes;

import client.stub.*;
import common.concurrency.*;
import common.dto.*;
import common.interfaces.*;

import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ClienteTeste {
    // Configuração do teste
    private static final int NUM_CLIENTES_DEFAULT = 10;
    private static final int THREADS_POR_CLIENTE = 5;
    private static final int OPERACOES_POR_THREAD = 5;
    private static final int TIMEOUT_MINUTOS = 10;
    
    // Configuração do servidor
    private static final String HOST_DEFAULT = "localhost";
    private static final int PORTA_DEFAULT = 5001;
    
    // Configuração de operações
    private static final int NUM_PRODUTOS = 10;
    private static final int MAX_QUANTIDADE = 30;
    private static final int MAX_DIAS = 7;
    private static final double PRECO_MIN = 10.0;
    private static final double PRECO_MAX = 100.0;
    private static final int DELAY_MAX_MS = 200;
    
    // Contadores
    private static final AtomicInteger eventosRegistados = new AtomicInteger(0);
    private static final AtomicInteger consultasRealizadas = new AtomicInteger(0);
    private static final AtomicInteger respostasRecebidas = new AtomicInteger(0);
    
    private static final Random random = new Random();

    public static void main(String[] args) {
        ConfigTeste config = parseArgs(args);
        
        mostrarHeader(config);
        
        ThreadPool pool = new ThreadPoolImpl(config.numClientes);


        for (int i = 1; i <= config.numClientes; i++) {
            final int clienteId = i;
            if(!pool.submit(() -> executarCliente(clienteId, config))) {
                System.err.println("Não foi possível submeter o cliente " + clienteId + ". Fila cheia...");
            }
        }

        pool.shutdown();
        aguardarTerminacao(pool);
        
        mostrarResultados();
    }
    
    private static ConfigTeste parseArgs(String[] args) {
        int numClientes = args.length > 0 ? Integer.parseInt(args[0]) : NUM_CLIENTES_DEFAULT;
        String host = args.length > 1 ? args[1] : HOST_DEFAULT;
        int porta = args.length > 2 ? Integer.parseInt(args[2]) : PORTA_DEFAULT;
        return new ConfigTeste(numClientes, host, porta);
    }
    
    private static void mostrarHeader(ConfigTeste config) {
        System.out.println("=== TESTE CONCORRENTE ===");
        System.out.println("Clientes: " + config.numClientes);
        System.out.println("Threads por cliente: " + THREADS_POR_CLIENTE);
        System.out.println("Operações por thread: " + OPERACOES_POR_THREAD);
        System.out.println("Servidor: " + config.host + ":" + config.porta);
        System.out.println("=========================\n");
    }

    private static void aguardarTerminacao(ThreadPool pool) {
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Timeout atingido! Forçando shutdown...");
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            System.err.println("Interrompido! Forçando shutdown...");
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    private static void mostrarResultados() {
        System.out.println("\n=== RESULTADO FINAL ===");
        System.out.println("Eventos registados: " + eventosRegistados.get());
        System.out.println("Consultas realizadas: " + consultasRealizadas.get());
        System.out.println("Total de respostas: " + respostasRecebidas.get());
    }

    private static void executarCliente(int clienteId, ConfigTeste config) {
        ClienteMiddleware middleware = null;
        try {
            log(clienteId, "Iniciando...");

            middleware = new ClienteMiddleware(config.host, config.porta);
            middleware.conectar();

            StubFactory stubs = new StubFactory(middleware);
            IServicoAutenticacao servicoAuth = stubs.criarStubAutenticacao();
            IServicoEventos servicoEventos = stubs.criarStubEventos();
            IServicoAgregacoes servicoAgregacoes = stubs.criarStubAgregacoes();

            if (!autenticarCliente(clienteId, servicoAuth)) {
                return;
            }

            executarOperacoesConcorrentes(clienteId, servicoEventos, servicoAgregacoes);

            log(clienteId, "Concluído");

        } catch (Exception e) {
            logErro(clienteId, "Erro na execução: " + e.getMessage());
        } finally {
            if (middleware != null) {
                middleware.desconectar();
            }
        }
    }
    
    private static boolean autenticarCliente(int clienteId, IServicoAutenticacao servicoAuth) throws Exception {
        String username = "user" + clienteId;
        String password = "pass" + clienteId;
        UsuarioDTO usuario = new UsuarioDTO(username, password);

        // Tentar registar (ignora se já existe)
        try {
            servicoAuth.registrar(usuario);
        } catch (Exception e) {
            // Usuario já existe, continuar
        }

        RespostaDTO login = servicoAuth.autenticar(usuario);
        if (!login.isSucesso()) {
            logErro(clienteId, "Falha na autenticação");
            return false;
        }

        log(clienteId, "Autenticado como " + username);
        return true;
    }
    
    private static void executarOperacoesConcorrentes(int clienteId, 
                                                      IServicoEventos servicoEventos,
                                                      IServicoAgregacoes servicoAgregacoes) throws InterruptedException {
        ThreadPool threadPool = new ThreadPoolImpl(THREADS_POR_CLIENTE);
        
        for (int t = 0; t < THREADS_POR_CLIENTE; t++) {
            final int threadId = t;
            if(!threadPool.submit(() -> {
                executarThread(clienteId, threadId, servicoEventos, servicoAgregacoes);
            })) {
                logErro(clienteId, threadId, "Não foi possível submeter a thread. Fila cheia ou shutdown...");
            }
        }
        Thread.currentThread().sleep(5000); // espera 5 segundos para as threads terminarem, senao shutdown cancela novos pedidos
        threadPool.shutdown();
        threadPool.awaitTermination(TIMEOUT_MINUTOS, TimeUnit.MINUTES);
    }
    
    private static void executarThread(int clienteId, int threadId,
                                       IServicoEventos servicoEventos,
                                       IServicoAgregacoes servicoAgregacoes) {
        try {
            for (int i = 0; i < OPERACOES_POR_THREAD; i++) {
                executarOperacaoAleatoria(clienteId, threadId, servicoEventos, servicoAgregacoes);
                Thread.sleep(random.nextInt(DELAY_MAX_MS));
            }
        } catch (InterruptedException e) {
            logErro(clienteId, threadId, "Thread interrompida");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logErro(clienteId, threadId, "Erro: " + e.getMessage());
        }
    }
    
    private static void executarOperacaoAleatoria(int clienteId, int threadId,
                                                  IServicoEventos servicoEventos,
                                                  IServicoAgregacoes servicoAgregacoes) throws Exception {
        int operacao = random.nextInt(5);
        RespostaDTO resposta;
        
        switch (operacao) {
            case 0:
                resposta = registarEvento(servicoEventos);
                if (resposta.isSucesso()) {
                    eventosRegistados.incrementAndGet();
                }
                break;
            case 1:
                resposta = consultarQuantidade(servicoAgregacoes);
                if (resposta.isSucesso()) {
                    consultasRealizadas.incrementAndGet();
                }
                break;
            case 2:
                resposta = consultarVolume(servicoAgregacoes);
                if (resposta.isSucesso()) {
                    consultasRealizadas.incrementAndGet();
                }
                break;
            case 3:
                resposta = notificarVendaEspecifica(servicoEventos);
                if (resposta.isSucesso()) {
                    eventosRegistados.incrementAndGet();
                }
                break;
            case 4:
                resposta = notificarVendasConsecutivas(servicoEventos);
                if (resposta.isSucesso()) {
                    eventosRegistados.incrementAndGet();
                }
                break;
            default:
                throw new IllegalStateException("Operação inválida: " + operacao);
        }
        
        respostasRecebidas.incrementAndGet();
        log(clienteId, threadId, "Resposta: " + resposta.getMensagem());
    }
    
    private static RespostaDTO registarEvento(IServicoEventos servicoEventos) throws Exception {
        int produtoId = random.nextInt(NUM_PRODUTOS) + 1;
        int quantidade = random.nextInt(MAX_QUANTIDADE) + 1;
        double preco = PRECO_MIN + random.nextDouble() * (PRECO_MAX - PRECO_MIN);
        
        EventoDTO evento = new EventoDTO(produtoId, quantidade, preco);
        return servicoEventos.registrarEvento(evento);
    }
    
    private static RespostaDTO consultarQuantidade(IServicoAgregacoes servicoAgregacoes) throws Exception {
        int produtoId = random.nextInt(NUM_PRODUTOS) + 1;
        int dias = random.nextInt(MAX_DIAS) + 1;
        return servicoAgregacoes.obterQuantidadeVendas(produtoId, dias);
    }
    
    private static RespostaDTO consultarVolume(IServicoAgregacoes servicoAgregacoes) throws Exception {
        int produtoId = random.nextInt(NUM_PRODUTOS) + 1;
        int dias = random.nextInt(MAX_DIAS) + 1;
        return servicoAgregacoes.obterVolumeVendas(produtoId, dias);
    }

    private static RespostaDTO notificarVendaEspecifica(IServicoEventos servicoEventos) throws Exception {
        int produtoID1 = random.nextInt(NUM_PRODUTOS) + 1;
        int produtoID2 = random.nextInt(NUM_PRODUTOS) + 1;
        return servicoEventos.notificarVendaEspecifica(new NotificacaoDTO(produtoID1, produtoID2));
    }

    private static RespostaDTO notificarVendasConsecutivas(IServicoEventos servicoEventos) throws Exception {
        int produtoID = random.nextInt(NUM_PRODUTOS) + 1;
        int n = random.nextInt(3) + 1;
        return servicoEventos.notificarVendasConsecutivas(new NotificacaoDTO(produtoID, n));
    }
    
    // Métodos de logging
    
    private static void log(int clienteId, String mensagem) {
        System.out.printf("[Cliente %d] %s%n", clienteId, mensagem);
    }
    
    private static void log(int clienteId, int threadId, String mensagem) {
        System.out.printf("[Cliente %d | Thread %d] %s%n", clienteId, threadId, mensagem);
    }
    
    private static void logErro(int clienteId, String mensagem) {
        System.err.printf("[Cliente %d] ERRO: %s%n", clienteId, mensagem);
    }
    
    private static void logErro(int clienteId, int threadId, String mensagem) {
        System.err.printf("[Cliente %d | Thread %d] ERRO: %s%n", clienteId, threadId, mensagem);
    }
    
    // Classe auxiliar para configuração
    
    private static class ConfigTeste {
        final int numClientes;
        final String host;
        final int porta;
        
        ConfigTeste(int numClientes, String host, int porta) {
            this.numClientes = numClientes;
            this.host = host;
            this.porta = porta;
        }
    }
}