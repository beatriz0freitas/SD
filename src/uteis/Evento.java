package src.uteis;

// Representa um evento de venda
public class Evento {

    private int produtoID;      // ID do produto vendido
    private int quantidade;     // Quantidade vendida
    private double preco;       // Preço unitário da venda

    // Construtor
    private Evento(int produtoID, int quantidade, double preco) {
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
                + // ", dia=" + dia +
                '}';
    }
}
