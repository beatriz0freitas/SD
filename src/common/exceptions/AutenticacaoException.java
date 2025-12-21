package common.exceptions;

/**
 * Exceção lançada quando há problemas de autenticação
 */
public class AutenticacaoException extends ServicoException {
    private static final long serialVersionUID = 1L;
    
    public AutenticacaoException(String mensagem) {
        super(mensagem);
    }
    
    public AutenticacaoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}