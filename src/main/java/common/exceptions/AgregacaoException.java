package common.exceptions;


public class AgregacaoException extends ServicoException {
    private static final long serialVersionUID = 1L;
    
    public AgregacaoException(String mensagem) {
        super(mensagem);
    }
    
    public AgregacaoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}