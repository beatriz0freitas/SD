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
    private static final ReentrantReadWriteLock instanceLock = new ReentrantReadWriteLock();
    
    private ErrorLogger() {}
    
    public static ErrorLogger getInstance() {
        // Double-checked locking com locks explícitos
        if (instance == null) {
            instanceLock.writeLock().lock();
            try {
                if (instance == null) {
                    instance = new ErrorLogger();
                }
            } finally {
                instanceLock.writeLock().unlock();
            }
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
            
            // Log também para console (apenas últimas linhas)
            String lastLines = getLastNCharacters(errorLog.toString(), 500);
            System.err.println("\n" + lastLines);
            
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
    
    private String getLastNCharacters(String str, int n) {
        if (str.length() <= n) {
            return str;
        }
        return str.substring(str.length() - n);
    }
}