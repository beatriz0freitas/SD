package common.concurrency;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import common.ErrorLogger;

public class ThreadPoolImpl implements ThreadPool {

    private final Lock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition termination = lock.newCondition();

    private final Queue<Runnable> taskQueue = new ArrayDeque<>();
    private final Set<Thread> workers = new HashSet<>();

    private final int maxThreads;
    private final int maxQueueSize;

    private int workerIdCounter = 0;
    private int workerCount = 0;
    private boolean shutdown = false;
    private boolean shutdownNow = false;

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

            // simples e seguro: acorda sempre alguém
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
                termination.await(); // sem tempo
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
            // esperado em shutdownNow
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