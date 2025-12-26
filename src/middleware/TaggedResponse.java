package middleware;

import java.io.Serializable;

/**
 * Envelope que adiciona tag a uma resposta
 * Usado apenas no protocolo de rede
 */
public class TaggedResponse implements Serializable {
    
    private long tag;
    private Object resposta;  

    public TaggedResponse() {}

    public TaggedResponse(long tag, Object resposta) {
        this.tag = tag;
        this.resposta = resposta;
    }

    public long getTag() {
        return tag;
    }

    public void setTag(long tag) {
        this.tag = tag;
    }

    public Object getResposta() {
        return resposta;
    }

    public void setResposta(Object resposta) {
        this.resposta = resposta;
    }

    @Override
    public String toString() {
        return "TaggedResponse{tag=" + tag + ", resposta=" + resposta + '}';
    }
}
