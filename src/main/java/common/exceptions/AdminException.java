package common.exceptions;


public class AdminException extends ServicoException {
    private static final long serialVersionUID = 1L;
    
    public AdminException(String mensagem) {
        super(mensagem);
    }
    
    public AdminException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
