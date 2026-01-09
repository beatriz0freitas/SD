package testes;

import common.concurrency.ThreadPool;
import common.concurrency.ThreadPoolImpl;

import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes do ThreadPool 
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ThreadPoolTestJUnit {

    @Test
    @Order(1)
    @DisplayName("Executar tarefas simples")
    void testeBasico() throws InterruptedException {
        ThreadPool pool = new ThreadPoolImpl(2);

        int tasks = 5;
        CountDownLatch latch = new CountDownLatch(tasks);

        for (int i = 0; i < tasks; i++) {
            assertTrue(pool.submit(latch::countDown), "Task rejeitada inesperadamente");
        }

        pool.shutdown();
        pool.awaitTermination();

        assertEquals(0, latch.getCount(), "Número incorreto de tasks executadas");
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
        pool.awaitTermination();

        assertTrue(aceite1, "Primeira task deveria ser aceite");
        assertTrue(aceite2, "Segunda task deveria ser aceite");
        assertTrue(rejeitada, "Task deveria ter sido rejeitada");
    }

    @Test
    @Order(3)
    @DisplayName("Execução concorrente de múltiplas tasks")
    void testeConcorrencia() throws InterruptedException {
        ThreadPool pool = new ThreadPoolImpl(10);

        int numTasks = 100;
        CountDownLatch latch = new CountDownLatch(numTasks);

        for (int i = 0; i < numTasks; i++) {
            pool.submit(latch::countDown);
        }

        latch.await();

        pool.shutdown();
        pool.awaitTermination();

        assertEquals(0, latch.getCount(), "Nem todas as tasks foram executadas");
    }

    @Test
    @Order(4)
    @DisplayName("Shutdown processa tasks pendentes")
    void testeShutdown() throws InterruptedException {
        ThreadPool pool = new ThreadPoolImpl(1);
        CountDownLatch bloqueio = new CountDownLatch(1);

        pool.submit(() -> {
            try {
                bloqueio.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        int pendentes = 5;
        CountDownLatch executadas = new CountDownLatch(pendentes);

        for (int i = 0; i < pendentes; i++) {
            pool.submit(executadas::countDown);
        }

        pool.shutdown();
        bloqueio.countDown();

        pool.awaitTermination();

        assertEquals(0, executadas.getCount(), "Tasks pendentes não foram executadas");
    }

    @Test
    @Order(5)
    @DisplayName("ShutdownNow descarta tasks pendentes")
    void testeShutdownNow() throws InterruptedException {
        ThreadPool pool = new ThreadPoolImpl(1);
        CountDownLatch bloqueio = new CountDownLatch(1);

        pool.submit(() -> {
            try {
                bloqueio.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        int pendentes = 5;
        CountDownLatch executadas = new CountDownLatch(pendentes);

        for (int i = 0; i < pendentes; i++) {
            pool.submit(executadas::countDown);
        }

        pool.shutdownNow();
        bloqueio.countDown();

        pool.awaitTermination();

        // Nenhuma das pendentes deveria executar
        assertEquals(pendentes, executadas.getCount(), "Tasks pendentes não deveriam ter sido executadas");
    }

    @Test
    @Order(6)
    @DisplayName("Workers são reutilizados")
    void testeReutilizacaoThreads() throws InterruptedException {
        ThreadPool pool = new ThreadPoolImpl(2);

        int tasks = 10;
        CountDownLatch done = new CountDownLatch(tasks);

        for (int i = 0; i < tasks; i++) {
            pool.submit(done::countDown);
        }

        done.await();

        assertEquals(2, pool.getActiveThreads(), "Número incorreto de workers ativos");

        pool.shutdown();
        pool.awaitTermination();
    }

    @Test
    @Order(7)
    @DisplayName("Workers sobrevivem a exceções em tasks")
    void testeExcecoesEmTasks() throws InterruptedException {
        ThreadPool pool = new ThreadPoolImpl(2);

        int okTasks = 5;
        CountDownLatch done = new CountDownLatch(okTasks);

        pool.submit(() -> { throw new RuntimeException("Erro intencional"); });

        for (int i = 0; i < okTasks; i++) {
            pool.submit(done::countDown);
        }

        pool.shutdown();
        pool.awaitTermination();

        assertEquals(0, done.getCount(), "Workers morreram após exceção");
    }
}