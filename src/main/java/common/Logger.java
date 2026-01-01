package common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Sistema de logging simples e estruturado
 * Alternativa aos System.out.println() espalhados
 */
public class Logger {
    
    public enum Level {
        DEBUG(0, "DEBUG"),
        INFO(1, "INFO "),
        WARN(2, "WARN "),
        ERROR(3, "ERROR");
        
        final int priority;
        final String label;
        
        Level(int priority, String label) {
            this.priority = priority;
            this.label = label;
        }
    }
    
    private static Level currentLevel = Level.INFO;
    private static final DateTimeFormatter formatter = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    public static void setLevel(Level level) {
        currentLevel = level;
    }
    
    public static void debug(String component, String message) {
        log(Level.DEBUG, component, message, null);
    }
    
    public static void info(String component, String message) {
        log(Level.INFO, component, message, null);
    }
    
    public static void warn(String component, String message) {
        log(Level.WARN, component, message, null);
    }
    
    public static void error(String component, String message) {
        log(Level.ERROR, component, message, null);
    }
    
    public static void error(String component, String message, Throwable e) {
        log(Level.ERROR, component, message, e);
    }
    
    private static void log(Level level, String component, String message, Throwable e) {
        if (level.priority < currentLevel.priority) {
            return;
        }
        
        String timestamp = LocalDateTime.now().format(formatter);
        String threadName = Thread.currentThread().getName();
        
        String logMessage = String.format("[%s] [%s] [%-20s] [%-15s] %s",
            timestamp,
            level.label,
            threadName,
            component,
            message
        );
        
        if (level == Level.ERROR) {
            System.err.println(logMessage);
            if (e != null) {
                e.printStackTrace(System.err);
            }
        } else {
            System.out.println(logMessage);
        }
    }
}

/**
 * EXEMPLO DE USO:
 * 
 * ANTES:
 * System.out.println("Cliente conectado: " + socket.getInetAddress());
 * System.err.println("Erro ao carregar série: " + e.getMessage());
 * e.printStackTrace();
 * 
 * DEPOIS:
 * Logger.info("ClientHandler", "Cliente conectado: " + socket.getInetAddress());
 * Logger.error("CacheManager", "Erro ao carregar série", e);
 * 
 * OUTPUT:
 * [2025-12-30 14:23:45.123] [INFO ] [Thread-5            ] [ClientHandler  ] Cliente conectado: /127.0.0.1
 * [2025-12-30 14:23:46.456] [ERROR] [Thread-3            ] [CacheManager   ] Erro ao carregar série
 * java.io.FileNotFoundException: dados/eventos/eventos_dia_5.dat
 *     at EventoFileRepository.carregarEventosDia(EventoFileRepository.java:89)
 *     ...
 * 
 * BENEFÍCIOS:
 * - Timestamp em todas as mensagens
 * - Identificação de thread
 * - Nível de log (filtrar DEBUG em produção)
 * - Formato consistente
 * - Fácil parsear logs para análise
 */