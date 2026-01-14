package server.data.cache;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import common.PerformanceMetrics;
import server.business.domain.Agregacao;
import server.business.domain.Evento;
import server.data.repository.IEventoRepository;


/**
 * Gestor de Cache - Cache de agregações com LRU (Least Recently Used).
 * 
 * Motivação:
 * - Agregações são operações custosas (leitura de ficheiros, cálculos)
 * - Muitos clientes podem consultar mesmos produtos nos mesmos dias
 * - Cache reduz I/O e latência significativamente
 * 
 * Estrutura:
 * - cacheAgregacoes: Map<produtoID, Map<dia, Agregacao>>
 * - seriesEmMemoria: Map<dia, Map<produtoID, List<Evento>>>
 *   * Armazena até S séries em memória (param do servidor)
 *   * Quando S+1, remove a menos recentemente usada
 * 
 * Strategy de cálculo:
 * - Se série está em memória: calcula rápido em RAM
 * - Se série não está e já temos S séries: usa streaming (RandomAccessFile)
 * - Senão: carrega série em memória
 * 
 * Thread safety:
 * - rwLock: proteção geral (read/write)
 * - computationLocks: por chave (produtoID:dia) para evitar múltiplos cálculos
 * 
 * Otimizações:
 * - Double-check locking para reduzir contenção
 * - Lazy computation locks (removidas quando não usadas)
 * - Métricas: recordCacheHit() / recordCacheMiss()
 */
public class CacheManager {
    private final IEventoRepository eventoRepository;
    private final int S;  // Tamanho máximo do cache em memória
    private final PerformanceMetrics metrics;

    // Cache: produto -> (dia -> Agregacao calculada)
    private final Map<Integer, Map<Integer, Agregacao>> cacheAgregacoes = new HashMap<>();

    // Séries em memória: dia -> (produto -> eventos)
    private final Map<Integer, Map<Integer, List<Evento>>> seriesEmMemoria = new HashMap<>();

    // Ordem de acesso (para LRU)
    private final List<Integer> ordemAcesso = new ArrayList<>();

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    // Computation locks por chave (evita múltiplos cálculos simultâneos)
    private final Map<String, ReentrantLock> computationLocks = new HashMap<>();
    private final ReentrantLock computationLocksLock = new ReentrantLock();

    public CacheManager(IEventoRepository eventoRepository, int S) {
        this.eventoRepository = eventoRepository;
        this.S = S;
        this.metrics = PerformanceMetrics.getInstance();
    }

    
    public Agregacao obterAgregacaoDia(int produtoID, int dia) {
        
        readLock.lock();
        try {
            Map<Integer, Agregacao> porProduto = cacheAgregacoes.get(produtoID);
            if (porProduto != null) {
                Agregacao existente = porProduto.get(dia);
                if (existente != null) {
                    metrics.recordCacheHit();
                    return existente;
                }
            }
        } finally {
            readLock.unlock();
        }

        metrics.recordCacheMiss();

        
        String computationKey = produtoID + ":" + dia;
        ReentrantLock compLock = getComputationLock(computationKey);

        compLock.lock();
        try {
            
            readLock.lock();
            try {
                Map<Integer, Agregacao> porProduto = cacheAgregacoes.get(produtoID);
                if (porProduto != null) {
                    Agregacao existente = porProduto.get(dia);
                    if (existente != null) {
                        return existente; 
                    }
                }
            } finally {
                readLock.unlock();
            }

            
            Agregacao calculada;
            boolean usarStreaming;

            readLock.lock();
            try {
                usarStreaming = seriesEmMemoria.size() >= S && 
                              !seriesEmMemoria.containsKey(dia);
            } finally {
                readLock.unlock();
            }

            if (usarStreaming) {
                calculada = eventoRepository.agregarEventosDia(produtoID, dia);
            } else {
                calculada = calcularComMemoria(produtoID, dia);
            }

            
            writeLock.lock();
            try {
                Map<Integer, Agregacao> porProduto = 
                    cacheAgregacoes.computeIfAbsent(produtoID, k -> new HashMap<>());

                
                Agregacao existente = porProduto.get(dia);
                if (existente != null) {
                    return existente;
                }

                porProduto.put(dia, calculada);
                return calculada;
            } finally {
                writeLock.unlock();
            }

        } finally {
            compLock.unlock();
            releaseComputationLock(computationKey);
        }
    }
    
