package common.exceptions;


public class DadosInvalidosException extends ServicoException {
    private static final long serialVersionUID = 1L;
    
    public DadosInvalidosException(String mensagem) {
        super(mensagem);
    }
    
    public DadosInvalidosException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}