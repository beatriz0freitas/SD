package server.data.cache;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import common.PerformanceMetrics;
import server.business.domain.Agregacao;
import server.business.domain.Evento;
import server.data.repository.IEventoRepository;

/**
 * Gerenciador de cache para agregações
 * Mantém no máximo S séries em memória (LRU)
 * 
 * Se memória cheia (S séries), usa STREAMING ao invés de carregar para memória
 * Respeita especificação: "informação lida do disco deve ir sendo processada 
 *   e descartada ao longo da agregação de modo a não exceder o limite S"
 */
public class CacheManager {
    private final IEventoRepository eventoRepository;
    private final int S; // Máximo de séries em memória

    // Cache: produtoID -> dia -> Agregacao
    private final Map<Integer, Map<Integer, Agregacao>> cacheAgregacoes = new HashMap<>();
    
    // Séries em memória: dia -> Map<produtoID, List<Evento>>
    private final Map<Integer, Map<Integer, List<Evento>>> seriesEmMemoria = new HashMap<>();
    
    // Lista para controlar ordem de acesso (LRU)
    private final List<Integer> ordemAcesso = new ArrayList<>();

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    public CacheManager(IEventoRepository eventoRepository, int S) {
        this.eventoRepository = eventoRepository;
        this.S = S;
    }

    /**
     * Obtém agregação de UM dia específico para um produto
     * Usa streaming se memória cheia
     */
    public Agregacao obterAgregacaoDia(int produtoID, int dia) {

        // ===== FASE 1: tentativa rápida (READ LOCK) =====
        readLock.lock();
        try {
            Map<Integer, Agregacao> porProduto = cacheAgregacoes.get(produtoID);
            if (porProduto != null) {
                Agregacao existente = porProduto.get(dia);
                if (existente != null) {
                    PerformanceMetrics.getInstance().recordCacheHit();
                    return existente;
                }
                // Quando não existe
                PerformanceMetrics.getInstance().recordCacheMiss();
            }
        } finally {
            readLock.unlock();
        }
    
        // ===== FASE 2: cálculo SEM locks =====
        Agregacao calculada;
    
        boolean usarStreaming;
        readLock.lock();
        try {
            usarStreaming = seriesEmMemoria.size() >= S && !seriesEmMemoria.containsKey(dia);
        } finally {
            readLock.unlock();
        }
    
        if (usarStreaming) {
            System.out.println("STREAMING dia " + dia + " (memória cheia, S=" + S + ")");
            calculada = eventoRepository.agregarEventosDia(produtoID, dia);
        } else {
            calculada = calcularComMemoria(produtoID, dia);
        }
    
        // ===== FASE 3: inserir no cache (WRITE LOCK) =====
        writeLock.lock();
        try {
            // Double-check (outra thread pode ter inserido entretanto)
            Map<Integer, Agregacao> porProduto =
                cacheAgregacoes.computeIfAbsent(produtoID, k -> new HashMap<>());
    
            Agregacao existente = porProduto.get(dia);
            if (existente != null) {
                return existente;
            }
    
            porProduto.put(dia, calculada);
            System.out.println("Cache MISS: produto=" + produtoID + " dia=" + dia);
            return calculada;
    
        } finally {
            writeLock.unlock();
        }
    }
    

    /**
    * Calcula agregação usando memória (série já está OU há espaço)
    */
    private Agregacao calcularComMemoria(int produtoID, int dia) {
        // 1. Verificar se série já está em memória
        if (seriesEmMemoria.containsKey(dia)) {
            // Atualizar LRU (mover para final)
            ordemAcesso.remove(dia);
            ordemAcesso.add(dia);

            List<Evento> eventos = seriesEmMemoria.get(dia).get(produtoID);
            return agregarEventos(eventos);
        }

        // 2. Série não está - precisa carregar do disco
        // Se memória cheia, remover LRU
        if (seriesEmMemoria.size() >= S) {
            Iterator<Integer> it = ordemAcesso.iterator();
            int diaRemover = it.next();
            it.remove();
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


