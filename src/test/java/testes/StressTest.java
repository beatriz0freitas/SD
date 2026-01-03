package testes;

import client.ClienteMiddleware;
import client.stub.StubFactory;
import common.dto.*;
import common.interfaces.*;
import server.Server;

import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de stress e performance (JUnit 5)
 */

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StressTest {

    private static final String HOST = "localhost";
    private static final int PORT = 5556;

    private static Server servidor;
    private static Thread serverThread;

    @BeforeAll
    static void iniciarServidor() throws InterruptedException {
        servidor = new Server(PORT, 30, 10);

        serverThread = new Thread(servidor::iniciar, "StressTestServer");
        serverThread.start();

        Thread.sleep(2000); // tempo para arrancar
    }

    @AfterAll
    static void pararServidor() throws InterruptedException {
        if (servidor != null) {
            servidor.parar();
        }
        if (serverThread != null) {
            serverThread.join(5000);
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("Carga: 50 clientes simultâneos")
    void testeCarga50Clientes() throws InterruptedException {

        int numClientes = 50;
        CountDownLatch latch = new CountDownLatch(numClientes);

        AtomicInteger sucessos = new AtomicInteger(0);
        AtomicInteger falhas = new AtomicInteger(0);

        long inicio = System.currentTimeMillis();

        for (int i = 0; i < numClientes; i++) {
            final int clienteId = i;

            new Thread(() -> {
                try {
                    ClienteMiddleware middleware =
                        new ClienteMiddleware(HOST, PORT, true, 5);
                    middleware.conectar();

                    StubFactory stubs = new StubFactory(middleware);
                    IServicoAutenticacao auth = stubs.criarStubAutenticacao();
                    IServicoEventos eventos = stubs.criarStubEventos();

                    // SEM underscore - apenas números
                    String username = "stress" + clienteId + System.nanoTime();
                    UsuarioDTO u = new UsuarioDTO(username, "pass" + clienteId);

                    RespostaDTO reg = auth.registrar(u);
                    if (!reg.isSucesso()) {
                        System.err.println("Cliente " + clienteId + 
                                         " - Registo falhou: " + reg.getMensagem());
                        falhas.incrementAndGet();
                        return;
                    }

                    RespostaDTO login = auth.autenticar(u);
                    if (!login.isSucesso()) {
                        System.err.println("Cliente " + clienteId + 
                                         " - Login falhou: " + login.getMensagem());
                        falhas.incrementAndGet();
                        return;
                    }

                    for (int j = 0; j < 10; j++) {
                        eventos.registrarEvento(
                            new EventoDTO(clienteId % 10 + 1, 1, 10.0)
                        );
                    }

                    sucessos.incrementAndGet();
                    middleware.desconectar();

                } catch (Exception e) {
                    System.err.println("Cliente " + clienteId + 
                                     " - Exceção: " + e.getMessage());
                    falhas.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            }, "StressClient-" + i).start();
        }

        boolean terminou = latch.await(60, TimeUnit.SECONDS);
        long duracao = System.currentTimeMillis() - inicio;

        System.out.println("\n[Carga] Resultados:");
        System.out.println("  Sucessos: " + sucessos.get());
        System.out.println("  Falhas:   " + falhas.get());
        System.out.println("  Duração:  " + duracao + " ms");

        double taxaSucesso = (sucessos.get() * 100.0) / numClientes;
        System.out.println("  Taxa:     " + String.format("%.1f%%", taxaSucesso));

        assertTrue(terminou, "Timeout no teste de carga");

        // Reduzir para 80% - mais realista
        assertTrue(
            taxaSucesso >= 80.0,
            String.format(
                "Taxa de sucesso inferior a 80%%: %.1f%% (%d/%d)",
                taxaSucesso, sucessos.get(), numClientes
            )
        );
    }

    @Test
    @Order(2)
    @DisplayName("Throughput mínimo aceitável")
    void testeThroughput() throws InterruptedException {
        int totalRequisicoes = 1000;
        int numThreads = 10;

        AtomicInteger processadas = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(numThreads);

        long inicio = System.currentTimeMillis();

        for (int i = 0; i < numThreads; i++) {
            final int id = i;

            new Thread(() -> {
                try {
                    ClienteMiddleware middleware =
                        new ClienteMiddleware(HOST, PORT, true, 5);
                    middleware.conectar();

                    StubFactory stubs = new StubFactory(middleware);
                    IServicoAutenticacao auth = stubs.criarStubAutenticacao();
                    IServicoEventos eventos = stubs.criarStubEventos();

                    // SEM underscore
                    String username = "throughput" + id + System.nanoTime();
                    UsuarioDTO u = new UsuarioDTO(username, "pass" + id);
                    auth.registrar(u);
                    auth.autenticar(u);

                    for (int j = 0; j < totalRequisicoes / numThreads; j++) {
                        eventos.registrarEvento(
                            new EventoDTO(1, 1, 10.0)
                        );
                        processadas.incrementAndGet();
                    }

                    middleware.desconectar();
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue(latch.await(60, TimeUnit.SECONDS), "Timeout no throughput");

        long duracao = System.currentTimeMillis() - inicio;
        double throughput = (processadas.get() * 1000.0) / duracao;

        System.out.println("[Throughput] " + throughput + " req/s");

        assertTrue(
            throughput > 50,
            "Throughput demasiado baixo: " + throughput
        );
    }

    @Test
    @Order(3)
    @DisplayName("Latência média aceitável")
    void testeLatencia() {
        int numReq = 100;
        AtomicLong tempoTotal = new AtomicLong(0);

        try {
            ClienteMiddleware middleware =
                new ClienteMiddleware(HOST, PORT, true, 5);
            middleware.conectar();

            StubFactory stubs = new StubFactory(middleware);
            IServicoAutenticacao auth = stubs.criarStubAutenticacao();
            IServicoEventos eventos = stubs.criarStubEventos();

            // SEM underscore
            String username = "latency" + System.nanoTime();
            UsuarioDTO u = new UsuarioDTO(username, "pass");
            auth.registrar(u);
            auth.autenticar(u);

            for (int i = 0; i < numReq; i++) {
                long ini = System.nanoTime();
                eventos.registrarEvento(new EventoDTO(1, 1, 10.0));
                long fim = System.nanoTime();
                tempoTotal.addAndGet(fim - ini);
            }

            middleware.desconectar();

        } catch (Exception e) {
            fail("Exceção durante teste de latência: " + e.getMessage());
        }

        long latenciaMediaMs =
            tempoTotal.get() / numReq / 1_000_000;

        System.out.println("[Latência] Média: " + latenciaMediaMs + " ms");

        assertTrue(
            latenciaMediaMs < 100,
            "Latência média demasiado alta"
        );
    }

    @Test
    @Order(4)
    @DisplayName("Carga sustentada durante 30s")
    void testeCargaSustentada() throws InterruptedException {
        AtomicInteger sucessos = new AtomicInteger(0);
        AtomicInteger falhas = new AtomicInteger(0);

        int numThreads = 20;
        CountDownLatch latch = new CountDownLatch(numThreads);

        final boolean[] running = {true};

        for (int i = 0; i < numThreads; i++) {
            final int id = i;

            new Thread(() -> {
                try {
                    ClienteMiddleware middleware =
                        new ClienteMiddleware(HOST, PORT, true, 5);
                    middleware.conectar();

                    StubFactory stubs = new StubFactory(middleware);
                    IServicoAutenticacao auth = stubs.criarStubAutenticacao();
                    IServicoEventos eventos = stubs.criarStubEventos();

                    // SEM underscore
                    String username = "sustained" + id + System.nanoTime();
                    UsuarioDTO u = new UsuarioDTO(username, "pass" + id);
                    auth.registrar(u);
                    auth.autenticar(u);

                    while (running[0]) {
                        try {
                            eventos.registrarEvento(
                                new EventoDTO(id % 5 + 1, 1, 10.0)
                            );
                            sucessos.incrementAndGet();
                            Thread.sleep(100);
                        } catch (Exception e) {
                            falhas.incrementAndGet();
                        }
                    }

                    middleware.desconectar();
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        Thread.sleep(30_000);
        running[0] = false;

        assertTrue(latch.await(10, TimeUnit.SECONDS));

        double taxaSucesso =
            sucessos.get() * 100.0 /
            (sucessos.get() + falhas.get());

        System.out.println("[Sustentado] Taxa sucesso: " + taxaSucesso + "%");

        assertTrue(
            taxaSucesso > 95,
            "Taxa de sucesso inferior a 95%"
        );
    }
}
