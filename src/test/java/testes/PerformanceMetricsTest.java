package testes;

import org.junit.jupiter.api.*;

import common.PerformanceMetrics;
import common.PerformanceMetrics.MetricsSnapshot;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite completa de testes para PerformanceMetrics
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PerformanceMetricsTest {

    private PerformanceMetrics metrics;

    @BeforeEach
    void setup() {
        metrics = PerformanceMetrics.getInstance();
        metrics.reset();
    }

    @Test
    @Order(1)
    @DisplayName("Contadores incrementam corretamente")
    void testeContadoresIncrementam() {
        // Registrar requisições com sucesso
        metrics.recordRequest(true, 1_000_000); // 1ms
        metrics.recordRequest(true, 2_000_000); // 2ms
        metrics.recordRequest(true, 3_000_000); // 3ms

        MetricsSnapshot snapshot = metrics.getSnapshot();

        assertEquals(3, snapshot.totalRequests, "Total de requisições deve ser 3");
        assertEquals(0, snapshot.totalErrors, "Não deve ter erros");
        assertEquals(0.0, snapshot.errorRate, 0.01, "Taxa de erro deve ser 0%");
    }

    @Test
    @Order(2)
    @DisplayName("Contadores de erro funcionam corretamente")
    void testeContadoresErro() {
        // 7 sucessos, 3 erros
        for (int i = 0; i < 7; i++) {
            metrics.recordRequest(true, 1_000_000);
        }
        for (int i = 0; i < 3; i++) {
            metrics.recordRequest(false, 1_000_000);
        }

        MetricsSnapshot snapshot = metrics.getSnapshot();

        assertEquals(10, snapshot.totalRequests, "Total deve ser 10");
        assertEquals(3, snapshot.totalErrors, "Deve ter 3 erros");
        assertEquals(30.0, snapshot.errorRate, 0.01, "Taxa de erro deve ser 30%");
    }

    @Test
    @Order(3)
    @DisplayName("Latências calculadas corretamente")
    void testeLatencias() {
        // Latências: 1ms, 2ms, 3ms, 4ms, 5ms
        metrics.recordRequest(true, 1_000_000);  // 1ms
        metrics.recordRequest(true, 2_000_000);  // 2ms
        metrics.recordRequest(true, 3_000_000);  // 3ms
        metrics.recordRequest(true, 4_000_000);  // 4ms
        metrics.recordRequest(true, 5_000_000);  // 5ms

        MetricsSnapshot snapshot = metrics.getSnapshot();

        assertEquals(3.0, snapshot.avgLatencyMs, 0.01, "Latência média deve ser 3ms");
        assertEquals(1.0, snapshot.minLatencyMs, 0.01, "Latência mínima deve ser 1ms");
        assertEquals(5.0, snapshot.maxLatencyMs, 0.01, "Latência máxima deve ser 5ms");
    }

    @Test
    @Order(4)
    @DisplayName("Latências com valores extremos")
    void testeLatenciasExtremas() {
        // Latência muito baixa e muito alta
        metrics.recordRequest(true, 100_000);      // 0.1ms
        metrics.recordRequest(true, 1_000_000_000); // 1000ms

        MetricsSnapshot snapshot = metrics.getSnapshot();

        assertTrue(snapshot.minLatencyMs < 0.2, "Latência mínima deve ser ~0.1ms");
        assertTrue(snapshot.maxLatencyMs > 999, "Latência máxima deve ser ~1000ms");
        assertTrue(snapshot.avgLatencyMs > 400 && snapshot.avgLatencyMs < 600, 
                  "Latência média deve estar entre 400-600ms");
    }

    @Test
    @Order(5)
    @DisplayName("Cache hit rate calculado corretamente")
    void testeCacheHitRate() {
        // 7 hits, 3 misses = 70% hit rate
        for (int i = 0; i < 7; i++) {
            metrics.recordCacheHit();
        }
        for (int i = 0; i < 3; i++) {
            metrics.recordCacheMiss();
        }

        MetricsSnapshot snapshot = metrics.getSnapshot();

        assertEquals(7, snapshot.cacheHits, "Deve ter 7 cache hits");
        assertEquals(3, snapshot.cacheMisses, "Deve ter 3 cache misses");
        assertEquals(70.0, snapshot.cacheHitRate, 0.01, "Hit rate deve ser 70%");
    }

    @Test
    @Order(6)
    @DisplayName("Cache hit rate com 100% hits")
    void testeCacheHitRate100Porcento() {
        for (int i = 0; i < 10; i++) {
            metrics.recordCacheHit();
        }

        MetricsSnapshot snapshot = metrics.getSnapshot();

        assertEquals(100.0, snapshot.cacheHitRate, 0.01, "Hit rate deve ser 100%");
    }

    @Test
    @Order(7)
    @DisplayName("Cache hit rate com 0% hits")
    void testeCacheHitRate0Porcento() {
        for (int i = 0; i < 10; i++) {
            metrics.recordCacheMiss();
        }

        MetricsSnapshot snapshot = metrics.getSnapshot();

        assertEquals(0.0, snapshot.cacheHitRate, 0.01, "Hit rate deve ser 0%");
    }

    @Test
    @Order(8)
    @DisplayName("Throughput calculado corretamente")
    void testeThroughput() throws InterruptedException {
        // Registrar requisições ao longo do tempo
        long inicio = System.currentTimeMillis();
        
        for (int i = 0; i < 100; i++) {
            metrics.recordRequest(true, 1_000_000);
            Thread.sleep(5); // 5ms entre requisições = 500ms total
        }

        long duracao = System.currentTimeMillis() - inicio;
        MetricsSnapshot snapshot = metrics.getSnapshot();

        // Throughput = requisições / tempo em segundos
        // 100 requisições em ~0.5s = ~200 req/s
        // Mas depende do uptime desde reset, então verificamos que é razoável
        assertTrue(snapshot.throughput > 1, 
                  "Throughput deve ser > 1 req/s, foi: " + snapshot.throughput);
        
        // Verificar que número de requisições está correto
        assertEquals(100, snapshot.totalRequests, "Deve ter 100 requisições");
    }

    @Test
    @Order(9)
    @DisplayName("Reset limpa todas as métricas")
    void testeReset() {
        // Adicionar algumas métricas
        metrics.recordRequest(true, 1_000_000);
        metrics.recordRequest(false, 2_000_000);
        metrics.recordCacheHit();
        metrics.recordCacheMiss();

        MetricsSnapshot antes = metrics.getSnapshot();
        assertTrue(antes.totalRequests > 0, "Deve ter requisições antes do reset");

        // Reset
        metrics.reset();

        MetricsSnapshot depois = metrics.getSnapshot();
        assertEquals(0, depois.totalRequests, "Requisições devem ser 0 após reset");
        assertEquals(0, depois.totalErrors, "Erros devem ser 0 após reset");
        assertEquals(0, depois.cacheHits, "Cache hits devem ser 0 após reset");
        assertEquals(0, depois.cacheMisses, "Cache misses devem ser 0 após reset");
    }

    @Test
    @Order(10)
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Thread-safety - múltiplas threads registrando métricas")
    void testeThreadSafety() throws InterruptedException {
        int numThreads = 20;
        int opsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++) {
            new Thread(() -> {
                try {
                    for (int j = 0; j < opsPerThread; j++) {
                        metrics.recordRequest(j % 2 == 0, 1_000_000);
                        metrics.recordCacheHit();
                        metrics.recordCacheMiss();
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue(latch.await(8, TimeUnit.SECONDS), "Todas threads devem completar");

        MetricsSnapshot snapshot = metrics.getSnapshot();

        int expectedRequests = numThreads * opsPerThread;
        assertEquals(expectedRequests, snapshot.totalRequests, 
                    "Total de requisições deve ser " + expectedRequests);
        
        int expectedCacheOps = numThreads * opsPerThread;
        assertEquals(expectedCacheOps, snapshot.cacheHits, 
                    "Cache hits deve ser " + expectedCacheOps);
        assertEquals(expectedCacheOps, snapshot.cacheMisses, 
                    "Cache misses deve ser " + expectedCacheOps);
    }

    @Test
    @Order(11)
    @DisplayName("Snapshot é imutável")
    void testeSnapshotImutavel() {
        metrics.recordRequest(true, 1_000_000);
        
        MetricsSnapshot snapshot1 = metrics.getSnapshot();
        long requests1 = snapshot1.totalRequests;

        // Adicionar mais requisições
        metrics.recordRequest(true, 1_000_000);

        // Snapshot anterior não deve mudar
        assertEquals(requests1, snapshot1.totalRequests, 
                    "Snapshot deve ser imutável");

        // Novo snapshot deve refletir mudanças
        MetricsSnapshot snapshot2 = metrics.getSnapshot();
        assertEquals(requests1 + 1, snapshot2.totalRequests, 
                    "Novo snapshot deve ter requisições atualizadas");
    }

    @Test
    @Order(12)
    @DisplayName("Métricas sem dados não causam exceções")
    void testeMetricasSemDados() {
        // Obter snapshot sem registrar nenhuma métrica
        MetricsSnapshot snapshot = metrics.getSnapshot();

        assertAll(
            () -> assertEquals(0, snapshot.totalRequests),
            () -> assertEquals(0, snapshot.totalErrors),
            () -> assertEquals(0.0, snapshot.errorRate),
            () -> assertEquals(0, snapshot.cacheHits),
            () -> assertEquals(0, snapshot.cacheMisses),
            () -> assertEquals(0.0, snapshot.cacheHitRate),
            () -> assertEquals(0.0, snapshot.avgLatencyMs),
            () -> assertEquals(0.0, snapshot.minLatencyMs),
            () -> assertEquals(0.0, snapshot.maxLatencyMs)
        );
    }

    @Test
    @Order(13)
    @DisplayName("toString() produz output legível")
    void testeToString() {
        metrics.recordRequest(true, 1_000_000);
        metrics.recordRequest(false, 2_000_000);
        metrics.recordCacheHit();
        metrics.recordCacheMiss();

        MetricsSnapshot snapshot = metrics.getSnapshot();
        String output = snapshot.toString();

        assertAll(
            () -> assertTrue(output.contains("MÉTRICAS"), "Deve conter título"),
            () -> assertTrue(output.contains("Requisições"), "Deve conter requisições"),
            () -> assertTrue(output.contains("Cache"), "Deve conter info de cache"),
            () -> assertTrue(output.contains("Latência"), "Deve conter latências"),
            () -> assertTrue(output.contains("Throughput"), "Deve conter throughput")
        );
    }

    @Test
    @Order(14)
    @DisplayName("Singleton retorna mesma instância")
    void testeSingleton() {
        PerformanceMetrics instance1 = PerformanceMetrics.getInstance();
        PerformanceMetrics instance2 = PerformanceMetrics.getInstance();

        assertSame(instance1, instance2, "Deve retornar a mesma instância");
    }

    @Test
    @Order(15)
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Singleton é thread-safe")
    void testeSingletonThreadSafe() throws InterruptedException {
        int numThreads = 50;
        CountDownLatch latch = new CountDownLatch(numThreads);
        PerformanceMetrics[] instances = new PerformanceMetrics[numThreads];

        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            new Thread(() -> {
                try {
                    instances[index] = PerformanceMetrics.getInstance();
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Todas threads devem completar");

        // Todas instâncias devem ser a mesma
        PerformanceMetrics first = instances[0];
        for (int i = 1; i < numThreads; i++) {
            assertSame(first, instances[i], 
                      "Todas instâncias devem ser iguais (índice " + i + ")");
        }
    }
}