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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de escalabilidade do sistema completo
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ScalabilityTest {

    private static final String HOST = "localhost";
    private static final int PORT = 5562;

    private static Server servidor;
    private static Thread serverThread;

    private final List<ResultadoEscalabilidade> resultados = new ArrayList<>();

    @BeforeAll
    static void iniciarServidor() throws InterruptedException {
        System.out.println("\n=== TESTES DE ESCALABILIDADE ===\n");
        servidor = new Server(PORT, 150, 40);

        serverThread = new Thread(servidor::iniciar, "EscalabilidadeTestServer");
        serverThread.start();

        Thread.sleep(2000);
        System.out.println("OK Servidor iniciado na porta " + PORT + "\n");
    }

    @AfterAll
    static void pararServidor() throws InterruptedException {
        if (servidor != null) servidor.parar();
        if (serverThread != null) serverThread.join(5000);
    }

    @Test
    @Order(1)
    @DisplayName("Escalabilidade: 10 clientes")
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    void testeEscalabilidade_10Clientes() throws InterruptedException {
        ResultadoEscalabilidade r = executarTesteEscalabilidade(10, "10 clientes");
        resultados.add(r);

        assertTrue(r.taxaSucesso >= 80.0,
                "Taxa de sucesso inferior a 80%: " + r.taxaSucesso + "% (" + r.sucessos + "/" + r.numClientes + ")");
    }

    @Test
    @Order(2)
    @DisplayName("Escalabilidade: 25 clientes")
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testeEscalabilidade_25Clientes() throws InterruptedException {
        Thread.sleep(1000);

        ResultadoEscalabilidade r = executarTesteEscalabilidade(25, "25 clientes");
        resultados.add(r);

        assertTrue(r.taxaSucesso >= 80.0,
                "Taxa de sucesso inferior a 80%: " + r.taxaSucesso + "% (" + r.sucessos + "/" + r.numClientes + ")");
    }

    @Test
    @Order(3)
    @DisplayName("Escalabilidade: 50 clientes")
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void testeEscalabilidade_50Clientes() throws InterruptedException {
        Thread.sleep(1000);

        ResultadoEscalabilidade r = executarTesteEscalabilidade(50, "50 clientes");
        resultados.add(r);

        assertTrue(r.taxaSucesso >= 80.0,
                "Taxa de sucesso inferior a 80%: " + r.taxaSucesso + "% (" + r.sucessos + "/" + r.numClientes + ")");
    }

    @Test
    @Order(4)
    @DisplayName("Escalabilidade: 100 clientes")
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void testeEscalabilidade_100Clientes() throws InterruptedException {
        Thread.sleep(1000);

        ResultadoEscalabilidade r = executarTesteEscalabilidade(100, "100 clientes");
        resultados.add(r);

        assertTrue(r.taxaSucesso >= 80.0,
                "Taxa de sucesso inferior a 80%: " + r.taxaSucesso + "% (" + r.sucessos + "/" + r.numClientes + ")");
    }

    @Test
    @Order(5)
    @DisplayName("Escalabilidade: Análise comparativa")
    void testeAnaliseComparativa() {
        System.out.println("\n=== ANALISE COMPARATIVA ===\n");

        if (resultados.isEmpty()) {
            fail("Nenhum resultado disponivel");
            return;
        }

        System.out.println("Clientes | Sucessos | Falhas | Tempo(s) | Throughput(ops/s) | Taxa(%)");
        System.out.println("---------|----------|--------|----------|-------------------|--------");

        for (ResultadoEscalabilidade r : resultados) {
            System.out.printf(
                    "%8d | %8d | %6d | %8.2f | %17.2f | %7.1f%n",
                    r.numClientes, r.sucessos, r.falhas, r.tempoSegundos, r.throughputOps, r.taxaSucesso
            );
        }

        System.out.println("\n=== ANALISE DE DEGRADACAO (informativa) ===\n");

        for (int i = 1; i < resultados.size(); i++) {
            ResultadoEscalabilidade anterior = resultados.get(i - 1);
            ResultadoEscalabilidade atual = resultados.get(i);

            double fatorClientes = (double) atual.numClientes / anterior.numClientes;
            double fatorTempo = atual.tempoSegundos / anterior.tempoSegundos;

            System.out.printf("%d -> %d clientes: tempo aumentou %.2fx (clientes %.2fx)%n",
                    anterior.numClientes, atual.numClientes, fatorTempo, fatorClientes);
        }

        for (ResultadoEscalabilidade r : resultados) {
            assertTrue(r.taxaSucesso >= 80.0,
                    "Taxa de sucesso baixa: " + r.taxaSucesso + "% com " + r.numClientes + " clientes");
        }

        System.out.println("\nOK Todos os testes passaram!\n");
    }

    private ResultadoEscalabilidade executarTesteEscalabilidade(int numClientes, String descricao)
            throws InterruptedException {

        System.out.println("\n=== TESTE: " + descricao + " ===");

        final boolean[] ok = new boolean[numClientes];
        CountDownLatch latch = new CountDownLatch(numClientes);

        long inicio = System.currentTimeMillis();

        for (int i = 0; i < numClientes; i++) {
            final int clienteId = i;

            new Thread(() -> {
                ClienteMiddleware middleware = null;

                try {
                    middleware = new ClienteMiddleware(HOST, PORT, true, 10);
                    middleware.conectar();

                    StubFactory stubs = new StubFactory(middleware);
                    IServicoAutenticacao auth = stubs.criarStubAutenticacao();
                    IServicoEventos eventos = stubs.criarStubEventos();

                    String username = "scale" + numClientes + clienteId + System.nanoTime();
                    UsuarioDTO u = new UsuarioDTO(username, "pass" + clienteId);

                    RespostaDTO reg = auth.registrar(u);
                    if (!reg.isSucesso()) return;

                    RespostaDTO login = auth.autenticar(u);
                    if (!login.isSucesso()) return;

                    for (int j = 0; j < 10; j++) {
                        RespostaDTO resp = eventos.registrarEvento(
                                new EventoDTO((clienteId % 10) + 1, 1, 10.0)
                        );
                        if (!resp.isSucesso()) return;
                    }

                    ok[clienteId] = true;

                } catch (Exception ignored) {
                } finally {
                    try {
                        if (middleware != null) middleware.desconectar();
                    } catch (Exception ignored) {
                    }
                    latch.countDown();
                }
            }, "ScaleClient-" + i).start();
        }

        boolean terminou = latch.await(Math.max(30, numClientes / 2), TimeUnit.SECONDS);
        long duracaoMs = System.currentTimeMillis() - inicio;

        assertTrue(terminou, "Timeout para " + numClientes + " clientes");

        int sucessos = 0;
        for (int i = 0; i < numClientes; i++) {
            if (ok[i]) sucessos++;
        }
        int falhas = numClientes - sucessos;

        double tempoSegundos = duracaoMs / 1000.0;
        double taxaSucesso = (sucessos * 100.0) / numClientes;


        int opsPorCliente = 12;
        double totalOps = (double) sucessos * opsPorCliente;
        double throughput = tempoSegundos > 0 ? (totalOps / tempoSegundos) : 0.0;

        System.out.println("Tempo: " + duracaoMs + " ms");
        System.out.println("Sucessos: " + sucessos + " / " + numClientes);
        System.out.println("Falhas: " + falhas);
        System.out.println("Taxa: " + String.format("%.1f", taxaSucesso) + "%");
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " ops/s");

        return new ResultadoEscalabilidade(
                numClientes,
                sucessos,
                falhas,
                tempoSegundos,
                throughput,
                taxaSucesso
        );
    }

    private static class ResultadoEscalabilidade {
        final int numClientes;
        final int sucessos;
        final int falhas;
        final double tempoSegundos;
        final double throughputOps;
        final double taxaSucesso;

        ResultadoEscalabilidade(int numClientes, int sucessos, int falhas,
                                double tempoSegundos, double throughputOps, double taxaSucesso) {
            this.numClientes = numClientes;
            this.sucessos = sucessos;
            this.falhas = falhas;
            this.tempoSegundos = tempoSegundos;
            this.throughputOps = throughputOps;
            this.taxaSucesso = taxaSucesso;
        }
    }
}