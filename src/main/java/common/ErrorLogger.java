package common;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Sistema centralizado de logging de erros
 * Thread-safe usando ReadWriteLock
 */
public class ErrorLogger {
    private static final DateTimeFormatter FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final StringBuilder errorLog = new StringBuilder();
    
    private static ErrorLogger instance;
    
    private ErrorLogger() {}
    
    public static synchronized ErrorLogger getInstance() {
        if (instance == null) {
            instance = new ErrorLogger();
        }
        return instance;
    }
    
    /**
     * Registra erro com stack trace completo
     */
    public void logError(String contexto, Throwable erro) {
        lock.writeLock().lock();
        try {
            String timestamp = LocalDateTime.now().format(FORMATTER);
            
            errorLog.append("[ERRO] ").append(timestamp)
                   .append(" - ").append(contexto).append("\n");
            errorLog.append("  Exceção: ").append(erro.getClass().getName())
                   .append(": ").append(erro.getMessage()).append("\n");
            
            // Stack trace
            StringWriter sw = new StringWriter();
            erro.printStackTrace(new PrintWriter(sw));
            errorLog.append(sw.toString()).append("\n");
            
            // Log também para console
            System.err.println("\n" + errorLog.substring(errorLog.length() - 500));
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Regista aviso
     */
    public void logWarning(String contexto, String mensagem) {
        lock.writeLock().lock();
        try {
            String timestamp = LocalDateTime.now().format(FORMATTER);
            
            String entry = "[AVISO] " + timestamp + " - " + contexto + 
                          ": " + mensagem + "\n";
            errorLog.append(entry);
            
            System.out.println(entry);
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Obtém log completo
     */
    public String getLog() {
        lock.readLock().lock();
        try {
            return errorLog.toString();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Limpa log
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            errorLog.setLength(0);
        } finally {
            lock.writeLock().unlock();
        }
    }
}

/**
 * Exceção customizada com contexto adicional
 */
class ContextException extends Exception {
    private final String contexto;
    private final long timestamp;
    
    public ContextException(String mensagem, String contexto) {
        super(mensagem);
        this.contexto = contexto;
        this.timestamp = System.currentTimeMillis();
    }
    
    public ContextException(String mensagem, String contexto, Throwable causa) {
        super(mensagem, causa);
        this.contexto = contexto;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getContexto() {
        return contexto;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return String.format("[%s @ %d] %s", contexto, timestamp, getMessage());
    }
}

/**
 * Wrapper para tratamento seguro de exceções em callbacks
 */
class SafeCallback {
    private final ErrorLogger logger = ErrorLogger.getInstance();
    
    /**
     * Executa callback capturando qualquer exceção
     */
    public void execute(String contexto, Runnable callback) {
        try {
            callback.run();
        } catch (Throwable t) {
            logger.logError(contexto, t);
        }
    }
    
    /**
     * Executa callback com retorno
     */
    public <T> T executeWithReturn(String contexto, java.util.concurrent.Callable<T> callback, T defaultValue) {
        try {
            return callback.call();
        } catch (Throwable t) {
            logger.logError(contexto, t);
            return defaultValue;
        }
    }
}