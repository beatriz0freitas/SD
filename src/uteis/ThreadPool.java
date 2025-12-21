package src.uteis;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/*
 * Implementação explicíta de uma Thread Pool para gestão da concorrência.
 * - Usa Condition para espera eficiente
 * - Evita espera ativa
 * - Garante justiça relativa (FIFO)
 * - Controla corretamente o ciclo de vida dos workers
*/
public class ThreadPool {
    private final Lock lock = new ReentrantLock();
    private final Condition tarefasDisponiveis = lock.newCondition();
    private final Condition espacoDisponivel = lock.newCondition();
    
    private final Queue<Runnable> taskQueue = new ArrayDeque<>();
    private final int maxThreads;
    private final int maxQueueSize;

    private int activeThreads = 0;
    private boolean isShutdown = false;

    public ThreadPool(int maxThreads) {
        this(maxThreads, 100);
    }

    public ThreadPool(int maxThreads, int maxQueueSize) {
        if (maxThreads <= 0 || maxQueueSize <= 0) {
            throw new IllegalArgumentException("maxThreads e maxQueueSize devem ser positivos");
        }
        this.maxThreads = maxThreads;
        this.maxQueueSize = maxQueueSize;
    }

    /**
     * Submete tarefa para execução.
     * Bloqueia se a fila estiver cheia.
    */
    public boolean submit(Runnable task) throws InterruptedException {
        if (task == null) {
            throw new NullPointerException("Tarefa não pode ser null");
        }
        
        lock.lock();
        try {
            // Espera enquanto fila está cheia E não estamos em shutdown
            while (taskQueue.size() >= maxQueueSize && !isShutdown) {
                espacoDisponivel.await();
            }
            
            if (isShutdown) {
                return false;
            }
            
            // Adiciona tarefa à fila
            taskQueue.add(task);
            
            // Se há capacidade para nova thread, cria worker
            if (activeThreads < maxThreads) {
                activeThreads++;
                startWorker();
            } else {
                // Acorda worker existente para processar tarefa
                tarefasDisponiveis.signal();
            }
            return true;

        } finally {
            lock.unlock();
        }
    }


    /**
     * Cria e inicia novo worker thread.
     * DEVE ser chamado com lock adquirido e activeThreads já incrementado.
     */
    private void startWorker() {
        Thread worker = new Thread(() -> {
            try {
                while (true) {
                    Runnable task;
                    lock.lock();
                    try {
                        // Espera por tarefa disponível (Capítulo 3 da sebenta)
                        while (taskQueue.isEmpty() && !isShutdown) {
                            tarefasDisponiveis.await();
                        }
                        
                        // Se shutdown e sem tarefas, termina
                        if (isShutdown && taskQueue.isEmpty()) {
                            return;
                        }
                        
                        task = taskQueue.poll();
                        
                        // Sinaliza que há espaço na fila
                        if (taskQueue.size() < maxQueueSize) {
                            espacoDisponivel.signal();
                        }
                        
                    } finally {
                        lock.unlock();
                    }
                    
                    // Executa tarefa FORA da secção crítica
                    if (task != null) {
                        try {
                            task.run();
                        } catch (Throwable t) {
                            System.err.println("Erro na execução de tarefa: " + t.getMessage());
                            t.printStackTrace();
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Worker interrompido");
            } finally {
                // Decrementa contador ao terminar
                lock.lock();
                try {
                    activeThreads--;
                } finally {
                    lock.unlock();
                }
            }
        });
        worker.setName("ThreadPool-Worker-" + worker.getId());
        worker.start();
    }

    /**
     * Inicia shutdown ordenado.
     * Tarefas já submetidas serão executadas, mas novas submissões falham.
     */
    public void shutdown() {
        lock.lock();
        try {
            isShutdown = true;
            tarefasDisponiveis.signalAll();  // Acorda todos workers
            espacoDisponivel.signalAll();    // Acorda threads bloqueadas em submit
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Estatísticas para debugging
     */
    public String getStats() {
        lock.lock();
        try {
            return String.format(
                "ThreadPool[active=%d/%d, queued=%d/%d, shutdown=%b]",
                activeThreads, maxThreads, taskQueue.size(), maxQueueSize, isShutdown
            );
        } finally {
            lock.unlock();
        }
    }
}
