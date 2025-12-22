package server.data.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import server.business.domain.Agregacao;
import server.data.repository.IEventoRepository;

/**
 * Gerenciador de cache para agregações
 */
public class CacheManager {
    private final IEventoRepository eventoRepository;
    private final int D; // Número de dias a considerar

    // Cache: produtoID -> dia -> Agregacao
    private final Map<Integer, Map<Integer, Agregacao>> cache = new HashMap<>();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public CacheManager(IEventoRepository eventoRepository, int D) {
        this.eventoRepository = eventoRepository;
        this.D = D;
    }

    /**
     * Obtém entrada da cache para um produto em um dia
     * Se não existir, busca do Repository e adiciona à cache
     */
    private Agregacao obterEntradaDia(int produtoID, int dia) {
        lock.writeLock().lock();
        try {
            Map<Integer, Agregacao> cacheProduto = cache.computeIfAbsent(produtoID, k -> new HashMap<>());
            Agregacao existente = cacheProduto.get(dia);
            if (existente == null) {
                Agregacao agregacao = eventoRepository.agregarEventosDia(produtoID, dia);
                cacheProduto.put(dia, agregacao);
                System.out.println("Cache MISS: produto=" + produtoID + " dia=" + dia);
                return agregacao;
            } else {
                System.out.println("Cache HIT: produto=" + produtoID + " dia=" + dia);
                return existente;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Método genérico para obter agregação dos últimos N dias
     */
    private <T> T obterAgregacao(int produtoID, int dias, Function<Agregacao, T> extractor) {
        // Limitar ao número de dias disponíveis
        int ultimoDia = eventoRepository.obterUltimoDia();
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

    public int obterQuantidade(int produtoID, int dias) {
        return obterAgregacao(produtoID, dias, Agregacao::getQuantidadeVendas);
    }

    public double obterVolume(int produtoID, int dias) {
        return obterAgregacao(produtoID, dias, Agregacao::getVolumeVendas);
    }

    public double obterPrecoMedio(int produtoID, int dias) {
        return obterAgregacao(produtoID, dias, Agregacao::getPrecoMedio);
    }

    public double obterPrecoMaximo(int produtoID, int dias) {
        return obterAgregacao(produtoID, dias, Agregacao::getPrecoMaximo);
    }

    /**
     * Limpa cache de um dia antigo
     * Chamado quando avançamos para novo dia
     */
    public void limparDiaAntigo(int dia) {
        int diaParaRemover = dia % D;
        lock.writeLock().lock();
        try {
            for (Map<Integer, Agregacao> cacheProduto : cache.values()) {
                if (cacheProduto.remove(diaParaRemover) != null) {
                    System.out.println("Cache REMOVE: dia=" + dia + " (slot " + diaParaRemover + ")");
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Limpa toda a cache
     */
    public void limparTudo() {
        lock.writeLock().lock();
        try {
            cache.clear();
            System.out.println("Cache completamente limpa");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Obtém estatísticas da cache
     */
    public String obterEstatisticas() {
        lock.readLock().lock();
        try {
            int totalEntradas = 0;
            for (Map<Integer, Agregacao> cacheProduto : cache.values()) {
                totalEntradas += cacheProduto.size();
            }

            return String.format(
                "Cache: %d produtos, %d entradas totais",
                cache.size(), totalEntradas
            );
        } finally {
            lock.readLock().unlock();
        }
    }
}