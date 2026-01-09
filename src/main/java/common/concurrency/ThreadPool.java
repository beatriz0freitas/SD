package common.concurrency;


public interface ThreadPool {

    
    boolean submit(Runnable task);

    
    int getMaxThreads();

    
    int getActiveThreads();

    void awaitTermination() throws InterruptedException;

    
    void shutdown();

    
    void shutdownNow();

}
