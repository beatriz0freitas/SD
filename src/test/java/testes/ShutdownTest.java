package testes;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;

import client.ClienteMiddleware;
import client.stub.StubFactory;
import common.dto.EventoDTO;
import common.dto.UsuarioDTO;
import common.interfaces.IServicoAutenticacao;
import common.interfaces.IServicoEventos;
import server.Server;

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
        Thread.sleep(300);
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
        Thread.sleep(200);
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
    @DisplayName("Cliente deteta shutdown: operação falha após server.parar()")
    void testeClienteDetetaShutdownPorOperacaoFalhar() throws Exception {
        servidor = new Server(PORT, 30, 5);
        serverThread = new Thread(servidor::iniciar, "ShutdownTestServer1");
        serverThread.start();
        Thread.sleep(1500);

        ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT);
        middleware.conectar();

        StubFactory stubs = new StubFactory(middleware);
        IServicoAutenticacao auth = stubs.criarStubAutenticacao();
        IServicoEventos eventos = stubs.criarStubEventos();

        String username = "shutdown1" + System.currentTimeMillis();
        UsuarioDTO user = new UsuarioDTO(username, "pass123");
        auth.registrar(user);
        auth.autenticar(user);

        servidor.parar();

        // ✅ critério robusto: uma chamada tem de falhar
        boolean falhou = false;
        try {
            eventos.registrarEvento(new EventoDTO(1, 1, 1.0));
        } catch (Exception e) {
            falhou = true;
        }

        assertTrue(falhou, "Após shutdown, invocação deve falhar");

        // Pode ou não marcar desconectado — não exigimos, mas aceitamos.
        middleware.desconectar();
    }

    @Test
    @Order(2)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Cliente não consegue invocar após shutdown")
    void testeClienteNaoPodeInvocarAposShutdown() throws Exception {
        servidor = new Server(PORT, 30, 5);
        serverThread = new Thread(servidor::iniciar, "ShutdownTestServer2");
        serverThread.start();
        Thread.sleep(1500);

        ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT);
        middleware.conectar();

        StubFactory stubs = new StubFactory(middleware);
        IServicoAutenticacao auth = stubs.criarStubAutenticacao();
        IServicoEventos eventos = stubs.criarStubEventos();

        String username = "shutdown2" + System.currentTimeMillis();
        UsuarioDTO user = new UsuarioDTO(username, "pass123");
        auth.registrar(user);
        auth.autenticar(user);

        servidor.parar();

        boolean falhou = false;
        try {
            eventos.registrarEvento(new EventoDTO(1, 10, 50.0));
        } catch (Exception e) {
            falhou = true;
        }

        assertTrue(falhou, "Operação deve falhar após shutdown");
        middleware.desconectar();
    }

    @Test
    @Order(3)
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("Múltiplos clientes: operações falham após shutdown")
    void testeMultiplosClientesOperacoesFalham() throws Exception {
        servidor = new Server(PORT, 30, 5);
        serverThread = new Thread(servidor::iniciar, "ShutdownTestServer3");
        serverThread.start();
        Thread.sleep(1500);

        int numClientes = 10;
        CountDownLatch latchConectados = new CountDownLatch(numClientes);
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
                } catch (Exception ignored) {
                } finally {
                    latchConectados.countDown();
                }
            }).start();
        }

        assertTrue(latchConectados.await(8, TimeUnit.SECONDS), "Clientes devem iniciar");

        servidor.parar();

        int falharam = 0;
        for (int i = 0; i < numClientes; i++) {
            if (middlewares[i] == null) continue;
            try {
                IServicoEventos eventos = new StubFactory(middlewares[i]).criarStubEventos();
                eventos.registrarEvento(new EventoDTO(1, 1, 1.0));
            } catch (Exception e) {
                falharam++;
            }
        }

        assertTrue(falharam >= numClientes - 2, "A maioria das operações deve falhar. Falharam: " + falharam);

        for (ClienteMiddleware m : middlewares) {
            if (m != null) {
                try { m.desconectar(); } catch (Exception ignored) {}
            }
        }
    }
}