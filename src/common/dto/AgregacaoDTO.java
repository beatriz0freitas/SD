package common.dto;

import java.io.Serializable;

public class AgregacaoDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private int produtoID;
    private int dias;
    private int quantidadeVendas;
    private double volumeVendas;
    private double precoMedio;
    private double precoMaximo;
    
    public AgregacaoDTO() {}
    
    public AgregacaoDTO(int produtoID, int dias) {
        this.produtoID = produtoID;
        this.dias = dias;
    }
    
    public int getProdutoID() { return produtoID; }
    public void setProdutoID(int produtoID) { this.produtoID = produtoID; }
    
    public int getDias() { return dias; }
    public void setDias(int dias) { this.dias = dias; }
    
    public int getQuantidadeVendas() { return quantidadeVendas; }
    public void setQuantidadeVendas(int quantidadeVendas) { 
        this.quantidadeVendas = quantidadeVendas; 
    }
    
    public double getVolumeVendas() { return volumeVendas; }
    public void setVolumeVendas(double volumeVendas) { 
        this.volumeVendas = volumeVendas; 
    }
    
    public double getPrecoMedio() { return precoMedio; }
    public void setPrecoMedio(double precoMedio) { 
        this.precoMedio = precoMedio; 
    }
    
    public double getPrecoMaximo() { return precoMaximo; }
    public void setPrecoMaximo(double precoMaximo) { 
        this.precoMaximo = precoMaximo; 
    }
    
    @Override
    public String toString() {
        return String.format(
            "AgregacaoDTO{produto=%d, dias=%d, qtd=%d, volume=%.2f, medio=%.2f, max=%.2f}",
            produtoID, dias, quantidadeVendas, volumeVendas, precoMedio, precoMaximo
        );
    }
}