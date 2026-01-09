package testes;

import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import server.business.domain.Usuario;
import server.data.repository.EventoFileRepository;
import server.data.repository.IEventoRepository;
import server.data.repository.IUsuarioRepository;
import server.data.repository.UsuarioFileRepository;

class RepositoryTest {

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
        
        int baseline = usuarioRepo.contarUtilizadores();

        for (int i = 0; i < 5; i++) {
            usuarioRepo.salvar(new Usuario("user" + i + "_" + System.nanoTime(), "hash" + i));
        }

        List<Usuario> usuariosDepois = usuarioRepo.listarTodos();
        int countDepois = usuarioRepo.contarUtilizadores();

        assertTrue(usuariosDepois.size() >= baseline + 5, "Deve ter pelo menos +5 utilizadores");
        assertEquals(baseline + 5, countDepois,
                "Contador deve aumentar exatamente 5 a partir do baseline");
    }

    @Test
    void testUsuarioConcorrencia() throws InterruptedException {
        int numThreads = 20;
        CountDownLatch latch = new CountDownLatch(numThreads);

        boolean[] ok = new boolean[numThreads];

        for (int i = 0; i < numThreads; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    String uname = "concurrent" + id + "_" + System.nanoTime();
                    Usuario u = new Usuario(uname, "hash" + id);
                    usuarioRepo.salvar(u);

                    ok[id] = (usuarioRepo.buscar(uname) != null);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        int sucessos = 0;
        for (int i = 0; i < numThreads; i++) {
            if (ok[i]) sucessos++;
        }

        assertEquals(numThreads, sucessos);
    }

    

    private static IUsuarioRepository criarUsuarioRepoTeste() {
        return new UsuarioFileRepository();
    }

    private static IEventoRepository criarEventoRepoTeste() {
        File dir = new File(TEST_EVENTO_DIR);
        dir.mkdirs();
        return new EventoFileRepository(TEST_EVENTO_DIR);
    }

    private static void limparDadosTeste() {
        
        File eventDir = new File(TEST_EVENTO_DIR);
        deleteDirectory(eventDir);
    }

    private static void deleteDirectory(File dir) {
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) deleteDirectory(f);
            }
        }
        if (dir.exists()) dir.delete();
    }
}