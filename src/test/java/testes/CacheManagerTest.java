package testes;

import org.junit.jupiter.api.*;

import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import server.business.domain.Agregacao;
import server.business.domain.Evento;
import server.data.cache.CacheManager;
import server.data.repository.EventoFileRepository;
import server.data.repository.IEventoRepository;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para CacheManager
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CacheManagerTest {

    private static final String TEST_DATA_DIR = "dados_teste_cache";

    @BeforeAll
    void prepararDados() {
        File dir = new File(TEST_DATA_DIR);
        if (dir.exists()) {
            deleteDirectory(dir);
        }
        dir.mkdirs();

        IEventoRepository repo = new EventoFileRepository(TEST_DATA_DIR);

        for (int dia = 0; dia < 10; dia++) {
            Map<Integer, List<Evento>> eventos = new HashMap<>();

            for (int produto = 1; produto <= 5; produto++) {
                List<Evento> lista = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    lista.add(new Evento(produto, 10, 50.0 + i));
                }
                eventos.put(produto, lista);
            }

            repo.salvarEventosDia(dia, eventos);
        }
    }

    @AfterAll
    void limparDados() {
        deleteDirectory(new File(TEST_DATA_DIR));
    }

    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        dir.delete();
    }

    @Test
    void testeCacheHit() {
        IEventoRepository repo = new EventoFileRepository(TEST_DATA_DIR);
        CacheManager cache = new CacheManager(repo, 5);

        Agregacao agg1 = cache.obterAgregacaoDia(1, 0);
        Agregacao agg2 = cache.obterAgregacaoDia(1, 0);

        assertNotNull(agg1);
        assertNotNull(agg2);
        assertEquals(
                agg1.getQuantidadeVendas(),
                agg2.getQuantidadeVendas(),
                "Cache hit deve devolver a mesma agregação"
        );
    }

    @Test
    void testeCacheMiss() {
        IEventoRepository repo = new EventoFileRepository(TEST_DATA_DIR);
        CacheManager cache = new CacheManager(repo, 5);

        Agregacao agg = cache.obterAgregacaoDia(1, 0);

        assertNotNull(agg);
        assertEquals(30, agg.getQuantidadeVendas());
    }

    @Test
    void testeLRU() {
        IEventoRepository repo = new EventoFileRepository(TEST_DATA_DIR);
        CacheManager cache = new CacheManager(repo, 3);

        for (int dia = 0; dia < 5; dia++) {
            cache.obterAgregacaoDia(1, dia);
        }

        String stats = cache.obterEstatisticas();

        assertTrue(stats.contains("3"), "LRU deve limitar o número de séries em memória");
    }

    @Test
    void testeLimpezaDia() {
        IEventoRepository repo = new EventoFileRepository(TEST_DATA_DIR);
        CacheManager cache = new CacheManager(repo, 5);

        Agregacao agg1 = cache.obterAgregacaoDia(1, 0);
        assertNotNull(agg1);

        cache.limparDia(0);

        Agregacao agg2 = cache.obterAgregacaoDia(1, 0);
        assertNotNull(agg2);
        assertEquals(
                agg1.getQuantidadeVendas(),
                agg2.getQuantidadeVendas(),
                "Após limpeza, dados devem ser recarregados corretamente"
        );
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testeConcorrencia() throws InterruptedException {
        IEventoRepository repo = new EventoFileRepository(TEST_DATA_DIR);
        CacheManager cache = new CacheManager(repo, 5);

        int numThreads = 20;
        CountDownLatch latch = new CountDownLatch(numThreads);

        boolean[] ok = new boolean[numThreads];

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    boolean localOk = true;
                    for (int dia = 0; dia < 5; dia++) {
                        Agregacao agg = cache.obterAgregacaoDia(threadId % 5 + 1, dia);
                        if (agg == null || agg.getQuantidadeVendas() != 30) {
                            localOk = false;
                            break;
                        }
                    }
                    ok[threadId] = localOk;
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue(latch.await(8, TimeUnit.SECONDS));

        int sucessos = 0;
        for (int i = 0; i < numThreads; i++) if (ok[i]) sucessos++;

        assertEquals(numThreads, sucessos, "Todas as threads devem conseguir 5 leituras corretas.");
    }

    @Test
    void testeEstatisticas() {
        IEventoRepository repo = new EventoFileRepository(TEST_DATA_DIR);
        CacheManager cache = new CacheManager(repo, 5);

        for (int dia = 0; dia < 3; dia++) {
            for (int produto = 1; produto <= 2; produto++) {
                cache.obterAgregacaoDia(produto, dia);
            }
        }

        String stats = cache.obterEstatisticas();

        assertAll(
                () -> assertTrue(stats.contains("Cache")),
                () -> assertTrue(stats.contains("produtos")),
                () -> assertTrue(stats.contains("agregações")),
                () -> assertTrue(stats.contains("séries"))
        );
    }
}