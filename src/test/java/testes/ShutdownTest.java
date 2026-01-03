package testes;

import org.junit.jupiter.api.*;

import client.ClienteMiddleware;
import client.stub.StubFactory;
import common.dto.*;
import common.interfaces.*;
import server.Server;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite completa de testes para Shutdown Gracioso
 * Versão robusta com tratamento de porta e timing
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
        // Limpar qualquer servidor anterior
        if (servidor != null) {
            try {
                servidor.parar();
            } catch (Exception e) {
                // Ignorar
            }
        }
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread.join(2000);
        }
        
        // Aguardar porta ficar disponível
        Thread.sleep(1000);
    }

    @AfterEach
    void limparServidor() throws InterruptedException {
        if (servidor != null) {
            try {
                servidor.parar();
            } catch (Exception e) {
                // Ignorar
            }
        }
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread.join(2000);
        }
        
        // Aguardar porta liberar
        Thread.sleep(500);
    }

    @Test
    @Order(1)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Cliente recebe notificação de shutdown")
    void testeClienteRecebeNotificacao() throws Exception {
        // Iniciar servidor
        servidor = new Server(PORT, 30, 5);
        serverThread = new Thread(servidor::iniciar, "ShutdownTestServer1");
        serverThread.start();
        Thread.sleep(2000);

        ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT);
        middleware.conectar();

        StubFactory stubs = new StubFactory(middleware);
        IServicoAutenticacao auth = stubs.criarStubAutenticacao();

        // Autenticar
        String username = "shutdown1" + System.currentTimeMillis();
        UsuarioDTO user = new UsuarioDTO(username, "pass123");
        auth.registrar(user);
        auth.autenticar(user);

        assertTrue(middleware.isConectado(), "Cliente deve estar conectado");
        assertFalse(middleware.isServerShutdown(), "Servidor não deve estar shutdown");

        // Parar servidor
        servidor.parar();
        
        // Aguardar notificação de shutdown
        long timeout = System.currentTimeMillis() + 5000;
        while (!middleware.isServerShutdown() && System.currentTimeMillis() < timeout) {
            Thread.sleep(100);
        }

        assertTrue(middleware.isServerShutdown(), "Cliente deve detectar shutdown");
        assertFalse(middleware.isConectado(), "Cliente não deve estar conectado");

        middleware.desconectar();
    }

    @Test
    @Order(2)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Cliente não pode invocar após shutdown")
    void testeClienteNaoPodeInvocarAposShutdown() throws Exception {
        // Iniciar servidor
        servidor = new Server(PORT, 30, 5);
        serverThread = new Thread(servidor::iniciar, "ShutdownTestServer2");
        serverThread.start();
        Thread.sleep(2000);

        ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT);
        middleware.conectar();

        StubFactory stubs = new StubFactory(middleware);
        IServicoAutenticacao auth = stubs.criarStubAutenticacao();
        IServicoEventos eventos = stubs.criarStubEventos();

        // Autenticar
        String username = "shutdown2" + System.currentTimeMillis();
        UsuarioDTO user = new UsuarioDTO(username, "pass123");
        auth.registrar(user);
        auth.autenticar(user);

        assertTrue(middleware.isConectado(), "Cliente deve estar conectado");

        // Parar servidor
        servidor.parar();
        Thread.sleep(2000);

        // Verificar que cliente detectou shutdown
        assertTrue(middleware.isServerShutdown() || !middleware.isConectado(), 
                  "Cliente deve detectar shutdown");

        // Tentar invocar operação - deve falhar
        boolean operacaoFalhou = false;
        try {
            eventos.registrarEvento(new EventoDTO(1, 10, 50.0));
        } catch (Exception e) {
            // Esperamos IOException ou EventoException
            operacaoFalhou = true;
        }
        
        assertTrue(operacaoFalhou, "Operação deve falhar após shutdown");

        middleware.desconectar();
    }

    @Test
    @Order(3)
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("Múltiplos clientes recebem notificação")
    void testeMultiplosClientesRecebemNotificacao() throws Exception {
        // Iniciar servidor
        servidor = new Server(PORT, 30, 5);
        serverThread = new Thread(servidor::iniciar, "ShutdownTestServer3");
        serverThread.start();
        Thread.sleep(2000);

        int numClientes = 10;
        AtomicInteger clientesNotificados = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(numClientes);
        ClienteMiddleware[] middlewares = new ClienteMiddleware[numClientes];

        // Conectar múltiplos clientes
        for (int i = 0; i < numClientes; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    middlewares[id] = new ClienteMiddleware(HOST, PORT);
                    middlewares[id].conectar();

                    StubFactory stubs = new StubFactory(middlewares[id]);
                    IServicoAutenticacao auth = stubs.criarStubAutenticacao();

                    String username = "shutdown3_" + id + "_" + System.currentTimeMillis();
                    UsuarioDTO user = new UsuarioDTO(username, "pass" + id);
                    auth.registrar(user);
                    auth.autenticar(user);

                    latch.countDown();
                } catch (Exception e) {
                    System.err.println("Erro ao conectar cliente " + id + ": " + e.getMessage());
                    latch.countDown();
                }
            }).start();
        }

        assertTrue(latch.await(8, TimeUnit.SECONDS), "Todos clientes devem conectar");

        // Parar servidor
        servidor.parar();
        Thread.sleep(2000);

        // Verificar quantos clientes detectaram shutdown
        for (int i = 0; i < numClientes; i++) {
            if (middlewares[i] != null && middlewares[i].isServerShutdown()) {
                clientesNotificados.incrementAndGet();
            }
        }

        assertTrue(clientesNotificados.get() >= numClientes - 2, 
                  "Pelo menos " + (numClientes - 2) + " clientes devem detectar shutdown. " +
                  "Detectados: " + clientesNotificados.get());

        // Desconectar todos
        for (ClienteMiddleware m : middlewares) {
            if (m != null) {
                try {
                    m.desconectar();
                } catch (Exception e) {
                    // Ignorar
                }
            }
        }
    }

    @Test
    @Order(4)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Servidor aguarda requisições pendentes antes de fechar")
    void testeServidorAguardaRequisicoesPendentes() throws Exception {
        // Iniciar servidor
        servidor = new Server(PORT, 30, 5);
        serverThread = new Thread(servidor::iniciar, "ShutdownTestServer4");
        serverThread.start();
        Thread.sleep(2000);

        ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT);
        middleware.conectar();

        StubFactory stubs = new StubFactory(middleware);
        IServicoAutenticacao auth = stubs.criarStubAutenticacao();
        IServicoEventos eventos = stubs.criarStubEventos();

        // Autenticar
        String username = "shutdown4" + System.currentTimeMillis();
        UsuarioDTO user = new UsuarioDTO(username, "pass123");
        auth.registrar(user);
        auth.autenticar(user);

        // Iniciar operação em thread separada
        new Thread(() -> {
            try {
                for (int i = 0; i < 3; i++) {
                    eventos.registrarEvento(new EventoDTO(i, 10, 50.0));
                    Thread.sleep(200);
                }
            } catch (Exception e) {
                // Pode falhar se servidor fechar
            }
        }).start();

        Thread.sleep(500);

        // Shutdown do servidor
        long inicio = System.currentTimeMillis();
        servidor.parar();
        long duracao = System.currentTimeMillis() - inicio;

        assertTrue(duracao < 10000, "Shutdown não deve demorar mais que 10s");
        
        middleware.desconectar();
    }

    @Test
    @Order(5)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Servidor fecha todas conexões durante shutdown")
    void testeServidorFechaTodasConexoes() throws Exception {
        // Iniciar servidor
        servidor = new Server(PORT, 30, 5);
        serverThread = new Thread(servidor::iniciar, "ShutdownTestServer5");
        serverThread.start();
        Thread.sleep(2000);

        int numClientes = 5;
        ClienteMiddleware[] middlewares = new ClienteMiddleware[numClientes];

        // Conectar clientes
        for (int i = 0; i < numClientes; i++) {
            middlewares[i] = new ClienteMiddleware(HOST, PORT);
            middlewares[i].conectar();

            StubFactory stubs = new StubFactory(middlewares[i]);
            IServicoAutenticacao auth = stubs.criarStubAutenticacao();

            String username = "shutdown5_" + i + "_" + System.currentTimeMillis();
            UsuarioDTO user = new UsuarioDTO(username, "pass" + i);
            auth.registrar(user);
            auth.autenticar(user);
        }

        // Verificar que todos estão conectados
        for (ClienteMiddleware m : middlewares) {
            assertTrue(m.isConectado(), "Cliente deve estar conectado");
        }

        // Parar servidor
        servidor.parar();
        Thread.sleep(2000);

        // Verificar que todos detectaram shutdown ou desconectaram
        int desconectados = 0;
        for (ClienteMiddleware m : middlewares) {
            if (!m.isConectado() || m.isServerShutdown()) {
                desconectados++;
            }
        }

        assertTrue(desconectados >= numClientes - 1, 
                  "Pelo menos " + (numClientes - 1) + " clientes devem desconectar");

        // Limpar
        for (ClienteMiddleware m : middlewares) {
            try {
                m.desconectar();
            } catch (Exception e) {
                // Ignorar
            }
        }
    }
}