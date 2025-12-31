package SD.testes;

import common.concurrency.ThreadPool;
import common.concurrency.ThreadPoolImpl;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Testes para ThreadPool
 */
public class ThreadPoolTest {
    
    public static void main(String[] args) {
        System.out.println("=== TESTES DO THREADPOOL ===\n");
        
        testeBasico();
        testeRejeicao();
        testeConcorrencia();
        testeShutdown();
        testeShutdownNow();
        testeReutilizacaoThreads();
        testeExcecoesEmTasks();
        
        System.out.println("\n=== TODOS OS TESTES CONCLUÍDOS ===");
    }
    
    private static void testeBasico() {
        System.out.println("1. Teste Básico - Executar tarefas simples");
        
        ThreadPool pool = new ThreadPoolImpl(2);
        AtomicInteger contador = new AtomicInteger(0);
        
        for (int i = 0; i < 5; i++) {
            boolean aceite = pool.submit(() -> {
                contador.incrementAndGet();
            });
            
            if (!aceite) {
                System.out.println("   ✗ Task rejeitada inesperadamente");
                return;
            }
        }
        
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                System.out.println("   ✗ Pool não terminou a tempo");
                return;
            }
        } catch (InterruptedException e) {
            System.out.println("   ✗ Teste interrompido");
            return;
        }
        
        if (contador.get() == 5) {
            System.out.println("   ✓ PASSOU - 5 tasks executadas\n");
        } else {
            System.out.println("   ✗ FALHOU - Esperado 5, obteve " + contador.get() + "\n");
        }
    }
    
    private static void testeRejeicao() {
        System.out.println("2. Teste Rejeição - Fila cheia");
        
        ThreadPool pool = new ThreadPoolImpl(1, 2); // max 1 thread, fila max 2
        CountDownLatch latch = new CountDownLatch(1);
        
        // Submeter task que bloqueia a thread
        pool.submit(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        // Encher fila
        boolean aceite1 = pool.submit(() -> {});
        boolean aceite2 = pool.submit(() -> {});
        
        // Esta deve ser rejeitada
        boolean rejeitada = !pool.submit(() -> {});
        
        latch.countDown();
        pool.shutdown();
        
        try {
            pool.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // Ignorar
        }
        
        if (aceite1 && aceite2 && rejeitada) {
            System.out.println("   ✓ PASSOU - Rejeição funcionou corretamente\n");
        } else {
            System.out.println("   ✗ FALHOU - Comportamento inesperado\n");
        }
    }
    
    private static void testeConcorrencia() {
        System.out.println("3. Teste Concorrência - Múltiplas threads");
        
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
        
        try {
            boolean completed = latch.await(10, TimeUnit.SECONDS);
            
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
            
            if (completed && contador.get() == numTasks) {
                System.out.println("   ✓ PASSOU - " + numTasks + " tasks executadas concorrentemente\n");
            } else {
                System.out.println("   ✗ FALHOU - Esperado " + numTasks + ", obteve " + contador.get() + "\n");
            }
            
        } catch (InterruptedException e) {
            System.out.println("   ✗ Teste interrompido\n");
        }
    }
    
    private static void testeShutdown() {
        System.out.println("4. Teste Shutdown - Processar pendentes");
        
        ThreadPool pool = new ThreadPoolImpl(1);
        AtomicInteger contador = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        
        // Bloquear thread worker
        pool.submit(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        // Adicionar tasks pendentes
        for (int i = 0; i < 5; i++) {
            pool.submit(contador::incrementAndGet);
        }
        
        // Shutdown gracioso
        pool.shutdown();
        latch.countDown();
        
        try {
            if (pool.awaitTermination(5, TimeUnit.SECONDS)) {
                if (contador.get() == 5) {
                    System.out.println("   ✓ PASSOU - Tasks pendentes processadas após shutdown\n");
                } else {
                    System.out.println("   ✗ FALHOU - Esperado 5, obteve " + contador.get() + "\n");
                }
            } else {
                System.out.println("   ✗ Timeout esperando término\n");
            }
        } catch (InterruptedException e) {
            System.out.println("   ✗ Teste interrompido\n");
        }
    }
    
    private static void testeShutdownNow() {
        System.out.println("5. Teste ShutdownNow - Descartar pendentes");
        
        ThreadPool pool = new ThreadPoolImpl(1);
        AtomicInteger contador = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        
        // Bloquear thread worker
        pool.submit(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        // Adicionar tasks pendentes
        for (int i = 0; i < 5; i++) {
            pool.submit(contador::incrementAndGet);
        }
        
        // ShutdownNow (descartar pendentes)
        pool.shutdownNow();
        latch.countDown();
        
        try {
            pool.awaitTermination(2, TimeUnit.SECONDS);
            
            // Tasks pendentes devem ter sido descartadas
            if (contador.get() == 0) {
                System.out.println("   ✓ PASSOU - Tasks pendentes descartadas\n");
            } else {
                System.out.println("   ✗ FALHOU - Tasks foram executadas: " + contador.get() + "\n");
            }
        } catch (InterruptedException e) {
            System.out.println("   ✗ Teste interrompido\n");
        }
    }
    
    private static void testeReutilizacaoThreads() {
        System.out.println("6. Teste Reutilização - Workers permanecem ativos");
        
        ThreadPool pool = new ThreadPoolImpl(2);
        
        // Primeira rodada
        for (int i = 0; i < 10; i++) {
            pool.submit(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        try {
            Thread.sleep(1000); // Aguardar conclusão
            
            int threadsAtivas = pool.getActiveThreads();
            
            if (threadsAtivas == 2) {
                System.out.println("   ✓ PASSOU - Workers reutilizados (" + threadsAtivas + " threads)\n");
            } else {
                System.out.println("   ✗ FALHOU - Esperado 2 threads, obteve " + threadsAtivas + "\n");
            }
            
            pool.shutdown();
            pool.awaitTermination(2, TimeUnit.SECONDS);
            
        } catch (InterruptedException e) {
            System.out.println("   ✗ Teste interrompido\n");
        }
    }
    
    private static void testeExcecoesEmTasks() {
        System.out.println("7. Teste Exceções - Workers sobrevivem a erros");
        
        ThreadPool pool = new ThreadPoolImpl(2);
        AtomicInteger sucesso = new AtomicInteger(0);
        
        // Task que lança exceção
        pool.submit(() -> {
            throw new RuntimeException("Erro intencional");
        });
        
        // Tasks normais após exceção
        for (int i = 0; i < 5; i++) {
            pool.submit(sucesso::incrementAndGet);
        }
        
        pool.shutdown();
        
        try {
            if (pool.awaitTermination(5, TimeUnit.SECONDS)) {
                if (sucesso.get() == 5) {
                    System.out.println("   ✓ PASSOU - Workers sobreviveram a exceções\n");
                } else {
                    System.out.println("   ✗ FALHOU - Esperado 5, obteve " + sucesso.get() + "\n");
                }
            } else {
                System.out.println("   ✗ Timeout esperando término\n");
            }
        } catch (InterruptedException e) {
            System.out.println("   ✗ Teste interrompido\n");
        }
    }
}