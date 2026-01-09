package testes;

import org.junit.jupiter.api.*;

import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import server.business.domain.Agregacao;
import server.business.domain.Evento;
import server.business.domain.Usuario;
import server.data.repository.*;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryTest {

    private static final String TEST_USUARIO_DIR = "dados/utilizadores.dat";
    private static final String TEST_EVENTO_DIR = "dados_teste_eventos";

    private IUsuarioRepository usuarioRepo;
    private IEventoRepository eventoRepo;

    @BeforeEach
    void setup() {
        limparDadosTeste();

        usuarioRepo = criarUsuarioRepoTeste();
        eventoRepo = criarEventoRepoTeste();
    }

    @AfterEach
    void cleanup() {
        limparDadosTeste();
    }

    @Test
    void testUsuarioSalvarCarregar() {
        Usuario usuario = new Usuario("testuser", "hash123");
        usuarioRepo.salvar(usuario);

        Usuario carregado = usuarioRepo.buscar("testuser");

        assertNotNull(carregado);
        assertEquals("testuser", carregado.getUsername());
        assertEquals("hash123", carregado.getPasswordHash());
    }

    @Test
    void testUsuarioExiste() {
        usuarioRepo.salvar(new Usuario("existeuser", "hash456"));

        assertTrue(usuarioRepo.existe("existeuser"));
        assertFalse(usuarioRepo.existe("naoexiste"));
    }

    @Test
    void testUsuarioListar() {
        // NÃO assumir 0: o repositório pode carregar utilizadores persistidos do disco.
        int baselineCount = usuarioRepo.contarUtilizadores();
        int baselineListSize = usuarioRepo.listarTodos().size();

        for (int i = 0; i < 5; i++) {
            usuarioRepo.salvar(new Usuario("user" + i + "_" + System.nanoTime(), "hash" + i));
        }

        List<Usuario> usuariosDepois = usuarioRepo.listarTodos();
        int countDepois = usuarioRepo.contarUtilizadores();

        assertEquals(baselineListSize + 5, usuariosDepois.size(),
                "listarTodos() deve crescer 5 em relação ao baseline");
        assertEquals(baselineCount + 5, countDepois,
                "contarUtilizadores() deve crescer 5 em relação ao baseline");
    }

    @Test
    void testUsuarioConcorrencia() throws InterruptedException {
        int numThreads = 20;
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger sucessos = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    Usuario u = new Usuario("concurrent" + id + "_" + System.nanoTime(), "hash" + id);
                    usuarioRepo.salvar(u);

                    if (usuarioRepo.buscar(u.getUsername()) != null) {
                        sucessos.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(numThreads, sucessos.get());
    }

    @Test
    void testEventoSalvarCarregar() {
        Map<Integer, List<Evento>> eventos = new HashMap<>();

        eventos.put(1, List.of(
                new Evento(1, 10, 50.0),
                new Evento(1, 20, 60.0),
                new Evento(1, 30, 70.0)
        ));

        eventos.put(2, List.of(
                new Evento(2, 5, 100.0),
                new Evento(2, 15, 110.0)
        ));

        eventoRepo.salvarEventosDia(0, eventos);

        Map<Integer, List<Evento>> carregados = eventoRepo.carregarEventosDia(0);

        assertEquals(2, carregados.size());
        assertEquals(3, carregados.get(1).size());
        assertEquals(2, carregados.get(2).size());
    }

    @Test
    void testEventoAgregar() {
        Map<Integer, List<Evento>> eventos = new HashMap<>();
        eventos.put(1, List.of(
                new Evento(1, 10, 50.0),
                new Evento(1, 20, 60.0)
        ));

        eventoRepo.salvarEventosDia(1, eventos);

        Agregacao agg = eventoRepo.agregarEventosDia(1, 1);

        assertEquals(30, agg.getQuantidadeVendas());
        assertEquals(1700.0, agg.getVolumeVendas());
    }

    @Test
    void testEventoUltimoDia() {
        for (int dia = 0; dia <= 2; dia++) {
            Map<Integer, List<Evento>> eventos = new HashMap<>();
            eventos.put(1, List.of(new Evento(1, 1, 1.0)));
            eventoRepo.salvarEventosDia(dia, eventos);
        }

        assertEquals(2, eventoRepo.obterUltimoDia());
    }

    @Test
    void testEventoConcorrencia() throws InterruptedException {
        int numThreads = 10;
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger sucessos = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            final int dia = i;
            new Thread(() -> {
                try {
                    Map<Integer, List<Evento>> eventos = new HashMap<>();
                    eventos.put(1, List.of(new Evento(1, 10, 50.0)));

                    eventoRepo.salvarEventosDia(dia, eventos);

                    if (!eventoRepo.carregarEventosDia(dia).isEmpty()) {
                        sucessos.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(numThreads, sucessos.get());
    }

    // ================= UTILITÁRIOS =================

    private static IUsuarioRepository criarUsuarioRepoTeste() {
        return new UsuarioFileRepository();
    }

    private static IEventoRepository criarEventoRepoTeste() {
        File dir = new File(TEST_EVENTO_DIR);
        dir.mkdirs();
        return new EventoFileRepository(TEST_EVENTO_DIR);
    }

    private static void limparDadosTeste() {
        File userFile = new File(TEST_USUARIO_DIR);
        if (userFile.exists()) {
            userFile.delete();
            System.out.println("Arquivo de usuários de teste deletado");
        }

        File eventDir = new File(TEST_EVENTO_DIR);
        deleteDirectory(eventDir);
    }

    private static void deleteDirectory(File dir) {
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteDirectory(f);
                }
            }
        }
        if (dir.exists()) {
            dir.delete();
        }
    }
}