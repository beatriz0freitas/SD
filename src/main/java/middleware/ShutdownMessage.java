package middleware;

import java.io.Serializable;

/**
 * Mensagem de shutdown do servidor
 */
public class ShutdownMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String razao;
    private final long timestamp;
    
    public ShutdownMessage(String razao) {
        this.razao = razao;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getRazao() {
        return razao;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return "ShutdownMessage{razao='" + razao + "', timestamp=" + timestamp + "}";
    }
}