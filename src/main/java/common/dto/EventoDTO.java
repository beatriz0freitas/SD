package common.dto;

import java.io.Serializable;

public class EventoDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private int produtoID;
    private int quantidade;
    private double preco;
    private int dia;
    
    public EventoDTO() {}
    
    public EventoDTO(int produtoID, int quantidade, double preco) {
        this.produtoID = produtoID;
        this.quantidade = quantidade;
        this.preco = preco;
    }
    
    public int getProdutoID() { return produtoID; }
    public void setProdutoID(int produtoID) { this.produtoID = produtoID; }
    
    public int getQuantidade() { return quantidade; }
    public void setQuantidade(int quantidade) { this.quantidade = quantidade; }
    
    public double getPreco() { return preco; }
    public void setPreco(double preco) { this.preco = preco; }
    
    public int getDia() { return dia; }
    public void setDia(int dia) { this.dia = dia; }
    
    public double getVolume() { return quantidade * preco; }
    
    @Override
    public String toString() {
        return String.format("EventoDTO{produto=%d, qtd=%d, preco=%.2f}", 
            produtoID, quantidade, preco);
    }
}