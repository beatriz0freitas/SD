package server.business.domain;

import java.util.Objects;

/**
 * Entidade de domínio - Evento de venda
 */
public class Evento {
    private int produtoID;
    private int quantidade;
    private double preco;
    private int dia;
    
    public Evento(int produtoID, int quantidade, double preco) {
        this.produtoID = produtoID;
        this.quantidade = quantidade;
        this.preco = preco;
    }
    
    public int getProdutoID() {
        return produtoID;
    }
    
    public int getQuantidade() {
        return quantidade;
    }
    
    public double getPreco() {
        return preco;
    }
    
    public int getDia() {
        return dia;
    }
    
    public void setDia(int dia) {
        this.dia = dia;
    }
    
    public double getVolume() {
        return quantidade * preco;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Evento evento = (Evento) o;
        return produtoID == evento.produtoID &&
               quantidade == evento.quantidade &&
               Double.compare(evento.preco, preco) == 0;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(produtoID, quantidade, preco);
    }
    
    @Override
    public String toString() {
        return String.format("Evento{produto=%d, qtd=%d, preco=%.2f}", 
            produtoID, quantidade, preco);
    }
}