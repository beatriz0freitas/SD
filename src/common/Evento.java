package src.comum;

// Representa um evento de venda
public class Evento {

    private int produtoID;      // ID do produto vendido
    private int quantidade;     // Quantidade vendida
    private double preco;       // Preço unitário da venda
    // private int dia;          //TODO: nao sei se vale a pena ter porque ja vao estar organizados por dias na estrutura de dados (map etc)

    // Construtor
    public Evento(int produtoID, int quantidade, double preco, int dia) {
        this.produtoID = produtoID;
        this.quantidade = quantidade;
        this.preco = preco;
        // this.dia = dia;
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

    // public int getDia() {
    //     return dia;
    // }

    // public void setDia(int dia) {
    //     this.dia = dia;
    // }

    @Override
    public String toString() {
        return "Evento{" +
                "produtoID=" + produtoID +
                ", quantidade=" + quantidade +
                ", preco=" + preco +
                // ", dia=" + dia +
                '}';
    }
}
