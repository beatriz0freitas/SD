package src.servidor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

// TODO fazer expirar entradas demasiado antigas
//Mantém dados em cache para agilizar consultas e evitar recalcular agregações repetidamente.
public class CacheAgregacoes {
    private PersistenciaEventos persistenciaEventos;
    // map produtoID -> nºDias -> Agregacao
    private Map<Integer, TreeMap<Integer, Agregacao>> cache = new HashMap<>();

    public CacheAgregacoes(PersistenciaEventos persistenciaEventos) {
        this.persistenciaEventos = persistenciaEventos;
    }

    // verifica se a entrada está na cache
    public boolean isCached(int produto, int dia) {
        Map<Integer, Agregacao> porDia = cache.get(produto);
        return porDia != null && porDia.containsKey(dia);
    }

    public Agregacao agregar(int produto, int dias) throws IOException {
        TreeMap<Integer, Agregacao> cacheProduto = cache.get(produto);
        if (cacheProduto == null) {
            cacheProduto = new TreeMap<>(); // facilita as procuras de chave em baixo
            cache.put(produto, cacheProduto);
            // TODO ... fazer do 0 agregação
        }

        // TLDR: pode-se calcular cumulativamente num sentido as métricas, mas nao no outro por causa de preçoMax
        // se houver chave menor registada, podemos ler a partir daí apenas
        Agregacao cacheEntry = new Agregacao();
        // chave menor mais proxima
        Integer lower = cacheProduto.floorKey(dias);

        if (lower != null){
            cacheEntry = new Agregacao(cacheProduto.get(lower));
            lower++; // para nao recalcular o que ja sabemos
        } else {
            lower = 1;
        }

        for(int i = lower; i <= dias; i++){
            Agregacao agregacaoDia = persistenciaEventos.agregarEventosDia(produto, i);
            cacheEntry.acumular(agregacaoDia);
        }

        // regista a nova entrada na cache
        cacheProduto.put(dias, cacheEntry);

        return cacheEntry;
    }

    // metodo genérico para evitar duplicação de código
    // já que apenas varia o getter da agregação, o resto do protocolo é igual
    private <T> T obterAgregacao(
            int produto,
            int dias,
            Function<Agregacao, T> extractor) throws IOException {

        Agregacao entrada;
        if (isCached(produto, dias)) {
            entrada = cache.get(produto).get(dias);
        } else {
            entrada = agregar(produto, dias);
        }

        return extractor.apply(entrada);
    }

    // metodos que devolvem os resultados das queries e metem em cache se necessário

    public int getQuantidade(int produto, int dias) throws IOException {
        return obterAgregacao(produto, dias, Agregacao::getQuantidadeVendas);
    }

    public double getVolume(int produto, int dias) throws IOException {
        return obterAgregacao(produto, dias, Agregacao::getVolumeVendas);
    }

    public double getPrecoMedio(int produto, int dias) throws IOException {
        return obterAgregacao(produto, dias, Agregacao::getPrecoMedio);
    }

    public double getPrecoMaximo(int produto, int dias) throws IOException {
        return obterAgregacao(produto, dias, Agregacao::getPrecoMaximo);
    }

}
