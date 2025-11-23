package src.servidor;

import com.sun.source.tree.Tree;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

// TODO
//  - neste momento só serve para dia atual, não guarda info calculada em dias anteriores
//  - fazer expirar entradas demasiado antigas (?) -> isso acontece quando?

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
        // Obtém a cache para o produto
        TreeMap<Integer, Agregacao> cacheProduto = cache.get(produto);
        if (cacheProduto == null) {
            cacheProduto = new TreeMap<>();  // TreeMap facilita as procuras de chave mais proxima
            cache.put(produto, cacheProduto);
        }

        // Obtém a chave mais próxima do "dias" (menor ou igual)
        // TLDR: pode-se calcular cumulativamente as métricas, mas nao "subtrair" por causa de preçoMax
        // se houver chave menor registada, podemos ler a partir daí apenas
        Integer lower = cacheProduto.floorKey(dias);

        // Cria a agregação, caso não exista na cache
        Agregacao cacheEntry;
        if (lower != null) {
            // Se já tiver algo em cache para o intervalo até "lower", faz uma cópia
            cacheEntry = new Agregacao(cacheProduto.get(lower));
        } else {
            // Se não tiver nada, inicia uma nova agregação
            cacheEntry = new Agregacao();
        }

        // Obtém o último dia disponível
        int ultimoDia = persistenciaEventos.obterUltimoDia();

        // Começa o loop do dia "lower", ou de 0 se não houver na cache
        for (int i = lower != null ? lower : 0; i < dias; i++) {
            // Calcula o dia físico com base no último dia registado
            int diaFisico = ultimoDia - i;
            Agregacao agregacaoDia = persistenciaEventos.agregarEventosDia(produto, diaFisico);
            cacheEntry.acumular(agregacaoDia);
        }

        // só se pode calcular no fim
        cacheEntry.updatePrecoMedio();

        // Registra a nova entrada na cache
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
            System.out.println("Acesso à Cache: " + produto + " " + dias);
        } else {
            entrada = agregar(produto, dias);
            System.out.println("Criação de Entrada na Cache: " + produto + " " + dias);
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

    public void clear() {
        cache.clear();
    }
}
