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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de integração do sistema completo
 * Cliente -> Middleware -> Servidor
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTest {

    private static final String HOST = "localhost";
    private static final int PORT = 5555;

    private Server servidor;
    private Thread serverThread;

    /* =========================
       Setup / Teardown
       ========================= */

    @BeforeAll
    void iniciarServidor() throws InterruptedException {
        servidor = new Server(PORT, 30, 5);

        serverThread = new Thread(servidor::iniciar, "TestServer");
        serverThread.start();

        // Aguardar servidor ficar disponível
        Thread.sleep(1500);
    }

    @AfterAll
    void pararServidor() throws InterruptedException {
        if (servidor != null) {
            servidor.parar();
        }

        if (serverThread != null) {
            serverThread.join(5000);
        }
    }

    /* =========================
       Testes
       ========================= */

    @Test
    @Order(1)
    void testeAutenticacaoBasica() throws Exception {
        ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT);
        middleware.conectar();

        StubFactory stubs = new StubFactory(middleware);
        IServicoAutenticacao auth = stubs.criarStubAutenticacao();

        UsuarioDTO user = new UsuarioDTO("testuser1", "password123");

        RespostaDTO reg = auth.registrar(user);
        assertTrue(reg.isSucesso(), "Registo deve ter sucesso");

        RespostaDTO login = auth.autenticar(user);
        assertTrue(login.isSucesso(), "Autenticação deve ter sucesso");

        middleware.desconectar();
    }

    @Test
    @Order(2)
    void testeRegistroEventos() throws Exception {
        ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT);
        middleware.conectar();

        StubFactory stubs = new StubFactory(middleware);
        IServicoAutenticacao auth = stubs.criarStubAutenticacao();
        IServicoEventos eventos = stubs.criarStubEventos();

        UsuarioDTO user = new UsuarioDTO("testuser2", "password123");
        auth.registrar(user);
        auth.autenticar(user);

        int sucesso = 0;
        for (int i = 0; i < 5; i++) {
            RespostaDTO r = eventos.registrarEvento(new EventoDTO(1, 10, 50.0));
            if (r.isSucesso()) sucesso++;
        }

        assertEquals(5, sucesso, "Devem ser registados 5 eventos");

        middleware.desconectar();
    }

    @Test
    @Order(3)
    void testeAgregacoes() throws Exception {
        ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT);
        middleware.conectar();

        StubFactory stubs = new StubFactory(middleware);
        IServicoAutenticacao auth = stubs.criarStubAutenticacao();
        IServicoEventos eventos = stubs.criarStubEventos();
        IServicoAgregacoes aggs = stubs.criarStubAgregacoes();

        UsuarioDTO user = new UsuarioDTO("testuser3", "password123");
        auth.registrar(user);
        auth.autenticar(user);

        for (int i = 0; i < 3; i++) {
            eventos.registrarEvento(new EventoDTO(2, 5, 100.0));
        }

        eventos.novoDia();

        RespostaDTO resp = aggs.obterQuantidadeVendas(2, 1);
        assertTrue(resp.isSucesso());

        assertEquals(15, resp.getDados());

        middleware.desconectar();
    }

    @Test
    @Order(4)
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testeClientesConcorrentes() throws InterruptedException {
        int clientes = 10;
        AtomicInteger sucesso = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(clientes);

        for (int i = 0; i < clientes; i++) {
            final int id = i;

            new Thread(() -> {
                try {
                    ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT);
                    middleware.conectar();

                    StubFactory stubs = new StubFactory(middleware);
                    IServicoAutenticacao auth = stubs.criarStubAutenticacao();
                    IServicoEventos eventos = stubs.criarStubEventos();

                    UsuarioDTO user = new UsuarioDTO("concurrent" + id, "pass" + id);
                    auth.registrar(user);
                    RespostaDTO login = auth.autenticar(user);

                    if (login.isSucesso()) {
                        for (int j = 0; j < 3; j++) {
                            eventos.registrarEvento(new EventoDTO(id, 1, 10.0));
                        }
                        sucesso.incrementAndGet();
                    }

                    middleware.desconectar();
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            }, "Client-" + i).start();
        }

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        assertEquals(10, sucesso.get(), "Todos os clientes devem completar com sucesso");
    }

    @Test
    @Order(5)
    void testeConnectionPoolVsDedicado() throws Exception {
        long dedicado = testarPerformance(false, 50);
        long pool = testarPerformance(true, 50);

        assertTrue(pool <= dedicado * 1.5,
                "Pool deve ter performance comparável à ligação dedicada");
    }

    private long testarPerformance(boolean usePool, int n) throws Exception {
        ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT, usePool, 10);
        middleware.conectar();

        StubFactory stubs = new StubFactory(middleware);
        IServicoAutenticacao auth = stubs.criarStubAutenticacao();
        IServicoEventos eventos = stubs.criarStubEventos();

        UsuarioDTO user = new UsuarioDTO("perf" + System.nanoTime(), "pass");
        auth.registrar(user);
        auth.autenticar(user);

        long ini = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            eventos.registrarEvento(new EventoDTO(1, 1, 10.0));
        }
        long fim = System.currentTimeMillis();

        middleware.desconectar();
        return fim - ini;
    }

    @Test
    @Order(6)
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testeNotificacoes() throws Exception {
        ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT);
        middleware.conectar();

        StubFactory stubs = new StubFactory(middleware);
        IServicoAutenticacao auth = stubs.criarStubAutenticacao();
        IServicoEventos eventos = stubs.criarStubEventos();

        UsuarioDTO user = new UsuarioDTO("notiftest", "pass123");
        auth.registrar(user);
        auth.autenticar(user);

        AtomicInteger notificado = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        Thread t = new Thread(() -> {
            try {
                RespostaDTO r = eventos.notificarVendaEspecifica(
                    new NotificacaoDTO(10, 11)
                );
                if (r.isSucesso()) notificado.set(1);
            } catch (Exception e) {
                fail("Erro inesperado na notificação: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
        
        t.start();

        eventos.registrarEvento(new EventoDTO(10, 1, 100.0));
        eventos.registrarEvento(new EventoDTO(11, 1, 100.0));

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(1, notificado.get(), "Notificação deve ser recebida");

        middleware.desconectar();
    }
}
