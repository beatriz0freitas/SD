package common.concurrency;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementação de ThreadPool com reutilização de threads.
 * 
 * Características:
 * - Cria workers sob demanda até atingir maxThreads
 * - Workers reutilizam threads aguardando novas tasks
 * - Suporta shutdown gracioso (processa pendentes) e forçado
 * - Fila de tarefas com capacidade configurável
 */
public class ThreadPoolImpl implements ThreadPool {

    private final Lock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition termination = lock.newCondition();

    private final Queue<Runnable> taskQueue = new ArrayDeque<>();
    private final Set<Thread> workers = new HashSet<>();

    private final int maxThreads;
    private final int maxQueueSize;
    
    // Contador para dar nomes únicos aos workers
    private final AtomicInteger workerIdCounter = new AtomicInteger(0);

    private int workerCount = 0;
    private boolean shutdown = false;
    private boolean shutdownNow = false;

    /**
     * Cria uma thread pool com número máximo de threads e fila ilimitada
     */
    public ThreadPoolImpl(int maxThreads) {
        this(maxThreads, Integer.MAX_VALUE);
    }
    
    /**
     * Cria uma thread pool com número máximo de threads e capacidade de fila
     */
    public ThreadPoolImpl(int maxThreads, int maxQueueSize) {
        if (maxThreads <= 0) {
            throw new IllegalArgumentException("maxThreads deve ser > 0");
        }
        if (maxQueueSize <= 0) {
            throw new IllegalArgumentException("maxQueueSize deve ser > 0");
        }
        this.maxThreads = maxThreads;
        this.maxQueueSize = maxQueueSize;
    }

    @Override
    public boolean submit(Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("task não pode ser null");
        }

        lock.lock();
        try {
            // Rejeita novas tasks após shutdown
            if (shutdown) {
                return false;
            }

            // Rejeita se a fila estiver cheia
            if (taskQueue.size() >= maxQueueSize) {
                return false;
            }

            // Adiciona task à fila
            taskQueue.add(task);

            // Cria novo worker se ainda não atingiu o máximo
            if (workerCount < maxThreads) {
                startWorker();
            } else {
                // Acorda um worker existente
                notEmpty.signal();
            }
            
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void shutdown() {
        lock.lock();
        try {
            if (shutdown) {
                return; // já foi chamado
            }
            
            shutdown = true;

            // Acorda todos os workers para que possam processar
            // as tasks pendentes e terminar quando a fila esvaziar
            notEmpty.signalAll();

            // Se já não houver workers, sinaliza término imediato
            if (workerCount == 0) {
                termination.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void shutdownNow() {
        lock.lock();
        try {
            if (shutdownNow) {
                return; // já foi chamado
            }
            
            shutdown = true;
            shutdownNow = true;

            // Descarta todas as tasks pendentes
            int discarded = taskQueue.size();
            taskQueue.clear();
            if (discarded > 0) {
                System.out.println("[ThreadPool] " + discarded + " tasks pendentes descartadas");
            }

            // Interrompe todos os workers
            for (Thread t : workers) {
                t.interrupt();
            }

            // Acorda todos
            notEmpty.signalAll();
            termination.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (unit == null) {
            throw new IllegalArgumentException("unit não pode ser null");
        }
        
        long nanos = unit.toNanos(timeout);

        lock.lock();
        try {
            // Pool está terminada quando shutdown foi chamado e não há workers vivos
            while (!isTerminated()) {
                if (nanos <= 0L) {
                    return false; // timeout
                }
                nanos = termination.awaitNanos(nanos);
            }
            return true; // terminou com sucesso
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int getMaxThreads() {
        return maxThreads;
    }

    @Override
    public int getActiveThreads() {
        lock.lock();
        try {
            return workerCount;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Retorna o número de tasks pendentes na fila
     */
    public int getQueueSize() {
        lock.lock();
        try {
            return taskQueue.size();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Verifica se a pool está terminada
     * (shutdown foi chamado E não há workers vivos)
     */
    private boolean isTerminated() {
        // lock já deve estar adquirido
        return shutdown && workerCount == 0;
    }

    /**
     * Cria e inicia um novo worker thread
     * ATENÇÃO: deve ser chamado com o lock adquirido
     */
    private void startWorker() {
        int workerId = workerIdCounter.incrementAndGet();
        Thread worker = new Thread(
            this::runWorkerLoop, 
            "ThreadPool-worker-" + workerId
        );
        worker.setDaemon(false); // não é daemon para garantir término gracioso
        workers.add(worker);
        workerCount++;
        worker.start();
    }

    /**
     * Loop principal de um worker thread
     */
    private void runWorkerLoop() {
        try {
            while (true) {
                Runnable task = null;

                lock.lock();
                try {
                    // Aguarda por trabalho
                    while (taskQueue.isEmpty() && !shutdown) {
                        notEmpty.await();
                    }

                    // Se está em shutdownNow, termina imediatamente
                    if (shutdownNow) {
                        return;
                    }

                    // Se está em shutdown e fila vazia, termina
                    if (shutdown && taskQueue.isEmpty()) {
                        return;
                    }

                    // Obtém próxima task
                    task = taskQueue.poll();
                    
                } finally {
                    lock.unlock();
                }

                // Executa a task fora do lock
                if (task != null) {
                    try {
                        task.run();
                    } catch (Throwable t) {
                        // Captura qualquer exceção/erro para evitar que o worker morra
                        System.err.println("[ThreadPool] Erro ao executar task: " + t.getMessage());
                        t.printStackTrace();
                    }
                }
            }
            
        } catch (InterruptedException e) {
            // Worker foi interrompido (shutdownNow)
            // Thread vai terminar normalmente
            
        } finally {
            // Cleanup: remove worker da pool
            lock.lock();
            try {
                workerCount--;
                workers.remove(Thread.currentThread());

                // Se foi o último worker e está em shutdown, sinaliza término
                if (shutdown && workerCount == 0) {
                    termination.signalAll();
                }
            } finally {
                lock.unlock();
            }
        }
    }
}