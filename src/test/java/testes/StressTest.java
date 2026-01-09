package testes;

import client.ClienteMiddleware;
import client.stub.StubFactory;
import common.dto.EventoDTO;
import common.dto.RespostaDTO;
import common.dto.UsuarioDTO;
import common.interfaces.IServicoAutenticacao;
import common.interfaces.IServicoEventos;
import server.Server;

import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;


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

        Thread.sleep(2000);
    }

    @AfterAll
    static void pararServidor() throws InterruptedException {
        if (servidor != null) servidor.parar();
        if (serverThread != null) serverThread.join(5000);
    }

    @Test
    @Order(1)
    @DisplayName("Carga: 50 clientes simultâneos")
    void testeCarga50Clientes() throws InterruptedException {

        int numClientes = 50;
        CountDownLatch latch = new CountDownLatch(numClientes);

        boolean[] ok = new boolean[numClientes];

        long inicio = System.currentTimeMillis();

        for (int i = 0; i < numClientes; i++) {
            final int clienteId = i;

            new Thread(() -> {
                try {
                    ClienteMiddleware middleware =
                            new ClienteMiddleware(HOST, PORT, true, 1);
                    middleware.conectar();

                    StubFactory stubs = new StubFactory(middleware);
                    IServicoAutenticacao auth = stubs.criarStubAutenticacao();
                    IServicoEventos eventos = stubs.criarStubEventos();

                    String username = "stress" + clienteId + System.nanoTime();
                    UsuarioDTO u = new UsuarioDTO(username, "pass" + clienteId);

                    RespostaDTO reg = auth.registrar(u);
                    if (reg == null || !reg.isSucesso()) return;

                    RespostaDTO login = auth.autenticar(u);
                    if (login == null || !login.isSucesso()) return;

                    for (int j = 0; j < 10; j++) {
                        eventos.registrarEvento(new EventoDTO(clienteId % 10 + 1, 1, 10.0));
                    }

                    ok[clienteId] = true;
                    middleware.desconectar();

                } catch (Exception ignored) {
                    ok[clienteId] = false;
                } finally {
                    latch.countDown();
                }
            }, "StressClient-" + i).start();
        }

        boolean terminou = latch.await(60, TimeUnit.SECONDS);
        long duracao = System.currentTimeMillis() - inicio;

        int sucessos = 0;
        for (int i = 0; i < numClientes; i++) if (ok[i]) sucessos++;
        int falhas = numClientes - sucessos;

        System.out.println("\n[Carga] Resultados:");
        System.out.println("  Sucessos: " + sucessos);
        System.out.println("  Falhas:   " + falhas);
        System.out.println("  Duração:  " + duracao + " ms");

        double taxaSucesso = (sucessos * 100.0) / numClientes;
        System.out.println("  Taxa:     " + String.format("%.1f%%", taxaSucesso));

        assertTrue(terminou, "Timeout no teste de carga");
        assertTrue(taxaSucesso >= 80.0,
                String.format("Taxa de sucesso inferior a 80%%: %.1f%% (%d/%d)",
                        taxaSucesso, sucessos, numClientes));
    }

    @Test
    @Order(2)
    @DisplayName("Throughput mínimo aceitável")
    void testeThroughput() throws InterruptedException {
        int totalRequisicoes = 1000;
        int numThreads = 10;

        CountDownLatch latch = new CountDownLatch(numThreads);

        int[] feitas = new int[numThreads];

        long inicio = System.currentTimeMillis();

        for (int i = 0; i < numThreads; i++) {
            final int id = i;

            new Thread(() -> {
                try {
                    ClienteMiddleware middleware =
                            new ClienteMiddleware(HOST, PORT, true, 1);
                    middleware.conectar();

                    StubFactory stubs = new StubFactory(middleware);
                    IServicoAutenticacao auth = stubs.criarStubAutenticacao();
                    IServicoEventos eventos = stubs.criarStubEventos();

                    String username = "throughput" + id + System.nanoTime();
                    UsuarioDTO u = new UsuarioDTO(username, "pass" + id);
                    auth.registrar(u);
                    auth.autenticar(u);

                    int alvo = totalRequisicoes / numThreads;
                    for (int j = 0; j < alvo; j++) {
                        eventos.registrarEvento(new EventoDTO(1, 1, 10.0));
                        feitas[id]++;
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

        int processadas = 0;
        for (int i = 0; i < numThreads; i++) processadas += feitas[i];

        double throughput = (processadas * 1000.0) / duracao;

        System.out.println("[Throughput] " + throughput + " req/s");

        assertTrue(throughput > 50, "Throughput demasiado baixo: " + throughput);
    }

    @Test
    @Order(3)
    @DisplayName("Latência média aceitável")
    void testeLatencia() {
        int numReq = 100;

        ClienteMiddleware middleware = null;
        try {
            middleware = new ClienteMiddleware(HOST, PORT, true, 1);
            middleware.conectar();

            StubFactory stubs = new StubFactory(middleware);
            IServicoAutenticacao auth = stubs.criarStubAutenticacao();
            IServicoEventos eventos = stubs.criarStubEventos();

            String username = "latency" + System.nanoTime();
            UsuarioDTO u = new UsuarioDTO(username, "pass");
            auth.registrar(u);
            auth.autenticar(u);

            long tempoTotalNs = 0;
            for (int i = 0; i < numReq; i++) {
                long ini = System.nanoTime();
                eventos.registrarEvento(new EventoDTO(1, 1, 10.0));
                long fim = System.nanoTime();
                tempoTotalNs += (fim - ini);
            }

            long latenciaMediaMs = tempoTotalNs / numReq / 1_000_000;

            System.out.println("[Latência] Média: " + latenciaMediaMs + " ms");

            assertTrue(latenciaMediaMs < 100, "Latência média demasiado alta");

        } catch (Exception e) {
            fail("Exceção durante teste de latência: " + e.getMessage());
        } finally {
            try {
                if (middleware != null) middleware.desconectar();
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("Carga sustentada durante 30s")
    void testeCargaSustentada() throws InterruptedException {

        int numThreads = 20;
        CountDownLatch latch = new CountDownLatch(numThreads);

        int[] sucessos = new int[numThreads];
        int[] falhas = new int[numThreads];

        final boolean[] running = {true};

        for (int i = 0; i < numThreads; i++) {
            final int id = i;

            new Thread(() -> {
                ClienteMiddleware middleware = null;
                try {
                    middleware = new ClienteMiddleware(HOST, PORT, true, 1);
                    middleware.conectar();

                    StubFactory stubs = new StubFactory(middleware);
                    IServicoAutenticacao auth = stubs.criarStubAutenticacao();
                    IServicoEventos eventos = stubs.criarStubEventos();

                    String username = "sustained" + id + System.nanoTime();
                    UsuarioDTO u = new UsuarioDTO(username, "pass" + id);
                    auth.registrar(u);
                    auth.autenticar(u);

                    while (running[0]) {
                        try {
                            eventos.registrarEvento(new EventoDTO(id % 5 + 1, 1, 10.0));
                            sucessos[id]++;
                            Thread.sleep(100);
                        } catch (Exception e) {
                            falhas[id]++;
                        }
                    }

                } catch (Exception ignored) {
                } finally {
                    try {
                        if (middleware != null) middleware.desconectar();
                    } catch (Exception ignored) {
                    }
                    latch.countDown();
                }
            }).start();
        }

        Thread.sleep(30_000);
        running[0] = false;

        assertTrue(latch.await(10, TimeUnit.SECONDS));

        int totalSucessos = 0;
        int totalFalhas = 0;
        for (int i = 0; i < numThreads; i++) {
            totalSucessos += sucessos[i];
            totalFalhas += falhas[i];
        }

        double taxaSucesso = (totalSucessos * 100.0) / (totalSucessos + totalFalhas);

        System.out.println("[Sustentado] Taxa sucesso: " + taxaSucesso + "%");

        assertTrue(taxaSucesso > 95, "Taxa de sucesso inferior a 95%");
    }
}
