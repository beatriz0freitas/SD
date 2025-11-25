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
    int D; // para saber os dias que tem em cache
    // map produtoID -> nºDias -> Agregacao
    private Map<Integer, Map<Integer, Agregacao>> cache = new HashMap<>();

    public CacheAgregacoes(PersistenciaEventos persistenciaEventos, int D) {
        this.persistenciaEventos = persistenciaEventos;
        this.D = D;
    }

    // recebe um dia a agregar, se nao existir cria na cache
    public Agregacao getEntradaDia(int produto, int dia) throws IOException {
        // Obtém a cache para o produto
        Map<Integer, Agregacao> cacheProduto = cache.get(produto);
        if (cacheProduto == null) {
            cacheProduto = new HashMap<>();
            cache.put(produto, cacheProduto);
        }

        if (!cacheProduto.containsKey(dia)) {
            cacheProduto.put(dia, persistenciaEventos.agregarEventosDia(produto, dia));
        }

        return cacheProduto.get(dia);
    }

    // metodo genérico para evitar duplicação de código
    // já que apenas varia o getter da agregação, o resto do protocolo é igual
    private <T> T obterAgregacao(
            int produto,
            int dias,
            Function<Agregacao, T> extractor) throws IOException {

        Agregacao entrada = new Agregacao();
        int ultimoDia = persistenciaEventos.obterUltimoDia();
        for (int i = 0; i < dias; i++){
            entrada.acumular(getEntradaDia(produto, (ultimoDia - i)%D));
        }
        entrada.updatePrecoMedio();

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

    public void clearOld(int dia){
        for(Map<Integer, Agregacao> cacheProduto : cache.values()){
            cacheProduto.remove(dia%D);
        }
    }

    public void clear() {
        cache.clear();
    }
}
