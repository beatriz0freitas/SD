package common.concurrency;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import common.ErrorLogger;

/**
 * Implementação customizada de ThreadPool.
 * 
 * Motivação: Controlo fino sobre comportamento, sem dependências externas.
 * 
 * Arquitetura:
 * - taskQueue: fila de tarefas (LinkedList)
 * - workers: conjunto de threads worker ativas
 * - maxThreads: número máximo de threads
 * - maxQueueSize: tamanho máximo da fila
 * 
 * Ciclo de vida de uma tarefa:
 * 1. submit(task) -> adiciona à fila
 * 2. Se workerCount < maxThreads: cria nova worker
 * 3. Worker aguarda taskQueue.notEmpty
 * 4. Worker executa task.run()
 * 5. Worker volta a aguardar
 * 
 * Shutdown:
 * - shutdown(): aceita tasks restantes, nega novos
 * - shutdownNow(): rejeita tudo, cancela tasks pendentes
 */
public class ThreadPoolImpl implements ThreadPool {

    private final Lock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();    // Sinal: fila não vazia
    private final Condition termination = lock.newCondition(); // Sinal: pool terminado

    private final Queue<Runnable> taskQueue = new ArrayDeque<>();  // FIFO de tarefas
    private final Set<Thread> workers = new HashSet<>();           // Workers ativas

    private final int maxThreads;           // Limite de threads
    private final int maxQueueSize;         // Limite da fila

    private int workerIdCounter = 0;        // Para nomar workers
    private int workerCount = 0;            // Threads ativas atualmente
    private boolean shutdown = false;       // Modo shutdown normal
    private boolean shutdownNow = false;    // Shutdown forçado

    public ThreadPoolImpl(int maxThreads) {
        this(maxThreads, Integer.MAX_VALUE);
    }

    public ThreadPoolImpl(int maxThreads, int maxQueueSize) {
        if (maxThreads <= 0) throw new IllegalArgumentException("maxThreads deve ser > 0");
        if (maxQueueSize <= 0) throw new IllegalArgumentException("maxQueueSize deve ser > 0");
        this.maxThreads = maxThreads;
        this.maxQueueSize = maxQueueSize;
    }

    @Override
    public boolean submit(Runnable task) {
        if (task == null) throw new IllegalArgumentException("task não pode ser null");

        lock.lock();
        try {
            if (shutdown || shutdownNow) return false;
            if (taskQueue.size() >= maxQueueSize) return false;

            taskQueue.add(task);

            if (workerCount < maxThreads) startWorker();

            
            notEmpty.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void shutdown() {
        lock.lock();
        try {
            if (shutdown) return;
            shutdown = true;
            notEmpty.signalAll();
            if (workerCount == 0) termination.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void shutdownNow() {
        lock.lock();
        try {
            if (shutdownNow) return;

            shutdown = true;
            shutdownNow = true;

            taskQueue.clear();

            for (Thread t : workers) t.interrupt();

            notEmpty.signalAll();
            termination.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void awaitTermination() throws InterruptedException {
        lock.lock();
        try {
            while (!isTerminated()) {
                termination.await(); 
            }
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

    private boolean isTerminated() {
        return shutdown && workerCount == 0;
    }

    private void startWorker() {
        workerIdCounter++;
        int workerId = workerIdCounter;

        Thread worker = new Thread(this::runWorkerLoop, "ThreadPool-worker-" + workerId);
        worker.setDaemon(false);

        workers.add(worker);
        workerCount++;
        worker.start();
    }

    private void runWorkerLoop() {
        try {
            while (true) {
                Runnable task;

                lock.lock();
                try {
                    while (taskQueue.isEmpty() && !shutdown) {
                        notEmpty.await();
                    }

                    if (shutdownNow) return;

                    if (shutdown && taskQueue.isEmpty()) return;

                    task = taskQueue.poll();
                } finally {
                    lock.unlock();
                }

                if (task != null) {
                    try {
                        task.run();
                    } catch (Throwable t) {
                        ErrorLogger.getInstance().logError(
                                "ThreadPool.Worker[" + Thread.currentThread().getName() + "]",
                                new Exception("Erro ao executar task", t)
                        );
                    }
                }
            }
        } catch (InterruptedException e) {
            
        } finally {
            lock.lock();
            try {
                workerCount--;
                if (shutdown && workerCount == 0) {
                    termination.signalAll();
                }
            } finally {
                lock.unlock();
            }
        }
    }
}