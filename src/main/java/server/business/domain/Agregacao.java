package server.business.domain;


public class Agregacao {
    private int quantidadeVendas;
    private double volumeVendas;
    private double precoMaximo;
    private double somaPrecos;
    private int numeroEventos;
    private double precoMedio;
    
    public Agregacao() {
        this.quantidadeVendas = 0;
        this.volumeVendas = 0.0;
        this.precoMaximo = 0.0;
        this.somaPrecos = 0.0;
        this.numeroEventos = 0;
        this.precoMedio = 0.0;
    }
    
    
    public void update(int quantidade, double preco) {
        this.quantidadeVendas += quantidade;
        this.volumeVendas += quantidade * preco;
        
        if (preco > this.precoMaximo) {
            this.precoMaximo = preco;
        }
        
        this.somaPrecos += preco;
        this.numeroEventos++;
    }
    
    
    public void updatePrecoMedio() {
        if (numeroEventos > 0) {
            this.precoMedio = somaPrecos / numeroEventos;
        } else {
            this.precoMedio = 0.0;
        }
    }
    
    
    public void acumular(Agregacao outra) {
        if (outra == null) {
            return;
        }
        
        this.quantidadeVendas += outra.quantidadeVendas;
        this.volumeVendas += outra.volumeVendas;
        
        if (outra.precoMaximo > this.precoMaximo) {
            this.precoMaximo = outra.precoMaximo;
        }
        
        this.somaPrecos += outra.somaPrecos;
        this.numeroEventos += outra.numeroEventos;
    }
    
    
    
    public int getQuantidadeVendas() {
        return quantidadeVendas;
    }
    
    public double getVolumeVendas() {
        return volumeVendas;
    }
    
    public double getPrecoMaximo() {
        return precoMaximo;
    }
    
    public double getPrecoMedio() {
        return precoMedio;
    }
    
    @Override
    public String toString() {
        return String.format(
            "Agregacao{qtd=%d, volume=%.2f€, max=%.2f€, medio=%.2f€, eventos=%d}",
            quantidadeVendas, volumeVendas, precoMaximo, precoMedio, numeroEventos
        );
    }
}