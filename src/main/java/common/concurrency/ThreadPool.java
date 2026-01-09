package common.concurrency;

/**
 * Abstração de um Thread Pool para execução concorrente de tarefas.
 * Implementações concretas são responsáveis pela gestão de threads e filas.
 */
public interface ThreadPool {

    /**
     * Submete uma tarefa para execução.
     *
     * @param task tarefa a executar
     * @return true se a tarefa foi aceite, false se foi rejeitada
     */
    boolean submit(Runnable task);

    /**
     * @return número máximo de threads do pool
     */
    int getMaxThreads();

    /**
     * @return número atual de threads ativas
     */
    int getActiveThreads();

    void awaitTermination() throws InterruptedException;

    /**
     * Inicia o encerramento do pool.
     * Implementações podem permitir a conclusão das tarefas pendentes.
     */
    void shutdown();

    /**
     * Força o encerramento do pool, sem esperar pelas tarefas pendentes.
     */
    void shutdownNow();

}
