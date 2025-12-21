package common.dto;

import java.io.Serializable;

public class RespostaDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private boolean sucesso;
    private String mensagem;
    private Object dados;
    
    public RespostaDTO() {}
    
    public RespostaDTO(boolean sucesso, String mensagem) {
        this.sucesso = sucesso;
        this.mensagem = mensagem;
    }
    
    public RespostaDTO(boolean sucesso, String mensagem, Object dados) {
        this.sucesso = sucesso;
        this.mensagem = mensagem;
        this.dados = dados;
    }
    
    public static RespostaDTO sucesso(String mensagem) {
        return new RespostaDTO(true, mensagem);
    }
    
    public static RespostaDTO sucesso(String mensagem, Object dados) {
        return new RespostaDTO(true, mensagem, dados);
    }
    
    public static RespostaDTO erro(String mensagem) {
        return new RespostaDTO(false, mensagem);
    }
    
    public boolean isSucesso() { return sucesso; }
    public void setSucesso(boolean sucesso) { this.sucesso = sucesso; }
    
    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }
    
    public Object getDados() { return dados; }
    public void setDados(Object dados) { this.dados = dados; }
    
    @Override
    public String toString() {
        return String.format("RespostaDTO{sucesso=%b, mensagem='%s'}", 
            sucesso, mensagem);
    }
}