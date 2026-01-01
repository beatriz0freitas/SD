package testes;

import common.concurrency.ThreadPool;
import common.concurrency.ThreadPoolImpl;

import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes do ThreadPool (JUnit 5)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ThreadPoolTestJUnit {

    @Test
    @Order(1)
    @DisplayName("Executar tarefas simples")
    void testeBasico() throws InterruptedException {

        ThreadPool pool = new ThreadPoolImpl(2);
        AtomicInteger contador = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            assertTrue(
                    pool.submit(contador::incrementAndGet),
                    "Task rejeitada inesperadamente"
            );
        }

        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(5, contador.get(), "Número incorreto de tasks executadas");
    }

    @Test
    @Order(2)
    @DisplayName("Rejeição quando fila está cheia")
    void testeRejeicao() throws InterruptedException {

        ThreadPool pool = new ThreadPoolImpl(1, 2);
        CountDownLatch latch = new CountDownLatch(1);

        pool.submit(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        boolean aceite1 = pool.submit(() -> {});
        boolean aceite2 = pool.submit(() -> {});
        boolean rejeitada = !pool.submit(() -> {});

        latch.countDown();
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);

        assertTrue(aceite1, "Primeira task deveria ser aceite");
        assertTrue(aceite2, "Segunda task deveria ser aceite");
        assertTrue(rejeitada, "Task deveria ter sido rejeitada");
    }

    @Test
    @Order(3)
    @DisplayName("Execução concorrente de múltiplas tasks")
    void testeConcorrencia() throws InterruptedException {

        ThreadPool pool = new ThreadPoolImpl(10);
        AtomicInteger contador = new AtomicInteger(0);

        int numTasks = 100;
        CountDownLatch latch = new CountDownLatch(numTasks);

        for (int i = 0; i < numTasks; i++) {
            pool.submit(() -> {
                contador.incrementAndGet();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                latch.countDown();
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals(numTasks, contador.get(), "Nem todas as tasks foram executadas");
    }

    @Test
    @Order(4)
    @DisplayName("Shutdown processa tasks pendentes")
    void testeShutdown() throws InterruptedException {

        ThreadPool pool = new ThreadPoolImpl(1);
        AtomicInteger contador = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        pool.submit(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        for (int i = 0; i < 5; i++) {
            pool.submit(contador::incrementAndGet);
        }

        pool.shutdown();
        latch.countDown();

        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(5, contador.get(), "Tasks pendentes não foram executadas");
    }

    @Test
    @Order(5)
    @DisplayName("ShutdownNow descarta tasks pendentes")
    void testeShutdownNow() throws InterruptedException {

        ThreadPool pool = new ThreadPoolImpl(1);
        AtomicInteger contador = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        pool.submit(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        for (int i = 0; i < 5; i++) {
            pool.submit(contador::incrementAndGet);
        }

        pool.shutdownNow();
        latch.countDown();
        pool.awaitTermination(2, TimeUnit.SECONDS);

        assertEquals(
                0,
                contador.get(),
                "Tasks pendentes não deveriam ter sido executadas"
        );
    }

    @Test
    @Order(6)
    @DisplayName("Workers são reutilizados")
    void testeReutilizacaoThreads() throws InterruptedException {

        ThreadPool pool = new ThreadPoolImpl(2);

        for (int i = 0; i < 10; i++) {
            pool.submit(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        Thread.sleep(1000);

        assertEquals(
                2,
                pool.getActiveThreads(),
                "Número incorreto de workers ativos"
        );

        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test
    @Order(7)
    @DisplayName("Workers sobrevivem a exceções em tasks")
    void testeExcecoesEmTasks() throws InterruptedException {

        ThreadPool pool = new ThreadPoolImpl(2);
        AtomicInteger sucesso = new AtomicInteger(0);

        pool.submit(() -> {
            throw new RuntimeException("Erro intencional");
        });

        for (int i = 0; i < 5; i++) {
            pool.submit(sucesso::incrementAndGet);
        }

        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(5, sucesso.get(), "Workers morreram após exceção");
    }
}
