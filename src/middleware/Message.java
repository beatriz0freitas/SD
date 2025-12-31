// ============ middleware/Message.java ============
package middleware;

import java.io.Serializable;

/**
 * Mensagem unificada para comunicação cliente-servidor.
 * Substitui Requisicao e TaggedResponse.
 * 
 * - Requests: contêm serviceId, methodId e payload (parâmetros)
 * - Responses: contêm apenas payload (resultado ou Exception)
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private long tag;
    private Byte serviceId;  // null em responses
    private Byte methodId;   // null em responses
    private Object payload;
    
    // Construtor privado - usar factory methods
    private Message() {}
    
    /**
     * Cria uma mensagem de REQUEST (cliente -> servidor)
     */
    public static Message request(long tag, byte serviceId, byte methodId, Object payload) {
        Message m = new Message();
        m.tag = tag;
        m.serviceId = serviceId;
        m.methodId = methodId;
        m.payload = payload;
        return m;
    }
    
    /**
     * Cria uma mensagem de RESPONSE (servidor -> cliente)
     */
    public static Message response(long tag, Object payload) {
        Message m = new Message();
        m.tag = tag;
        m.payload = payload;
        return m;
    }
    
    /**
     * Verifica se é um request (tem serviceId definido)
     */
    public boolean isRequest() {
        return serviceId != null;
    }
    
    /**
     * Verifica se é uma resposta
     */
    public boolean isResponse() {
        return serviceId == null;
    }
    
    // Getters
    public long getTag() {
        return tag;
    }
    
    public byte getServiceId() {
        if (serviceId == null) {
            throw new IllegalStateException("ServiceId só existe em requests");
        }
        return serviceId;
    }
    
    public byte getMethodId() {
        if (methodId == null) {
            throw new IllegalStateException("MethodId só existe em requests");
        }
        return methodId;
    }
    
    public Object getPayload() {
        return payload;
    }
    
    @Override
    public String toString() {
        if (isRequest()) {
            return String.format("Message{tag=%d, service=%d, method=%d, payload=%s}",
                tag, serviceId, methodId, payload);
        } else {
            return String.format("Message{tag=%d, payload=%s}",
                tag, payload);
        }
    }
}