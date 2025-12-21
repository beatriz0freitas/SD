package server.data.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import server.business.domain.Agregacao;
import server.data.IEventoDAO;

/**
 * Gerenciador de cache para agregações
 */
public class CacheManager {
    private final IEventoDAO eventoDAO;
    private final int D; // Número de dias a considerar
    
    // Cache: produtoID -> dia -> Agregacao
    private final Map<Integer, Map<Integer, Agregacao>> cache = new HashMap<>();
    
    public CacheManager(IEventoDAO eventoDAO, int D) {
        this.eventoDAO = eventoDAO;
        this.D = D;
    }
    
    /**
     * Obtém entrada da cache para um produto em um dia
     * Se não existir, busca do DAO e adiciona à cache
     */
    private Agregacao obterEntradaDia(int produtoID, int dia) {
        // Obter cache do produto
        Map<Integer, Agregacao> cacheProduto = cache.computeIfAbsent(
            produtoID, k -> new HashMap<>());
        
        // Se não existe na cache, buscar do DAO
        if (!cacheProduto.containsKey(dia)) {
            Agregacao agregacao = eventoDAO.agregarEventosDia(produtoID, dia);
            cacheProduto.put(dia, agregacao);
            System.out.println("Cache MISS: produto=" + produtoID + " dia=" + dia);
        } else {
            System.out.println("Cache HIT: produto=" + produtoID + " dia=" + dia);
        }
        
        return cacheProduto.get(dia);
    }
    
    /**
     * Método genérico para obter agregação dos últimos N dias
     */
    private <T> T obterAgregacao(
            int produtoID, 
            int dias, 
            Function<Agregacao, T> extractor) {
        
        // Limitar ao número de dias disponíveis
        int ultimoDia = eventoDAO.obterUltimoDia();
        if (dias > ultimoDia + 1) {
            dias = ultimoDia + 1;
        }
        
        // Acumular agregações dos últimos N dias
        Agregacao resultado = new Agregacao();
        
        for (int i = 0; i < dias; i++) {
            int dia = ultimoDia - i;
            if (dia < 0) break;
            
            Agregacao entradaDia = obterEntradaDia(produtoID, dia);
            resultado.acumular(entradaDia);
        }
        
        // Calcular preço médio final
        resultado.updatePrecoMedio();
        
        // Extrair resultado desejado
        return extractor.apply(resultado);
    }
    
    /**
     * Obtém quantidade total de vendas
     */
    public int obterQuantidade(int produtoID, int dias) {
        return obterAgregacao(produtoID, dias, Agregacao::getQuantidadeVendas);
    }
    
    /**
     * Obtém volume total de vendas
     */
    public double obterVolume(int produtoID, int dias) {
        return obterAgregacao(produtoID, dias, Agregacao::getVolumeVendas);
    }
    
    /**
     * Obtém preço médio de vendas
     */
    public double obterPrecoMedio(int produtoID, int dias) {
        return obterAgregacao(produtoID, dias, Agregacao::getPrecoMedio);
    }
    
    /**
     * Obtém preço máximo de vendas
     */
    public double obterPrecoMaximo(int produtoID, int dias) {
        return obterAgregacao(produtoID, dias, Agregacao::getPrecoMaximo);
    }
    
    /**
     * Limpa cache de um dia antigo
     * Chamado quando avançamos para novo dia
     */
    public void limparDiaAntigo(int dia) {
        int diaParaRemover = dia % D;
        
        for (Map<Integer, Agregacao> cacheProduto : cache.values()) {
            if (cacheProduto.remove(diaParaRemover) != null) {
                System.out.println("Cache REMOVE: dia=" + dia + " (slot " + diaParaRemover + ")");
            }
        }
    }
    
    /**
     * Limpa toda a cache
     */
    public void limparTudo() {
        cache.clear();
        System.out.println("Cache completamente limpa");
    }
    
    /**
     * Obtém estatísticas da cache
     */
    public String obterEstatisticas() {
        int totalEntradas = 0;
        for (Map<Integer, Agregacao> cacheProduto : cache.values()) {
            totalEntradas += cacheProduto.size();
        }
        
        return String.format(
            "Cache: %d produtos, %d entradas totais",
            cache.size(), totalEntradas
        );
    }
}