package testes;

import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import common.ErrorLogger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para ErrorLogger
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ErrorLoggerTest {

    private ErrorLogger logger;

    @BeforeEach
    void setup() {
        logger = ErrorLogger.getInstance();
        logger.clear();
    }

    @Test
    void testeLoggingBasico() {
        try {
            throw new RuntimeException("Erro de teste");
        } catch (Exception e) {
            logger.logError("TesteBasico", e);
        }

        String log = logger.getLog();

        assertAll(
                () -> assertTrue(log.contains("ERRO")),
                () -> assertTrue(log.contains("TesteBasico")),
                () -> assertTrue(log.contains("RuntimeException"))
        );
    }

    @Test
    void testeStackTrace() {
        try {
            metodoComErro();
        } catch (Exception e) {
            logger.logError("TesteStackTrace", e);
        }

        String log = logger.getLog();

        assertAll(
                () -> assertTrue(log.contains("metodoComErro"),
                        "Stack trace deve conter o método que causou o erro"),
                () -> assertTrue(log.contains("ErrorLoggerTest"),
                        "Stack trace deve conter a classe de teste")
        );
    }

    private void metodoComErro() {
        throw new IllegalStateException("Erro no método");
    }

    @Test
    void testeWarning() {
        logger.logWarning("TesteWarning", "Este é um aviso");

        String log = logger.getLog();

        assertAll(
                () -> assertTrue(log.contains("AVISO")),
                () -> assertTrue(log.contains("TesteWarning")),
                () -> assertTrue(log.contains("Este é um aviso"))
        );
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testeConcorrencia() throws InterruptedException {
        int numThreads = 20;
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger errosLogados = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;

            new Thread(() -> {
                try {
                    throw new RuntimeException("Erro da thread " + threadId);
                } catch (Exception e) {
                    logger.logError("Thread-" + threadId, e);
                    errosLogados.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue(latch.await(4, TimeUnit.SECONDS));

        Thread.sleep(500);

        String log = logger.getLog();

        int count = 0;
        for (int i = 0; i < numThreads; i++) {
            if (log.contains("Thread-" + i)) {
                count++;
            }
        }

        assertTrue(
            errosLogados.get() >= numThreads - 1,
            "Deve ter pelo menos 19 erros logados: " + errosLogados.get()
        );

        assertTrue(
            count >= numThreads - 1,
            "Log deve conter pelo menos 19 threads: " + count
        );
    }
}