    private ReentrantLock getComputationLock(String key) {
        
        computationLocksLock.lock();
        try {
            return computationLocks.computeIfAbsent(key, k -> new ReentrantLock());
        } finally {
            computationLocksLock.unlock();
        }
    }
    
    private void releaseComputationLock(String key) {
        computationLocksLock.lock();
        try {
            ReentrantLock lock = computationLocks.get(key);
            if (lock != null && !lock.hasQueuedThreads()) {
                computationLocks.remove(key);
            }
        } finally {
            computationLocksLock.unlock();
        }
    }

    
    private Agregacao calcularComMemoria(int produtoID, int dia) {
        writeLock.lock();
        try {
            
            if (seriesEmMemoria.containsKey(dia)) {
                
                ordemAcesso.remove(Integer.valueOf(dia));
                ordemAcesso.add(dia);

                List<Evento> eventos = seriesEmMemoria.get(dia).get(produtoID);
                return agregarEventos(eventos);
            }

            
            
            if (seriesEmMemoria.size() >= S) {
                Integer diaRemover = ordemAcesso.remove(0);
                seriesEmMemoria.remove(diaRemover);
                System.out.println("LRU: removida série dia " + diaRemover);
            }

            
            Map<Integer, List<Evento>> seriesDia = eventoRepository.carregarEventosDia(dia);
            seriesEmMemoria.put(dia, seriesDia);
            ordemAcesso.add(dia);
            System.out.println("Carregada série dia " + dia + " para memória (" + seriesEmMemoria.size() + "/" + S + ")");

            
            List<Evento> eventos = seriesDia.get(produtoID);
            return agregarEventos(eventos);
            
        } finally {
            writeLock.unlock();
        }
    }

    
    private Agregacao agregarEventos(List<Evento> eventos) {
        Agregacao agregacao = new Agregacao();
        if (eventos != null) {
            for (Evento e : eventos) {
                agregacao.update(e.getQuantidade(), e.getPreco());
            }
        }
        agregacao.updatePrecoMedio();
        return agregacao;
    }

    
    public void limparAgregacoesDia(int dia) {
        writeLock.lock();
        try {
            boolean removeu = false;
            for (Map<Integer, Agregacao> cacheProduto : cacheAgregacoes.values()) {
                if (cacheProduto.remove(dia) != null) {
                    removeu = true;
                }
            }
            if (removeu) {
                System.out.println("Agregações do dia " + dia + " removidas da cache");
            }
        } finally {
            writeLock.unlock();
        }
    }

    
    public void removerSerieDaMemoria(int dia) {
        writeLock.lock();
        try {
            boolean removeu = seriesEmMemoria.remove(dia) != null;
            ordemAcesso.remove(Integer.valueOf(dia));
            if (removeu) {
                System.out.println("Série do dia " + dia + " removida da memória");
            }
        } finally {
            writeLock.unlock();
        }
    }

    
    public void limparDia(int dia) {
        writeLock.lock();
        try {
            limparAgregacoesDia(dia);
            removerSerieDaMemoria(dia);
        } finally {
            writeLock.unlock();
        }
    }

    
    public void limparTudo() {
        writeLock.lock();
        try {
            cacheAgregacoes.clear();
            seriesEmMemoria.clear();
            ordemAcesso.clear();
            System.out.println("Cache completamente limpa");
        } finally {
            writeLock.unlock();
        }
    }

    
    public String obterEstatisticas() {
        readLock.lock();
        try {
            int totalAgregacoes = cacheAgregacoes.values().stream()
                .mapToInt(Map::size).sum();

            int totalEventos = seriesEmMemoria.values().stream()
                .flatMap(serie -> serie.values().stream())
                .mapToInt(List::size).sum();

            return String.format(
                "Cache: %d produtos, %d agregações, %d/%d séries em memória (%d eventos)",
                cacheAgregacoes.size(), totalAgregacoes, 
                seriesEmMemoria.size(), S, totalEventos
            );
        } finally {
            readLock.unlock();
        }
    }
    
    public Agregacao getAgregacaoSeExistir(int produtoID, int dia) {
        readLock.lock();
        try {
            Map<Integer, Agregacao> porProduto = cacheAgregacoes.get(produtoID);
            if (porProduto == null) return null;
            return porProduto.get(dia);
        } finally {
            readLock.unlock();
        }
    }
}