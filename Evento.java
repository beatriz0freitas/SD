import java.io.Serializable;

public class Evento implements Serializable {
    private String nome_produto;
    private int quantidade;
    private double preco_unitario;
    
    public Evento(String nomeProduto, int quant, double precoUnitario) {
        this.nome_produto = nomeProduto;
        this.quantidade = quant;
        this.preco_unitario = precoUnitario;
    }

    public String getNome_produto() {
        return nome_produto;
    }
    public int getQuantidade() {
        return quantidade;
    }
    public double getPreco_unitario() {
        return preco_unitario;
    }

    public String toString() {
        return "Evento [nome_produto=" + nome_produto + 
                     ", quantidade=" + quantidade + 
                     ", preco_unitario=" + preco_unitario + 
                     "]";
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Evento evento = (Evento) o;
        return quantidade == evento.quantidade &&
               Double.compare(evento.preco_unitario, preco_unitario) == 0 &&
               nome_produto.equals(evento.nome_produto);
    }
}
