package testes;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
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
import common.dto.FiltrarEventosDTO;
import common.dto.NotificacaoDTO;
import common.dto.RespostaDTO;
import common.dto.UsuarioDTO;
import common.interfaces.IServicoAgregacoes;
import common.interfaces.IServicoAutenticacao;
import common.interfaces.IServicoEventos;
import server.Server;

/**
 * Testes de integração do sistema completo
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTest {

    private static final String HOST = "localhost";
    private static final int PORT = 5555;

    private Server servidor;
    private Thread serverThread;

    @BeforeAll
    void iniciarServidor() throws InterruptedException {
        servidor = new Server(PORT, 30, 5);
        serverThread = new Thread(servidor::iniciar, "TestServer");
        serverThread.start();

        Thread.sleep(1500);
    }

    @AfterAll
    void pararServidor() throws InterruptedException {
        if (servidor != null) servidor.parar();
        if (serverThread != null) serverThread.join(5000);
    }

    @Test
    @Order(1)
    void testeAutenticacaoBasica() throws Exception {
        ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT);
        middleware.conectar();

        StubFactory stubs = new StubFactory(middleware);
        IServicoAutenticacao auth = stubs.criarStubAutenticacao();

        String username = "testuser1" + System.currentTimeMillis();
        UsuarioDTO user = new UsuarioDTO(username, "password123");

        RespostaDTO reg = auth.registrar(user);
        assertTrue(reg.isSucesso(), "Registo deve ter sucesso. Erro: " + reg.getMensagem());

        RespostaDTO login = auth.autenticar(user);
        assertTrue(login.isSucesso(), "Autenticação deve ter sucesso. Erro: " + login.getMensagem());

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

        String username = "testuser2" + System.currentTimeMillis();
        UsuarioDTO user = new UsuarioDTO(username, "password123");
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

        String username = "testuser3" + System.currentTimeMillis();
        UsuarioDTO user = new UsuarioDTO(username, "password123");
        auth.registrar(user);
        auth.autenticar(user);

        for (int i = 0; i < 3; i++) {
            eventos.registrarEvento(new EventoDTO(2, 5, 100.0));
        }

        eventos.novoDia();

        RespostaDTO resp = aggs.obterQuantidadeVendas(2, 1);
        assertTrue(resp.isSucesso(), resp.getMensagem());

        assertTrue(
                resp.getMensagem().contains("15"),
                "Mensagem deve conter o resultado esperado (15). Mensagem: " + resp.getMensagem()
        );

        middleware.desconectar();
    }

    @Test
    @Order(4)
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testeClientesConcorrentes() throws InterruptedException {
        int clientes = 10;
        CountDownLatch latch = new CountDownLatch(clientes);

        boolean[] ok = new boolean[clientes];

        for (int i = 0; i < clientes; i++) {
            final int id = i;

            new Thread(() -> {
                ClienteMiddleware middleware = null;
                try {
                    middleware = new ClienteMiddleware(HOST, PORT);
                    middleware.conectar();

                    StubFactory stubs = new StubFactory(middleware);
                    IServicoAutenticacao auth = stubs.criarStubAutenticacao();
                    IServicoEventos eventos = stubs.criarStubEventos();

                    String username = "concurrent" + id + System.nanoTime();
                    UsuarioDTO user = new UsuarioDTO(username, "pass" + id);

                    RespostaDTO reg = auth.registrar(user);
                    if (reg == null || !reg.isSucesso()) return;

                    RespostaDTO login = auth.autenticar(user);
                    if (login != null && login.isSucesso()) {
                        for (int j = 0; j < 3; j++) {
                            eventos.registrarEvento(new EventoDTO(id + 1, 1, 10.0));
                        }
                        ok[id] = true;
                    }

                } catch (Exception ignored) {
                } finally {
                    try {
                        if (middleware != null) middleware.desconectar();
                    } catch (Exception ignored) {
                    }
                    latch.countDown();
                }
            }, "Client-" + i).start();
        }

        assertTrue(latch.await(15, TimeUnit.SECONDS));

        int sucessos = 0;
        for (int i = 0; i < clientes; i++) if (ok[i]) sucessos++;

        assertEquals(clientes, sucessos, "Todos os clientes devem completar com sucesso.");
    }

    @Test
    @Order(5)
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Teste completo de filtro de eventos (validar mensagem de resumo)")
    void testeFiltrarEventosCompleto() throws Exception {
        ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT);
        middleware.conectar();

        StubFactory stubs = new StubFactory(middleware);
        IServicoAutenticacao auth = stubs.criarStubAutenticacao();
        IServicoEventos eventos = stubs.criarStubEventos();

        String username = "filtrotest" + System.currentTimeMillis();
        UsuarioDTO user = new UsuarioDTO(username, "pass123");
        auth.registrar(user);
        auth.autenticar(user);

        // Registar eventos (dia atual)
        eventos.registrarEvento(new EventoDTO(1, 10, 50.0));
        eventos.registrarEvento(new EventoDTO(2, 20, 60.0));
        eventos.registrarEvento(new EventoDTO(3, 30, 70.0));
        eventos.registrarEvento(new EventoDTO(4, 40, 80.0));

        // Avançar dia (os eventos ficam no dia anterior)
        eventos.novoDia();

        Set<Integer> produtosFiltro = new HashSet<>(Arrays.asList(1, 3));
        FiltrarEventosDTO filtro = new FiltrarEventosDTO(produtosFiltro, 1);

        RespostaDTO resposta = eventos.filtrarEventos(filtro);
        assertTrue(resposta.isSucesso(), resposta.getMensagem());

        String msg = resposta.getMensagem();
        assertTrue(msg.contains("Eventos filtrados"), "Mensagem deve indicar filtro. Msg: " + msg);
        assertTrue(msg.contains("produtos=2"), "Mensagem deve indicar 2 produtos filtrados. Msg: " + msg);

        middleware.desconectar();
    }

    @Test
    @Order(6)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testeNotificacoes() throws Exception {
        ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT);
        middleware.conectar();

        StubFactory stubs = new StubFactory(middleware);
        IServicoAutenticacao auth = stubs.criarStubAutenticacao();
        IServicoEventos eventos = stubs.criarStubEventos();

        String username = "notiftest" + System.currentTimeMillis();
        UsuarioDTO user = new UsuarioDTO(username, "pass123");
        auth.registrar(user);
        auth.autenticar(user);

        final boolean[] notificado = new boolean[] { false };
        CountDownLatch latch = new CountDownLatch(1);

        Thread t = new Thread(() -> {
            try {
                RespostaDTO r = eventos.notificarVendaEspecifica(new NotificacaoDTO(10, 11));
                notificado[0] = (r != null && r.isSucesso());
            } catch (Exception ignored) {
                notificado[0] = false;
            } finally {
                latch.countDown();
            }
        });

        t.start();

        Thread.sleep(500);

        eventos.registrarEvento(new EventoDTO(10, 1, 100.0));
        Thread.sleep(200);
        eventos.registrarEvento(new EventoDTO(11, 1, 100.0));

        assertTrue(latch.await(8, TimeUnit.SECONDS), "Notificação deve ser recebida");
        assertTrue(notificado[0], "Notificação deve ter sucesso");

        middleware.desconectar();
    }
}