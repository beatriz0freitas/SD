package testes;

import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import common.ErrorLogger;

import static org.junit.jupiter.api.Assertions.*;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ErrorLoggerTest {

    private ErrorLogger logger;

    @BeforeEach
    void setup() {
        logger = ErrorLogger.getInstance();
        logger.clear();
    }

    @Test
    @Order(1)
    @DisplayName("Logging básico de erro")
    void testeLoggingBasico() {
        try {
            throw new RuntimeException("Erro de teste");
        } catch (Exception e) {
            logger.logError("TesteBasico", e);
        }

        String log = logger.getLog();

        assertAll(
                () -> assertTrue(log.contains("ERRO"), "Log deve conter '[ERRO]'"),
                () -> assertTrue(log.contains("TesteBasico"), "Log deve conter contexto"),
                () -> assertTrue(log.contains("RuntimeException"), "Log deve conter tipo de exceção"),
                () -> assertTrue(log.contains("Erro de teste"), "Log deve conter mensagem")
        );
    }

    @Test
    @Order(2)
    @DisplayName("Stack trace completo é registado")
    void testeStackTraceCompleto() {
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
                        "Stack trace deve conter a classe de teste"),
                () -> assertTrue(log.contains("at testes.ErrorLoggerTest"),
                        "Stack trace deve ter formato correto")
        );
    }

    private void metodoComErro() {
        throw new IllegalStateException("Erro no método");
    }

    @Test
    @Order(3)
    @DisplayName("Diferentes tipos de exceções são registados")
    void testeDiferentesTiposExcecoes() {
        logger.logError("IO", new java.io.IOException("Erro de IO"));
        logger.logError("NPE", new NullPointerException("Ponteiro nulo"));
        logger.logError("Custom", new IllegalArgumentException("Argumento inválido"));

        String log = logger.getLog();

        assertAll(
                () -> assertTrue(log.contains("IOException"), "Deve conter IOException"),
                () -> assertTrue(log.contains("NullPointerException"), "Deve conter NullPointerException"),
                () -> assertTrue(log.contains("IllegalArgumentException"), "Deve conter IllegalArgumentException"),
                () -> assertTrue(log.contains("Erro de IO"), "Deve conter mensagem de IO"),
                () -> assertTrue(log.contains("Ponteiro nulo"), "Deve conter mensagem de NPE"),
                () -> assertTrue(log.contains("Argumento inválido"), "Deve conter mensagem custom")
        );
    }

    @Test
    @Order(4)
    @DisplayName("Exceções encadeadas são registadas")
    void testeExcecoesEncadeadas() {
        Exception causa = new IllegalStateException("Causa raiz");
        Exception principal = new RuntimeException("Erro principal", causa);

        logger.logError("Encadeado", principal);

        String log = logger.getLog();

        assertAll(
                () -> assertTrue(log.contains("RuntimeException"), "Deve conter exceção principal"),
                () -> assertTrue(log.contains("Erro principal"), "Deve conter mensagem principal"),
                () -> assertTrue(log.contains("IllegalStateException"), "Deve conter causa"),
                () -> assertTrue(log.contains("Causa raiz"), "Deve conter mensagem da causa")
        );
    }

    @Test
    @Order(5)
    @DisplayName("Warnings são registados")
    void testeWarning() {
        logger.logWarning("TesteWarning", "Este é um aviso");

        String log = logger.getLog();

        assertAll(
                () -> assertTrue(log.contains("AVISO"), "Log deve conter '[AVISO]'"),
                () -> assertTrue(log.contains("TesteWarning"), "Log deve conter contexto"),
                () -> assertTrue(log.contains("Este é um aviso"), "Log deve conter mensagem")
        );
    }

    @Test
    @Order(6)
    @DisplayName("Múltiplos erros são acumulados")
    void testeMultiplosErros() {
        for (int i = 0; i < 5; i++) {
            logger.logError("Erro-" + i, new RuntimeException("Exceção " + i));
        }

        String log = logger.getLog();

        for (int i = 0; i < 5; i++) {
            assertTrue(log.contains("Erro-" + i), "Deve conter erro " + i);
            assertTrue(log.contains("Exceção " + i), "Deve conter exceção " + i);
        }

        int count = log.split("\\[ERRO\\]").length - 1;
        assertEquals(5, count, "Deve ter exatamente 5 erros registados");
    }

    @Test
    @Order(7)
    @DisplayName("Clear limpa o log")
    void testeClear() {
        logger.logError("Teste", new RuntimeException("Erro"));

        String antes = logger.getLog();
        assertTrue(antes.length() > 0, "Log deve ter conteúdo antes de clear");

        logger.clear();

        String depois = logger.getLog();
        assertEquals(0, depois.length(), "Log deve estar vazio após clear");
    }

    @Test
    @Order(8)
    @DisplayName("Timestamps são registados")
    void testeTimestamps() {
        logger.logError("Timestamp1", new RuntimeException("Erro 1"));

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.logError("Timestamp2", new RuntimeException("Erro 2"));

        String log = logger.getLog();

        assertTrue(
                log.matches("(?s).*\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}.*"),
                "Log deve conter timestamps no formato correto"
        );
    }

    @Test
    @Order(9)
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Thread-safety - múltiplas threads logando erros")
    void testeThreadSafety() throws InterruptedException {
        int numThreads = 20;
        CountDownLatch latch = new CountDownLatch(numThreads);

        
        int[] logsFeitos = new int[numThreads];

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;

            new Thread(() -> {
                try {
                    for (int j = 0; j < 10; j++) {
                        logger.logError(
                                "Thread-" + threadId + "-Erro-" + j,
                                new RuntimeException("Erro da thread " + threadId + ", iteração " + j)
                        );
                        logsFeitos[threadId]++;
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue(latch.await(8, TimeUnit.SECONDS), "Todas threads devem completar");

        Thread.sleep(500);

        int totalEsperado = numThreads * 10;
        int totalFeito = 0;
        for (int i = 0; i < numThreads; i++) totalFeito += logsFeitos[i];

        assertEquals(totalEsperado, totalFeito, "Deve ter exatamente " + totalEsperado + " logs feitos");

        String log = logger.getLog();

        int threadsEncontradas = 0;
        for (int i = 0; i < numThreads; i++) {
            if (log.contains("Thread-" + i)) threadsEncontradas++;
        }

        assertTrue(threadsEncontradas >= (int) (numThreads * 0.9),
                "Log deve conter pelo menos 90% das threads. Encontradas: " + threadsEncontradas);
    }

    @Test
    @Order(10)
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Thread-safety - leituras concorrentes")
    void testeThreadSafetyLeituras() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            logger.logError("Setup-" + i, new RuntimeException("Erro " + i));
        }

        int numThreads = 20;
        CountDownLatch latch = new CountDownLatch(numThreads);

        
        int[] leiturasOk = new int[numThreads];

        for (int i = 0; i < numThreads; i++) {
            final int idx = i;
            new Thread(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        String log = logger.getLog();
                        if (log.contains("Setup-")) {
                            leiturasOk[idx]++;
                        }
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue(latch.await(8, TimeUnit.SECONDS), "Todas threads devem completar");

        int total = 0;
        for (int i = 0; i < numThreads; i++) total += leiturasOk[i];

        assertEquals(numThreads * 100, total, "Todas leituras devem ter sucesso");
    }

    @Test
    @Order(11)
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Thread-safety - escrita e leitura concorrentes")
    void testeThreadSafetyEscritaLeitura() throws InterruptedException {
        int numWriters = 10;
        int numReaders = 10;
        CountDownLatch latch = new CountDownLatch(numWriters + numReaders);

        boolean[] writerDone = new boolean[numWriters];
        boolean[] readerDone = new boolean[numReaders];

        for (int i = 0; i < numWriters; i++) {
            final int writerId = i;
            new Thread(() -> {
                try {
                    for (int j = 0; j < 50; j++) {
                        logger.logError("Writer-" + writerId, new RuntimeException("Erro " + j));
                    }
                    writerDone[writerId] = true;
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        for (int i = 0; i < numReaders; i++) {
            final int readerId = i;
            new Thread(() -> {
                try {
                    for (int j = 0; j < 50; j++) {
                        logger.getLog();
                        Thread.sleep(10);
                    }
                    readerDone[readerId] = true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue(latch.await(15, TimeUnit.SECONDS), "Todas threads devem completar");

        int writersOk = 0;
        for (int i = 0; i < numWriters; i++) if (writerDone[i]) writersOk++;

        int readersOk = 0;
        for (int i = 0; i < numReaders; i++) if (readerDone[i]) readersOk++;

        assertEquals(numWriters, writersOk, "Todas escritas devem completar");
        assertEquals(numReaders, readersOk, "Todas leituras devem completar");
    }

    @Test
    @Order(12)
    @DisplayName("Singleton retorna mesma instância")
    void testeSingleton() {
        ErrorLogger instance1 = ErrorLogger.getInstance();
        ErrorLogger instance2 = ErrorLogger.getInstance();

        assertSame(instance1, instance2, "Deve retornar a mesma instância");
    }

    @Test
    @Order(13)
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Singleton é thread-safe")
    void testeSingletonThreadSafe() throws InterruptedException {
        int numThreads = 50;
        CountDownLatch latch = new CountDownLatch(numThreads);

        ErrorLogger[] instances = new ErrorLogger[numThreads];

        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            new Thread(() -> {
                try {
                    instances[index] = ErrorLogger.getInstance();
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Todas threads devem completar");

        ErrorLogger first = instances[0];
        for (int i = 1; i < numThreads; i++) {
            assertSame(first, instances[i], "Todas instâncias devem ser iguais (índice " + i + ")");
        }
    }

    @Test
    @Order(14)
    @DisplayName("Contexto ajuda a identificar origem do erro")
    void testeContextoIdentificaOrigem() {
        logger.logError("Repositorio.salvar", new RuntimeException("Erro ao salvar"));
        logger.logError("ClientHandler.processar", new RuntimeException("Erro ao processar"));
        logger.logError("ServicoEventos.novoDia", new RuntimeException("Erro ao avançar dia"));

        String log = logger.getLog();

        assertAll(
                () -> assertTrue(log.contains("Repositorio.salvar"), "Deve identificar repositório"),
                () -> assertTrue(log.contains("ClientHandler.processar"), "Deve identificar handler"),
                () -> assertTrue(log.contains("ServicoEventos.novoDia"), "Deve identificar serviço")
        );
    }

    @Test
    @Order(15)
    @DisplayName("Log não cresce indefinidamente em uso normal")
    void testeLogNaoCresceIndefinidamente() {
        for (int i = 0; i < 100; i++) {
            logger.logError("Erro-" + i, new RuntimeException("Erro " + i));
        }

        String log1 = logger.getLog();
        int tamanho1 = log1.length();

        logger.clear();

        for (int i = 0; i < 100; i++) {
            logger.logError("Erro-" + i, new RuntimeException("Erro " + i));
        }

        String log2 = logger.getLog();
        int tamanho2 = log2.length();

        assertTrue(Math.abs(tamanho1 - tamanho2) < tamanho1 * 0.1,
                "Tamanho deve ser similar após clear. Antes: " + tamanho1 + ", Depois: " + tamanho2);
    }
}