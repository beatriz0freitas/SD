package common.exceptions;

/**
 * Exceção base para todos os serviços
 */
public class ServicoException extends Exception {
    private static final long serialVersionUID = 1L;
    
    public ServicoException(String mensagem) {
        super(mensagem);
    }
    
    public ServicoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}