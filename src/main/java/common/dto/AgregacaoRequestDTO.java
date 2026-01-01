package common.dto;

import java.io.Serializable;

/**
 * DTO para requisições de agregação
 */
public class AgregacaoRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private int produtoID;
    private int dias;
    
    public AgregacaoRequestDTO() {}
    
    public AgregacaoRequestDTO(int produtoID, int dias) {
        this.produtoID = produtoID;
        this.dias = dias;
    }
    
    public int getProdutoID() {
        return produtoID;
    }
    
    public void setProdutoID(int produtoID) {
        this.produtoID = produtoID;
    }
    
    public int getDias() {
        return dias;
    }
    
    public void setDias(int dias) {
        this.dias = dias;
    }
}