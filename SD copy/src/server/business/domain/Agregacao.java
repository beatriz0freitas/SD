package server.business.domain;

/**
 * Entidade de domínio - Agregação de dados de vendas
 */
public class Agregacao {
    private int quantidadeVendas;
    private double volumeVendas;
    private double precoMedio;
    private double precoMaximo;
    
    public Agregacao() {
        this.quantidadeVendas = 0;
        this.volumeVendas = 0.0;
        this.precoMedio = 0.0;
        this.precoMaximo = 0.0;
    }
    
    /**
     * Construtor de cópia
     */
    public Agregacao(Agregacao outra) {
        this.quantidadeVendas = outra.quantidadeVendas;
        this.volumeVendas = outra.volumeVendas;
        this.precoMedio = outra.precoMedio;
        this.precoMaximo = outra.precoMaximo;
    }
    
    public int getQuantidadeVendas() {
        return quantidadeVendas;
    }
    
    public double getVolumeVendas() {
        return volumeVendas;
    }
    
    public double getPrecoMedio() {
        return precoMedio;
    }
    
    public double getPrecoMaximo() {
        return precoMaximo;
    }
    
    /**
     * Atualiza agregação com novo evento
     */
    public void update(int quantidade, double preco) {
        this.quantidadeVendas += quantidade;
        this.volumeVendas += quantidade * preco;
        updatePrecoMaximo(preco);
    }
    
    /**
     * Atualiza preço máximo
     */
    public void updatePrecoMaximo(double preco) {
        if (this.precoMaximo < preco) {
            this.precoMaximo = preco;
        }
    }
    
    /**
     * Calcula e atualiza o preço médio
     * Deve ser chamado após todas as atualizações
     */
    public void updatePrecoMedio() {
        if (quantidadeVendas > 0) {
            this.precoMedio = volumeVendas / quantidadeVendas;
        }
    }
    
    /**
     * Acumula outra agregação nesta
     */
    public void acumular(Agregacao outra) {
        if (outra == null) return;
        
        this.quantidadeVendas += outra.quantidadeVendas;
        this.volumeVendas += outra.volumeVendas;
        updatePrecoMaximo(outra.precoMaximo);
        updatePrecoMedio();
    }
    
    @Override
    public String toString() {
        return String.format(
            "Agregacao{qtd=%d, volume=%.2f, medio=%.2f, max=%.2f}",
            quantidadeVendas, volumeVendas, precoMedio, precoMaximo
        );
    }
}