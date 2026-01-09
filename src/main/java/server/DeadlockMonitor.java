package server;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import common.ErrorLogger;

public class DeadlockMonitor {
    private final ThreadMXBean threadBean;
    private final ScheduledExecutorService scheduler;
    private volatile boolean running;
    
    public DeadlockMonitor() {
        this.threadBean = ManagementFactory.getThreadMXBean();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DeadlockMonitor");
            t.setDaemon(true);
            return t;
        });
        this.running = false;
    }
    
    public void start(long intervalSeconds) {
        if (running) return;
        
        running = true;
        scheduler.scheduleAtFixedRate(
            this::checkForDeadlocks,
            intervalSeconds,
            intervalSeconds,
            TimeUnit.SECONDS
        );
        
        System.out.println("DeadlockMonitor iniciado (intervalo: " + intervalSeconds + "s)");
    }
    
    public void stop() {
        if (!running) return;
        
        running = false;
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.println("DeadlockMonitor parado");
    }
    
    private void checkForDeadlocks() {
        long[] deadlockedThreads = threadBean.findDeadlockedThreads();
        
        if (deadlockedThreads != null && deadlockedThreads.length > 0) {
            ThreadInfo[] threadInfos = threadBean.getThreadInfo(deadlockedThreads, true, true);
            
            StringBuilder sb = new StringBuilder();
            sb.append("DEADLOCK DETECTADO! \n");
            sb.append("Threads em deadlock: ").append(deadlockedThreads.length).append("\n\n");
            
            for (ThreadInfo info : threadInfos) {
                sb.append("Thread: ").append(info.getThreadName())
                  .append(" (ID: ").append(info.getThreadId()).append(")\n");
                sb.append("Estado: ").append(info.getThreadState()).append("\n");
                
                if (info.getLockName() != null) {
                    sb.append("Aguardando lock: ").append(info.getLockName()).append("\n");
                }
                
                if (info.getLockOwnerName() != null) {
                    sb.append("Lock detido por: ").append(info.getLockOwnerName())
                      .append(" (ID: ").append(info.getLockOwnerId()).append(")\n");
                }
                
                sb.append("Stack trace:\n");
                for (StackTraceElement element : info.getStackTrace()) {
                    sb.append("  ").append(element.toString()).append("\n");
                }
                sb.append("\n");
            }
            
            System.err.println(sb.toString());
            ErrorLogger.getInstance().logError("DeadlockMonitor", 
                new Exception("Deadlock detectado!\n" + sb.toString()));
        }
    }
}