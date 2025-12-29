package common.dto;

import java.io.Serializable;

public class NotificacaoDTO implements Serializable {
    private static final long serialVersionUID = 1L;


    private final int produtoID1;
    private final int produtoID2;

    public NotificacaoDTO(int produtoID1, int produtoID2) {
        this.produtoID1 = produtoID1;
        this.produtoID2 = produtoID2;
    }

    public int getProdutoID1() {
        return produtoID1;
    }

    public int getProdutoID2() {
        return produtoID2;
    }
}   