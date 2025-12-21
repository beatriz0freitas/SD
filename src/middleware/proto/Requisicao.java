package middleware.proto;

import java.io.Serializable;

/**
 * Representa uma requisição no protocolo
 */
public class Requisicao implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String operacao;
    private Object parametros;
    
    public Requisicao() {}
    
    public Requisicao(String operacao, Object parametros) {
        this.operacao = operacao;
        this.parametros = parametros;
    }
    
    public String getOperacao() {
        return operacao;
    }
    
    public void setOperacao(String operacao) {
        this.operacao = operacao;
    }
    
    public Object getParametros() {
        return parametros;
    }
    
    public void setParametros(Object parametros) {
        this.parametros = parametros;
    }
    
    @Override
    public String toString() {
        return "Requisicao{operacao='" + operacao + "'}";
    }
}