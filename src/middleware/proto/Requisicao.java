package middleware.proto;

import java.io.Serializable;

/**
 * Representa uma requisição no protocolo, usando IDs binários
 */
public class Requisicao implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private byte serviceId;   // ID do serviço
    private byte methodId;    // ID do método
    private Object parametros;
    private long tag;         // ← NOVO: ID único da requisição

    public Requisicao() {}

    /**
     * Construtor ANTIGO - mantém compatibilidade
     * Tag será 0 (para comunicação síncrona simples)
     */
    public Requisicao(byte serviceId, byte methodId, Object parametros) {
        this(serviceId, methodId, parametros, 0L);
    }

    /**
     * Construtor NOVO - com tag para demultiplexing
     *
     * @param serviceId ID do serviço (ex: 1=Auth, 2=Eventos)
     * @param methodId  ID do método (ex: 1=Registrar, 2=Login)
     * @param parametros DTO ou parâmetros da operação
     * @param tag ID único desta requisição para demultiplexing
     */
    public Requisicao(byte serviceId, byte methodId, Object parametros, long tag) {
        this.serviceId = serviceId;
        this.methodId = methodId;
        this.parametros = parametros;
        this.tag = tag;
    }

    public byte getServiceId() {
        return serviceId;
    }

    public void setServiceId(byte serviceId) {
        this.serviceId = serviceId;
    }

    public byte getMethodId() {
        return methodId;
    }

    public void setMethodId(byte methodId) {
        this.methodId = methodId;
    }

    public Object getParametros() {
        return parametros;
    }

    public void setParametros(Object parametros) {
        this.parametros = parametros;
    }

    public long getTag() {
        return tag;
    }

    public void setTag(long tag) {
        this.tag = tag;
    }

    @Override
    public String toString() {
        return "Requisicao{" +
                "serviceId=" + serviceId +
                ", methodId=" + methodId +
                ", tag=" + tag +
                '}';
    }
}