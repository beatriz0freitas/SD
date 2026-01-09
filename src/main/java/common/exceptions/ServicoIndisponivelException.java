package common.exceptions;


public class ServicoIndisponivelException extends ServicoException {
    private static final long serialVersionUID = 1L;
    
    public ServicoIndisponivelException(String mensagem) {
        super(mensagem);
    }
    
    public ServicoIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}