package common.concurrency;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/*
 * Implementação concreta de ThreadPool com reutilização de threads:
 * - Cria até maxThreads on-demand.
 * - Workers ficam vivos e aguardam novas tasks quando a fila está vazia.
 */
public class ThreadPoolImpl implements ThreadPool {

    private final Lock lock = new ReentrantLock();

    // Condition para workers aguardarem trabalho
    private final Condition notEmpty = lock.newCondition();

    // Condition para awaitTermination
    private final Condition termination = lock.newCondition();

    private final Queue<Runnable> taskQueue = new ArrayDeque<>();
    private final Set<Thread> workers = new HashSet<>();

    private final int maxThreads;
    private final int maxQueueSize = Integer.MAX_VALUE; // fila ilimitada

    // número de workers existentes
    private int workerCount = 0;
    // número de workers disponíveis
    //private int idleWorkers = 0;


    private boolean shutdown = false;
    private boolean shutdownNow = false;

    public ThreadPoolImpl(int n) {
        if (n <= 0) throw new IllegalArgumentException("n must be > 0");
        this.maxThreads = n;
    }

    // para uma sem limite máximo de threads, util para thread I/O por conexão
    public ThreadPoolImpl() {
        this.maxThreads = Integer.MAX_VALUE;
    }

    @Override
    public boolean submit(Runnable task) {
        if (task == null) throw new IllegalArgumentException("task == null");

        lock.lock();
        try {
            if (shutdown) {
                return false; // não aceita novas tasks após shutdown/shutdownNow
            }

            if (taskQueue.size() >= maxQueueSize) {
                return false; // fila cheia
            }

            taskQueue.add(task);

            // só cria se não houver idle
            if (workerCount < maxThreads) {
                startWorker();
            }

            // acorda um worker (existente ou recém-criado)
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
            shutdown = true;

            // acorda todos para que possam sair quando a fila esvaziar
            notEmpty.signalAll();

            // caso já esteja tudo terminado
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
            shutdown = true;
            shutdownNow = true;

            // descarta pendentes
            taskQueue.clear();

            // acorda todos e interrompe (para tasks bloqueadas/interrompíveis)
            for (Thread t : workers) {
                t.interrupt();
            }
            notEmpty.signalAll();
            termination.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (unit == null) throw new IllegalArgumentException("unit == null");
        long nanos = unit.toNanos(timeout);

        lock.lock();
        try {
            // Terminado quando:
            // - shutdown foi pedido
            // - e não há workers vivos
            while (!(shutdown && workerCount == 0)) {
                if (nanos <= 0L) return false;
                nanos = termination.awaitNanos(nanos);
            }
            return true;
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

    private void startWorker() {
        // o lock já está adquirido no submit
        Thread worker = new Thread(this::runWorkerLoop, "ThreadPool-worker-" + workerCount);
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
                    while (taskQueue.isEmpty()) {
                        if (shutdown) return;
                        notEmpty.await();
                        if (shutdown && taskQueue.isEmpty()) return;
                    }

                    // interromper em caso de shutdownNow
                    if (shutdownNow) {
                        return;
                    }

                    // Verifica se há tarefa para executar
                    task = taskQueue.poll();
                    if (task == null) {
                        if (shutdown) {
                            return;
                        }
                        continue;
                    }
                } finally {
                    lock.unlock();
                }

                // Executa a task fora do lock
                try {
                    task.run();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        } catch (InterruptedException e) {
            // interrompido via shutdownNow
        } finally {
            lock.lock();
            try {
                workerCount--;
                workers.remove(Thread.currentThread());

                if (shutdown && workerCount == 0) {
                    termination.signalAll();
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
