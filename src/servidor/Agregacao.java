package src.servidor;

/*
 * Estrutura que define uma entrada na cache
 * É partilhada por persistenciaEventos para compor a cache numa unica leitura
 */
public class Agregacao {
    private int quantidadeVendas = 0;
    private double volumeVendas = 0;
    private double precoMedio = 0;
    private double precoMaximo = 0;

    public Agregacao(){}

    // construtor de copia usado na cache
    public Agregacao(Agregacao a){
        this.quantidadeVendas = a.quantidadeVendas;
        this.volumeVendas = a.volumeVendas;
        this.precoMedio = a.precoMedio;
        this.precoMaximo = a.precoMaximo;
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

    public void update(int quantidade, double preco){
        this.quantidadeVendas += quantidade;
        this.volumeVendas += quantidade * preco;
        updatePrecoMaximo(preco);
    }

    public void updatePrecoMaximo(double preco) {
        if(this.precoMaximo < preco) this.precoMaximo = preco;
    }

    // o preco medio so deve ser atualizado no fim, daí o metodo separado
    public void updatePrecoMedio(){
        this.precoMedio = volumeVendas / quantidadeVendas;
    }

    // para "somar" entradas, de modo a que representem os N ultimos dias
    public void acumular(Agregacao entrada){
        this.quantidadeVendas += entrada.getQuantidadeVendas();
        this.volumeVendas += entrada.getVolumeVendas();
        // precoMedio so deve ser calculado no fim
        updatePrecoMaximo(entrada.getPrecoMaximo());
    }
}