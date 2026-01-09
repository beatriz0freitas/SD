package testes;

import client.ClienteMiddleware;
import client.stub.StubFactory;
import common.dto.EventoDTO;
import common.dto.FiltrarEventosDTO;
import common.dto.NotificacaoDTO;
import common.dto.RespostaDTO;
import common.dto.UsuarioDTO;
import common.interfaces.IServicoAgregacoes;
import common.interfaces.IServicoAutenticacao;
import common.interfaces.IServicoEventos;
import server.Server;

import org.junit.jupiter.api.*;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CargaTrabalhoTest {

    private static final String HOST = "localhost";
    private static final int PORT = 5565;

    private Server servidor;
    private Thread serverThread;

    @BeforeAll
    void iniciarServidor() throws InterruptedException {
        servidor = new Server(PORT, 60, 15);
        serverThread = new Thread(servidor::iniciar, "CargaTrabalhoServer");
        serverThread.start();
        Thread.sleep(2000);
    }

    @AfterAll
    void pararServidor() throws InterruptedException {
        if (servidor != null) servidor.parar();
        if (serverThread != null) serverThread.join(5000);
    }

    private static UsuarioDTO novoUtilizador(String prefixo) {
        return new UsuarioDTO(prefixo + System.nanoTime(), "pass123");
    }

    private static class Resultado {
        final int totalOps;
        final int sucessos;
        final int falhas;
        final long duracaoMs;

        Resultado(int totalOps, int sucessos, int falhas, long duracaoMs) {
            this.totalOps = totalOps;
            this.sucessos = sucessos;
            this.falhas = falhas;
            this.duracaoMs = duracaoMs;
        }

        double taxaSucesso() {
            return totalOps == 0 ? 0.0 : (sucessos * 100.0) / totalOps;
        }

        double throughputOpsPorSeg() {
            double s = duracaoMs / 1000.0;
            return s > 0 ? sucessos / s : 0.0;
        }
    }

    @Test
    @Order(1)
    @DisplayName("Workload 1: Write-heavy (muitos registos de evento)")
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    void workloadWriteHeavy() throws Exception {

        ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT, true, 1);
        middleware.conectar();

        try {
            StubFactory stubs = new StubFactory(middleware);
            IServicoAutenticacao auth = stubs.criarStubAutenticacao();
            IServicoEventos eventos = stubs.criarStubEventos();

            UsuarioDTO u = novoUtilizador("write");
            assertTrue(auth.registrar(u).isSucesso());
            assertTrue(auth.autenticar(u).isSucesso());

            int total = 500;
            int sucessos = 0;

            long ini = System.currentTimeMillis();

            for (int i = 0; i < total; i++) {
                RespostaDTO r = eventos.registrarEvento(new EventoDTO((i % 10) + 1, 1, 10.0));
                if (r != null && r.isSucesso()) sucessos++;
            }

            long dur = System.currentTimeMillis() - ini;
            int falhas = total - sucessos;

            Resultado res = new Resultado(total, sucessos, falhas, dur);

            System.out.println("\n[WORKLOAD Write-heavy]");
            System.out.println("  Ops:      " + total);
            System.out.println("  Sucessos: " + sucessos);
            System.out.println("  Falhas:   " + falhas);
            System.out.println("  Taxa:     " + String.format("%.1f", res.taxaSucesso()) + "%");
            System.out.println("  Tempo:    " + dur + " ms");
            System.out.println("  Thrpt:    " + String.format("%.2f", res.throughputOpsPorSeg()) + " ops/s\n");

            assertTrue(res.taxaSucesso() >= 90.0, "Taxa de sucesso demasiado baixa em write-heavy.");

        } finally {
            middleware.desconectar();
        }
    }

    @Test
    @Order(2)
    @DisplayName("Workload 2: Read-heavy (muitas consultas de agregações)")
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    void workloadReadHeavy() throws Exception {

        ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT, true, 1);
        middleware.conectar();

        try {
            StubFactory stubs = new StubFactory(middleware);
            IServicoAutenticacao auth = stubs.criarStubAutenticacao();
            IServicoEventos eventos = stubs.criarStubEventos();
            IServicoAgregacoes aggs = stubs.criarStubAgregacoes();

            UsuarioDTO u = novoUtilizador("read");
            assertTrue(auth.registrar(u).isSucesso());
            assertTrue(auth.autenticar(u).isSucesso());

            
            for (int i = 0; i < 50; i++) {
                eventos.registrarEvento(new EventoDTO(1, 1, 10.0));
            }
            eventos.novoDia();

            int total = 500;
            int sucessos = 0;

            long ini = System.currentTimeMillis();

            for (int i = 0; i < total; i++) {
                RespostaDTO r = aggs.obterQuantidadeVendas(1, 1);
                if (r != null && r.isSucesso()) sucessos++;
            }

            long dur = System.currentTimeMillis() - ini;
            int falhas = total - sucessos;

            Resultado res = new Resultado(total, sucessos, falhas, dur);

            System.out.println("\n[WORKLOAD Read-heavy]");
            System.out.println("  Ops:      " + total);
            System.out.println("  Sucessos: " + sucessos);
            System.out.println("  Falhas:   " + falhas);
            System.out.println("  Taxa:     " + String.format("%.1f", res.taxaSucesso()) + "%");
            System.out.println("  Tempo:    " + dur + " ms");
            System.out.println("  Thrpt:    " + String.format("%.2f", res.throughputOpsPorSeg()) + " ops/s\n");

            assertTrue(res.taxaSucesso() >= 90.0, "Taxa de sucesso demasiado baixa em read-heavy.");

        } finally {
            middleware.desconectar();
        }
    }

    @Test
    @Order(3)
    @DisplayName("Workload 3: Mixed concorrente (escrita + leitura; filtro/notificação em minoria)")
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void workloadMixedConcorrente() throws InterruptedException {

        final int clientes = 20;
        final int eventosPorCliente = 10;
        final int leiturasPorCliente = 10;

        
        final int filtroCadaN = 5;

        
        final int notifCadaN = 10;

        CountDownLatch latch = new CountDownLatch(clientes);
        boolean[] ok = new boolean[clientes];

        long ini = System.currentTimeMillis();

        for (int i = 0; i < clientes; i++) {
            final int id = i;

            new Thread(() -> {
                ClienteMiddleware middleware = null;
                try {
                    middleware = new ClienteMiddleware(HOST, PORT, true, 1);
                    middleware.conectar();

                    StubFactory stubs = new StubFactory(middleware);
                    IServicoAutenticacao auth = stubs.criarStubAutenticacao();
                    IServicoEventos eventos = stubs.criarStubEventos();
                    IServicoAgregacoes aggs = stubs.criarStubAgregacoes();

                    UsuarioDTO u = new UsuarioDTO("mix" + id + System.nanoTime(), "pass" + id);

                    RespostaDTO reg = auth.registrar(u);
                    if (reg == null || !reg.isSucesso()) return;

                    RespostaDTO login = auth.autenticar(u);
                    if (login == null || !login.isSucesso()) return;

                    
                    for (int j = 0; j < eventosPorCliente; j++) {
                        RespostaDTO r = eventos.registrarEvento(new EventoDTO((id % 10) + 1, 1, 10.0));
                        if (r == null || !r.isSucesso()) return;
                    }

                    
                    for (int j = 0; j < leiturasPorCliente; j++) {
                        RespostaDTO r = aggs.obterQuantidadeVendas((id % 10) + 1, 1);
                        if (r == null || !r.isSucesso()) return;
                    }

                    
                    if (id % notifCadaN == 0) {
                        try {
                            eventos.notificarVendaEspecifica(new NotificacaoDTO(1, 2));
                        } catch (Exception ignored) {
                            
                        }
                    }

                    
                    if (id % filtroCadaN == 0) {
                        Set<Integer> produtos = new HashSet<>();
                        produtos.add(1);
                        produtos.add(3);
                        try {
                            RespostaDTO r = eventos.filtrarEventos(new FiltrarEventosDTO(produtos, 1));
                            if (r == null || !r.isSucesso()) return;
                        } catch (Exception e) {
                            return;
                        }
                    }

                    ok[id] = true;

                } catch (Exception ignored) {
                } finally {
                    try { if (middleware != null) middleware.desconectar(); } catch (Exception ignored) {}
                    latch.countDown();
                }
            }, "MixedClient-" + i).start();
        }

        assertTrue(latch.await(80, TimeUnit.SECONDS), "Timeout no workload mixed.");

        long dur = System.currentTimeMillis() - ini;

        int sucessos = 0;
        for (int i = 0; i < clientes; i++) if (ok[i]) sucessos++;

        double taxa = (sucessos * 100.0) / clientes;

        System.out.println("\n[WORKLOAD Mixed concorrente]");
        System.out.println("  Clientes: " + clientes);
        System.out.println("  Sucessos: " + sucessos);
        System.out.println("  Taxa:     " + String.format("%.1f", taxa) + "%");
        System.out.println("  Tempo:    " + dur + " ms\n");

        assertTrue(taxa >= 70.0, "Taxa de sucesso demasiado baixa em workload mixed.");
    }
}
