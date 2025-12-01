package src.uteis;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/*
 * Implementação explicíta de uma Thread Pool para gestão da concorrência.
*/
public class ThreadPool {
    private final Lock lock = new ReentrantLock();
    private final Queue<Runnable> taskQueue = new ArrayDeque<>();
    private final int maxThreads;
    private int activeThreads = 0;
    private final int maxQueueSize = 100;

    public ThreadPool(int n) {
        this.maxThreads = n;
    }

    public boolean submit(Runnable task) {
        lock.lock();
        try {
            // Se ainda há espaço para criar uma nova thread
            if (activeThreads < maxThreads) {
                activeThreads++;
                startWorker(task);
                return true;
            }

            // Caso contrário, tenta enfileirar
            if (taskQueue.size() < maxQueueSize) {
                taskQueue.add(task);
                System.out.println("Task queued.");
                return true;
            } else {
                System.out.println("Task queue full! Task rejected.");
                return false;
            }
        } finally {
            lock.unlock();
        }
    }

    private void startWorker(Runnable firstTask) {
        Thread worker = new Thread(() -> {
            try {
                // Primeiro task passado diretamente
                Runnable task = firstTask;
                while (task != null) {
                    try {
                        task.run();
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }

                    // Buscar próxima tarefa da fila
                    lock.lock();
                    try {
                        task = taskQueue.poll();
                        if (task == null) {
                            // Nada mais para fazer, esta thread morre
                            activeThreads--;
                            return;
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            } finally {
                // fallback de segurança, caso algo corra mal no loop
                lock.lock();
                try {
                    if (activeThreads > 0) activeThreads--;
                } finally {
                    lock.unlock();
                }
            }
        });

        worker.start();
    }
}
