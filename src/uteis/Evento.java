package src.uteis;

import java.util.Objects;

// Representa um evento de venda
public class Evento {

    private int produtoID;      // ID do produto vendido
    private int quantidade;     // Quantidade vendida
    private double preco;       // Preço unitário da venda

    public Evento () {
        this.produtoID = 0;
        this.quantidade = 0;
        this.preco = 0.0;
    }

    public Evento(int produtoID, int quantidade, double preco) {
        this.produtoID = produtoID;
        this.quantidade = quantidade;
        this.preco = preco;
    }

    // Getters e Setters
    public int getProdutoID() {
        return produtoID;
    }

    public void setProdutoID(int produtoID) {
        this.produtoID = produtoID;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(int quantidade) {
        this.quantidade = quantidade;
    }

    public double getPreco() {
        return preco;
    }

    public void setPreco(double preco) {
        this.preco = preco;
    }

    public double getVolume() {
        return quantidade * preco;
    }
    
    @Override
    public String toString() {
        return "Evento{"
                + "produtoID=" + produtoID
                + ", quantidade=" + quantidade
                + ", preco=" + preco
                + '}';
    }

    @Override
    public boolean equals (Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Evento evento = (Evento) o;
        return produtoID == evento.produtoID &&
               quantidade == evento.quantidade &&
               Double.compare(evento.preco, preco) == 0;
    }
    
    //mesmo que objetos tenham os mesmos atributos so sao considerados iguais se tiverem o mesmo hashcode (complemento do equals)
    @Override 
    public int hashCode() {
        return Objects.hash(produtoID, quantidade, preco);
    }
}
