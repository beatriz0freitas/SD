package testes;

import client.ClienteMiddleware;
import client.stub.StubFactory;
import common.dto.*;
import common.interfaces.*;
import server.Server;

import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
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
        assertTrue(reg.isSucesso(), 
            "Registo deve ter sucesso. Erro: " + reg.getMensagem());
    
        RespostaDTO login = auth.autenticar(user);
        assertTrue(login.isSucesso(), 
            "Autenticação deve ter sucesso. Erro: " + login.getMensagem());
    
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
    
                    // SEM underscore - apenas números
                    String username = "concurrent" + id + System.nanoTime();
                    UsuarioDTO user = new UsuarioDTO(username, "pass" + id);
                    
                    RespostaDTO reg = auth.registrar(user);
                    if (!reg.isSucesso()) {
                        System.err.println("Cliente " + id + " registo falhou: " + 
                                         reg.getMensagem());
                        return;
                    }
                    
                    RespostaDTO login = auth.autenticar(user);
                    if (login.isSucesso()) {
                        for (int j = 0; j < 3; j++) {
                            eventos.registrarEvento(new EventoDTO(id, 1, 10.0));
                        }
                        sucesso.incrementAndGet();
                    }
    
                    middleware.desconectar();
                } catch (Exception e) {
                    System.err.println("Cliente " + id + " exceção: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            }, "Client-" + i).start();
        }
    
        assertTrue(latch.await(15, TimeUnit.SECONDS));
        assertEquals(10, sucesso.get(), 
            "Todos os clientes devem completar com sucesso. Sucessos: " + 
            sucesso.get());
    }

    @Test
    @Order(5)
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Teste completo de filtro de eventos")
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

        // Registar eventos de múltiplos produtos
        eventos.registrarEvento(new EventoDTO(1, 10, 50.0));
        eventos.registrarEvento(new EventoDTO(2, 20, 60.0));
        eventos.registrarEvento(new EventoDTO(3, 30, 70.0));
        eventos.registrarEvento(new EventoDTO(4, 40, 80.0));

        // Avançar dia
        eventos.novoDia();

        // Filtrar apenas produtos 1 e 3
        Set<Integer> produtosFiltro = new HashSet<>(Arrays.asList(1, 3));
        FiltrarEventosDTO filtro = new FiltrarEventosDTO(produtosFiltro, 1);

        RespostaDTO resposta = eventos.filtrarEventos(filtro);
        assertTrue(resposta.isSucesso());

        EventosFiltradosDTO resultado = (EventosFiltradosDTO) resposta.getDados();
        assertEquals(2, resultado.getEventosPorProduto().size());
        assertTrue(resultado.getEventosPorProduto().containsKey(1));
        assertTrue(resultado.getEventosPorProduto().containsKey(3));
        assertFalse(resultado.getEventosPorProduto().containsKey(2));
        assertFalse(resultado.getEventosPorProduto().containsKey(4));

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
    
        AtomicInteger notificado = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
    
        Thread t = new Thread(() -> {
            try {
                // Esta operação vai bloquear até ambos produtos serem vendidos
                RespostaDTO r = eventos.notificarVendaEspecifica(
                    new NotificacaoDTO(10, 11)
                );
                if (r.isSucesso()) {
                    notificado.set(1);
                }
            } catch (Exception e) {
                System.err.println("Erro na notificação: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
        
        t.start();
        
        // Aguardar thread iniciar
        Thread.sleep(500);
        
        // Registrar os eventos que vão satisfazer a notificação
        eventos.registrarEvento(new EventoDTO(10, 1, 100.0));
        Thread.sleep(200);
        eventos.registrarEvento(new EventoDTO(11, 1, 100.0));
    
        assertTrue(latch.await(8, TimeUnit.SECONDS), "Notificação deve ser recebida");
        assertEquals(1, notificado.get(), "Notificação deve ter sucesso");
    
        middleware.desconectar();
    }
}
