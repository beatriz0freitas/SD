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
 * Gestor de cache para agregações
 * Mantém no máximo S séries em memória (LRU)
 * Integrado com sistema de métricas
 */
public class CacheManager {
    private final IEventoRepository eventoRepository;
    private final int S; // Máximo de séries em memória
    private final PerformanceMetrics metrics;

    // Cache: produtoID -> dia -> Agregacao
    private final Map<Integer, Map<Integer, Agregacao>> cacheAgregacoes = new HashMap<>();
    
    // Séries em memória: dia -> Map<produtoID, List<Evento>>
    private final Map<Integer, Map<Integer, List<Evento>>> seriesEmMemoria = new HashMap<>();
    
    // Lista para controlar ordem de acesso (LRU)
    private final List<Integer> ordemAcesso = new ArrayList<>();

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    private final Map<String, ReentrantLock> computationLocks = new HashMap<>();
    private final ReentrantLock computationLocksLock = new ReentrantLock();

    public CacheManager(IEventoRepository eventoRepository, int S) {
        this.eventoRepository = eventoRepository;
        this.S = S;
        this.metrics = PerformanceMetrics.getInstance();
    }

    /**
     * Obtém agregação de UM dia específico para um produto
     * Usa streaming se memória cheia
     */
    public Agregacao obterAgregacaoDia(int produtoID, int dia) {
        // FASE 1: Tentativa rápida (READ LOCK)
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

        //Obter computation lock específico para esta chave
        String computationKey = produtoID + ":" + dia;
        ReentrantLock compLock = getComputationLock(computationKey);

        compLock.lock();
        try {
            // Double-check: outra thread pode ter calculado enquanto esperávamos
            readLock.lock();
            try {
                Map<Integer, Agregacao> porProduto = cacheAgregacoes.get(produtoID);
                if (porProduto != null) {
                    Agregacao existente = porProduto.get(dia);
                    if (existente != null) {
                        return existente; // Outra thread já calculou
                    }
                }
            } finally {
                readLock.unlock();
            }

            // FASE 2: Cálculo (SEM locks de cache, só computation lock)
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

            // FASE 3: Inserir no cache (WRITE LOCK)
            writeLock.lock();
            try {
                Map<Integer, Agregacao> porProduto = 
                    cacheAgregacoes.computeIfAbsent(produtoID, k -> new HashMap<>());

                // Triple-check (paranóia)
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

    /**
    * Calcula agregação usando memória (série já está OU há espaço)
    */
    private Agregacao calcularComMemoria(int produtoID, int dia) {
        writeLock.lock();
        try {
            // 1. Verificar se série já está em memória
            if (seriesEmMemoria.containsKey(dia)) {
                // Atualizar LRU (mover para final)
                ordemAcesso.remove(Integer.valueOf(dia));
                ordemAcesso.add(dia);

                List<Evento> eventos = seriesEmMemoria.get(dia).get(produtoID);
                return agregarEventos(eventos);
            }

            // 2. Série não está - precisa carregar do disco
            // Se memória cheia, remover LRU
            if (seriesEmMemoria.size() >= S) {
                Integer diaRemover = ordemAcesso.remove(0);
                seriesEmMemoria.remove(diaRemover);
                System.out.println("LRU: removida série dia " + diaRemover);
            }

            // Carregar série do disco
            Map<Integer, List<Evento>> seriesDia = eventoRepository.carregarEventosDia(dia);
            seriesEmMemoria.put(dia, seriesDia);
            ordemAcesso.add(dia);
            System.out.println("Carregada série dia " + dia + " para memória (" + seriesEmMemoria.size() + "/" + S + ")");

            // Agregar eventos do produto
            List<Evento> eventos = seriesDia.get(produtoID);
            return agregarEventos(eventos);
            
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Agrega lista de eventos em uma Agregacao
     */
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

    /**
     * Limpa cache de agregações de um dia específico
     */
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

    /**
     * Remove série de um dia da memória
     */
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

    /**
     * Remove todas as estruturas de um dia
     */
    public void limparDia(int dia) {
        writeLock.lock();
        try {
            limparAgregacoesDia(dia);
            removerSerieDaMemoria(dia);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Limpa toda a cache e memória
     */
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

    /**
     * Obtém estatísticas da cache
     */
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