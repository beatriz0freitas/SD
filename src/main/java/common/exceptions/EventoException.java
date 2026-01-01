package common.exceptions;

/**
 * Exceção lançada quando há problemas com eventos
 */
public class EventoException extends ServicoException {
    private static final long serialVersionUID = 1L;
    
    public EventoException(String mensagem) {
        super(mensagem);
    }
    
    public EventoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
