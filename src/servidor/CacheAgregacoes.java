package src.servidor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// TODO
//  - neste momento só serve para dia atual, não guarda info calculada em dias anteriores
//  - fazer expirar entradas demasiado antigas (?) -> isso acontece quando?

/**
 * Cache otimizada com ReadWriteLock.
 * 
 * ESTRATÉGIA DE CONCORRÊNCIA:
 * - readLock: consultas (get*, obterAgregacao)
 * - writeLock: modificações (clearOld, clear)
 * 
 * Permite múltiplas leituras simultâneas, maximizando throughput
 * em cenários read-heavy (típico de caches).
 */
public class CacheAgregacoes {
    private PersistenciaEventos persistenciaEventos;
    private final int D; // para saber os dias que tem em cache
    
    // map produtoID -> nºDias -> Agregacao
    private Map<Integer, Map<Integer, Agregacao>> cache = new HashMap<>();

    // ReadWriteLock para otimizar leituras concorrentes
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    public CacheAgregacoes(PersistenciaEventos persistenciaEventos, int D) {
        this.persistenciaEventos = persistenciaEventos;
        this.D = D;
    }

    /**
     * Obtém entrada da cache para um produto/dia.
     * Cria entrada se não existir (lazy loading).
     * 
     * OTIMIZAÇÃO: Usa padrão double-checked locking otimizado
     * para minimizar contenção em writeLock.
     */
    public Agregacao getEntradaDia(int produto, int dia) throws IOException {
        // 1ª verificação com readLock (caso comum: entrada já existe)
        readLock.lock();
        try {
            Map<Integer, Agregacao> cacheProduto = cache.get(produto);
            if (cacheProduto != null) {
                Agregacao entrada = cacheProduto.get(dia);
                if (entrada != null) {
                    System.out.println("CACHE HIT: produto=" + produto + " dia=" + dia);
                    return entrada;
                }
            }
        } finally {
            readLock.unlock();
        }

        // 2ª verificação com writeLock (entrada não existe, precisa criar)
        writeLock.lock();
        try {
            // Re-verificar (outra thread pode ter criado entre locks)
            Map<Integer, Agregacao> cacheProduto = cache.get(produto);
            if (cacheProduto == null) {
                cacheProduto = new HashMap<>();
                cache.put(produto, cacheProduto);
            }

            Agregacao entrada = cacheProduto.get(dia);
            if (entrada == null) {
                // Carrega do disco DENTRO do writeLock
                // Alternativa: carregar fora do lock e depois inserir
                // Trade-off: I/O dentro do lock vs. possível trabalho duplicado
                entrada = persistenciaEventos.agregarEventosDia(produto, dia);
                if (entrada != null) {
                    cacheProduto.put(dia, entrada);
                    System.out.println("CACHE MISS -> LOADED: produto=" + produto + " dia=" + dia);
                } else {
                    System.out.println("CACHE MISS -> NO DATA: produto=" + produto + " dia=" + dia);
                }
            }

            return entrada;

        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Método genérico para agregações com readLock.
     * Minimiza duplicação de código.
     */
    private <T> T obterAgregacao(int produto, int dias, Function<Agregacao, T> extractor) throws IOException {

        if (dias > D) 
            dias = D;
        
        int ultimoDia = persistenciaEventos.obterUltimoDia();
        if (ultimoDia < 0) {
            // Sem dados ainda
            return extractor.apply(new Agregacao());
        }

        // Acumula agregações dos últimos N dias
        Agregacao entrada = new Agregacao();

        for (int i = 0; i < dias; i++) {
            int diaConsulta = (ultimoDia - i) % D;
            Agregacao agregacaoDia = getEntradaDia(produto, diaConsulta);
            entrada.acumular(agregacaoDia);
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

    /**
    * Remove entradas antigas da cache (usa writeLock).
    * Chamado ao avançar de dia.
    */
    public void clearOld(int dia) {
        writeLock.lock();
        try {
            int slotRemover = dia % D;
            int removidos = 0;

            for (Map<Integer, Agregacao> cacheProduto : cache.values()) {
                if (cacheProduto.remove(slotRemover) != null) {
                    removidos++;
                }
            }

            if (removidos > 0) {
                System.out.println("CACHE CLEANUP: removidas " + removidos + 
                                 " entradas do slot " + slotRemover + " (dia " + dia + ")");
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Limpa toda a cache (usa writeLock).
     */
    public void clear() {
        writeLock.lock();
        try {
            int tamanho = cache.values().stream()
                              .mapToInt(Map::size)
                              .sum();
            cache.clear();
            System.out.println("CACHE CLEARED: " + tamanho + " entradas removidas");
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Estatísticas da cache (usa readLock).
     */
    public String getStats() {
        readLock.lock();
        try {
            int produtos = cache.size();
            int entradas = cache.values().stream()
                               .mapToInt(Map::size)
                               .sum();
            return String.format("Cache[produtos=%d, entradas=%d, D=%d]", 
                               produtos, entradas, D);
        } finally {
            readLock.unlock();
        }
    }
}
