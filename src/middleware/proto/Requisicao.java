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

    public Requisicao() {}

    /**
     * Cria uma requisição com IDs binários
     *
     * @param serviceId ID do serviço (ex: 1=Auth, 2=Eventos)
     * @param methodId  ID do método (ex: 1=Registrar, 2=Login)
     * @param parametros DTO ou parâmetros da operação
     */
    public Requisicao(byte serviceId, byte methodId, Object parametros) {
        this.serviceId = serviceId;
        this.methodId = methodId;
        this.parametros = parametros;
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

    @Override
    public String toString() {
        return "Requisicao{" +
                "serviceId=" + serviceId +
                ", methodId=" + methodId +
                '}';
    }
}
