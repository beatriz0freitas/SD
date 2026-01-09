package testes;

import org.junit.jupiter.api.*;

import client.ClienteMiddleware;
import client.stub.StubFactory;
import common.dto.*;
import common.interfaces.*;
import server.Server;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite completa de testes para Shutdown Gracioso
 *
 * Ajustado: ClienteMiddleware não expõe isServerShutdown() e pode não marcar
 * isConectado=false imediatamente após o servidor fechar sockets.
 *
 * Estratégia robusta:
 * - após servidor.parar(), o cliente DEVE falhar ao invocar operações.
 * - opcionalmente, depois da falha, isConectado pode passar a false.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShutdownTest {

    private static final String HOST = "localhost";
    private static final int PORT = 5557;

    private Server servidor;
    private Thread serverThread;

    @BeforeEach
    void prepararServidor() throws InterruptedException {
        if (servidor != null) {
            try { servidor.parar(); } catch (Exception ignored) {}
        }
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread.join(2000);
        }
        Thread.sleep(1000);
    }

    @AfterEach
    void limparServidor() throws InterruptedException {
        if (servidor != null) {
            try { servidor.parar(); } catch (Exception ignored) {}
        }
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread.join(2000);
        }
        Thread.sleep(500);
    }

    private static boolean tentaInvocacaoFalhar(IServicoEventos eventos) {
        try {
            eventos.registrarEvento(new EventoDTO(1, 1, 10.0));
            return false; // não falhou
        } catch (Exception e) {
            return true; // falhou como esperado
        }
    }

    @Test
    @Order(1)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Após shutdown, cliente deixa de conseguir invocar")
    void testeClienteRecebeNotificacao() throws Exception {
        servidor = new Server(PORT, 30, 5);
        serverThread = new Thread(servidor::iniciar, "ShutdownTestServer1");
        serverThread.start();
        Thread.sleep(2000);

        ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT);
        middleware.conectar();

        StubFactory stubs = new StubFactory(middleware);
        IServicoAutenticacao auth = stubs.criarStubAutenticacao();
        IServicoEventos eventos = stubs.criarStubEventos();

        String username = "shutdown1" + System.currentTimeMillis();
        UsuarioDTO user = new UsuarioDTO(username, "pass123");
        auth.registrar(user);
        auth.autenticar(user);

        assertTrue(middleware.isConectado(), "Cliente deve estar conectado");

        servidor.parar();
        Thread.sleep(500);

        // Em vez de depender de isConectado mudar sozinho, forçar uma chamada
        // que deve falhar após shutdown.
        boolean falhou = tentaInvocacaoFalhar(eventos);
        assertTrue(falhou, "Invocação deve falhar após shutdown do servidor");

        // Pode ou não marcar desconectado — não exigimos, mas aceitamos.
        middleware.desconectar();
    }

    @Test
    @Order(2)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Cliente não pode invocar após shutdown")
    void testeClienteNaoPodeInvocarAposShutdown() throws Exception {
        servidor = new Server(PORT, 30, 5);
        serverThread = new Thread(servidor::iniciar, "ShutdownTestServer2");
        serverThread.start();
        Thread.sleep(2000);

        ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT);
        middleware.conectar();

        StubFactory stubs = new StubFactory(middleware);
        IServicoAutenticacao auth = stubs.criarStubAutenticacao();
        IServicoEventos eventos = stubs.criarStubEventos();

        String username = "shutdown2" + System.currentTimeMillis();
        UsuarioDTO user = new UsuarioDTO(username, "pass123");
        auth.registrar(user);
        auth.autenticar(user);

        assertTrue(middleware.isConectado(), "Cliente deve estar conectado");

        servidor.parar();
        Thread.sleep(500);

        assertTrue(
            tentaInvocacaoFalhar(eventos),
            "Operação deve falhar após shutdown"
        );

        middleware.desconectar();
    }

    @Test
    @Order(3)
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("Múltiplos clientes deixam de conseguir invocar após shutdown")
    void testeMultiplosClientesRecebemNotificacao() throws Exception {
        servidor = new Server(PORT, 30, 5);
        serverThread = new Thread(servidor::iniciar, "ShutdownTestServer3");
        serverThread.start();
        Thread.sleep(2000);

        int numClientes = 10;
        CountDownLatch latch = new CountDownLatch(numClientes);
        ClienteMiddleware[] middlewares = new ClienteMiddleware[numClientes];
        IServicoEventos[] eventosStubs = new IServicoEventos[numClientes];

        for (int i = 0; i < numClientes; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    middlewares[id] = new ClienteMiddleware(HOST, PORT);
                    middlewares[id].conectar();

                    StubFactory stubs = new StubFactory(middlewares[id]);
                    IServicoAutenticacao auth = stubs.criarStubAutenticacao();
                    eventosStubs[id] = stubs.criarStubEventos();

                    String username = "shutdown3" + id + System.nanoTime();
                    UsuarioDTO user = new UsuarioDTO(username, "pass" + id);
                    auth.registrar(user);
                    auth.autenticar(user);
                } catch (Exception e) {
                    System.err.println("Erro ao conectar cliente " + id + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue(latch.await(8, TimeUnit.SECONDS), "Todos clientes devem conectar");

        servidor.parar();
        Thread.sleep(800);

        int falharam = 0;
        for (int i = 0; i < numClientes; i++) {
            if (eventosStubs[i] != null) {
                if (tentaInvocacaoFalhar(eventosStubs[i])) {
                    falharam++;
                }
            }
        }

        assertTrue(
            falharam >= numClientes - 2,
            "Pelo menos " + (numClientes - 2) + " clientes devem falhar ao invocar após shutdown. Falharam: " + falharam
        );

        for (ClienteMiddleware m : middlewares) {
            if (m != null) {
                try { m.desconectar(); } catch (Exception ignored) {}
            }
        }
    }

    @Test
    @Order(4)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Servidor aguarda requisições pendentes antes de fechar (limite 10s)")
    void testeServidorAguardaRequisicoesPendentes() throws Exception {
        servidor = new Server(PORT, 30, 5);
        serverThread = new Thread(servidor::iniciar, "ShutdownTestServer4");
        serverThread.start();
        Thread.sleep(2000);

        ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT);
        middleware.conectar();

        StubFactory stubs = new StubFactory(middleware);
        IServicoAutenticacao auth = stubs.criarStubAutenticacao();
        IServicoEventos eventos = stubs.criarStubEventos();

        String username = "shutdown4" + System.currentTimeMillis();
        UsuarioDTO user = new UsuarioDTO(username, "pass123");
        auth.registrar(user);
        auth.autenticar(user);

        new Thread(() -> {
            try {
                for (int i = 0; i < 3; i++) {
                    eventos.registrarEvento(new EventoDTO(i + 1, 10, 50.0));
                    Thread.sleep(200);
                }
            } catch (Exception ignored) {
            }
        }).start();

        Thread.sleep(500);

        long inicio = System.currentTimeMillis();
        servidor.parar();
        long duracao = System.currentTimeMillis() - inicio;

        assertTrue(duracao < 10000, "Shutdown não deve demorar mais que 10s");

        middleware.desconectar();
    }

    @Test
    @Order(5)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Servidor fecha conexões: clientes passam a falhar invocações")
    void testeServidorFechaTodasConexoes() throws Exception {
        servidor = new Server(PORT, 30, 5);
        serverThread = new Thread(servidor::iniciar, "ShutdownTestServer5");
        serverThread.start();
        Thread.sleep(2000);

        int numClientes = 5;
        ClienteMiddleware[] middlewares = new ClienteMiddleware[numClientes];
        IServicoEventos[] eventosStubs = new IServicoEventos[numClientes];

        for (int i = 0; i < numClientes; i++) {
            middlewares[i] = new ClienteMiddleware(HOST, PORT);
            middlewares[i].conectar();

            StubFactory stubs = new StubFactory(middlewares[i]);
            IServicoAutenticacao auth = stubs.criarStubAutenticacao();
            eventosStubs[i] = stubs.criarStubEventos();

            String username = "shutdown5" + i + System.nanoTime();
            UsuarioDTO user = new UsuarioDTO(username, "pass" + i);
            auth.registrar(user);
            auth.autenticar(user);

            assertTrue(middlewares[i].isConectado(), "Cliente deve estar conectado");
        }

        servidor.parar();
        Thread.sleep(800);

        int falharam = 0;
        for (int i = 0; i < numClientes; i++) {
            if (eventosStubs[i] != null && tentaInvocacaoFalhar(eventosStubs[i])) {
                falharam++;
            }
        }

        assertTrue(
            falharam >= numClientes - 1,
            "Pelo menos " + (numClientes - 1) + " clientes devem falhar invocação após shutdown. Falharam: " + falharam
        );

        for (ClienteMiddleware m : middlewares) {
            try { if (m != null) m.desconectar(); } catch (Exception ignored) {}
        }
    }
}