import java.io.Serializable;

public class Evento implements Serializable {
    private String nome_produto;
    private int quantidade;
    private double preço_unitario;
    
    public Evento(String nome_produto, int quantidade, double preço_unitario) {
        this.nome_produto = nome_produto;
        this.quantidade = quantidade;
        this.preço_unitario = preço_unitario;
    }

    public String getNome_produto() {
        return nome_produto;
    }
    public int getQuantidade() {
        return quantidade;
    }
    public double getPreço_unitario() {
        return preço_unitario;
    }

}
