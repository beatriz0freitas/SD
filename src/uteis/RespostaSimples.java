package src.uteis;

/*
 * Representa uma resposta simples com status de sucesso e mensagem. 
 * //todo: referir utilidade
 */
public class RespostaSimples {
    // private boolean sucesso;
    private String mensagem;
    
    public RespostaSimples(boolean sucesso, String mensagem) {
        // this.sucesso = sucesso;
        this.mensagem = mensagem;
    }
    
    
    public String getMensagem() {
        return mensagem;
    }
}