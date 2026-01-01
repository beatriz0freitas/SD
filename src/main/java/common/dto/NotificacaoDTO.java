package common.dto;

import java.io.Serializable;

/**
 * DTO para notificações de eventos específicos
 * Argumentos genéricos para diferentes tipos de notificações
 * TODO Aplicar a AgregacaoRequest tambem??? (estrutura de 2 ints)
 */
public class NotificacaoDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int arg1; // produto1 ou produtoID
    private final int arg2; // produto2 ou n de vendas

    public NotificacaoDTO(int arg1, int arg2) {
        this.arg1 = arg1;
        this.arg2 = arg2;
    }

    public int getArg1() {
        return arg1;
    }

    public int getArg2() {
        return arg2;
    }
}   