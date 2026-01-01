package common.dto;

import java.io.Serializable;
import java.util.Set;

/**
 * DTO para requisição de filtrar eventos de uma série temporal
 */
public class FiltrarEventosDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Set<Integer> produtosIDs;  // Conjunto c de produtos
    private int diaAnterior;           // d dias anteriores (1 ≤ d ≤ D)
    
    public FiltrarEventosDTO() {}
    
    public FiltrarEventosDTO(Set<Integer> produtosIDs, int diaAnterior) {
        this.produtosIDs = produtosIDs;
        this.diaAnterior = diaAnterior;
    }
    
    public Set<Integer> getProdutosIDs() {
        return produtosIDs;
    }
    
    public void setProdutosIDs(Set<Integer> produtosIDs) {
        this.produtosIDs = produtosIDs;
    }
    
    public int getDiaAnterior() {
        return diaAnterior;
    }
    
    public void setDiaAnterior(int diaAnterior) {
        this.diaAnterior = diaAnterior;
    }
}