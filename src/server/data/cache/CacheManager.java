package server.data.cache;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import server.business.domain.Agregacao;
import server.business.domain.Evento;
import server.data.repository.IEventoRepository;

/**
 * Gerenciador de cache para agregações
 * Mantém no máximo S séries em memória
 * Processa séries incrementalmente quando memória está cheia
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
     * Usa cache se disponível, senão calcula
     */
    public Agregacao obterAgregacaoDia(int produtoID, int dia) {
        // Primeiro tenta ler da cache de agregações
        readLock.lock();
        try {
            if (cacheAgregacoes.containsKey(produtoID)) {
                Agregacao existente = cacheAgregacoes.get(produtoID).get(dia);
                if (existente != null) {
                    System.out.println("Cache HIT: produto=" + produtoID + " dia=" + dia);
                    return existente;
                }
            }
        } finally {
            readLock.unlock();
        }

        // Não está em cache, precisa calcular
        writeLock.lock();
        try {
            // Double-check
            if (cacheAgregacoes.containsKey(produtoID)) {
                Agregacao existente = cacheAgregacoes.get(produtoID).get(dia);
                if (existente != null) {
                    return existente;
                }
            }

            // Calcular agregação do dia
            Agregacao agregacao = calcularAgregacaoDia(produtoID, dia);
            
            // Adicionar à cache
            if (!cacheAgregacoes.containsKey(produtoID)) {
                cacheAgregacoes.put(produtoID, new HashMap<>());
            }
            cacheAgregacoes.get(produtoID).put(dia, agregacao);
            
            System.out.println("Cache MISS: produto=" + produtoID + " dia=" + dia);
            
            return agregacao;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Calcula agregação de um produto em um dia
     * ESTRATÉGIA INTELIGENTE:
     * - Se há espaço em memória: carrega série e mantém
     * - Se memória cheia: processa incrementalmente do disco (streaming)
     */
    private Agregacao calcularAgregacaoDia(int produtoID, int dia) {
        // Verificar se série já está em memória
        if (seriesEmMemoria.containsKey(dia)) {
            // Atualizar LRU
            ordemAcesso.remove(Integer.valueOf(dia));
            ordemAcesso.add(dia);
            
            // Usar série da memória
            Map<Integer, List<Evento>> seriesDia = seriesEmMemoria.get(dia);
            List<Evento> eventos = seriesDia.get(produtoID);
            return agregarEventos(eventos);
        }

        // Série não está em memória
        // DECISÃO: carregar para memória OU processar em streaming?
        
        if (seriesEmMemoria.size() < S) {
            // HÁ ESPAÇO - Carregar série para memória
            return calcularComCarregamento(produtoID, dia);
        } else {
            // MEMÓRIA CHEIA - Processar em streaming (sem adicionar à memória)
            return calcularEmStreaming(produtoID, dia);
        }
    }

    /**
     * Carrega série para memória (quando há espaço)
     * Remove série LRU se necessário
     */
    private Agregacao calcularComCarregamento(int produtoID, int dia) {
        // Verificar se precisa remover série antiga
        if (seriesEmMemoria.size() >= S) {
            int diaRemover = ordemAcesso.remove(0);
            seriesEmMemoria.remove(diaRemover);
            System.out.println("Série do dia " + diaRemover + " removida (limite S=" + S + ")");
        }

        // Carregar série do disco
        Map<Integer, List<Evento>> seriesDia = eventoRepository.carregarEventosDia(dia);
        seriesEmMemoria.put(dia, seriesDia);
        ordemAcesso.add(dia);
        System.out.println("Série do dia " + dia + " carregada do disco para memória");

        // Agregar eventos do produto
        List<Evento> eventos = seriesDia.get(produtoID);
        return agregarEventos(eventos);
    }

    /**
     * Processa série em streaming do disco (SEM adicionar à memória)
     * Usado quando já há S séries em memória
     */
    private Agregacao calcularEmStreaming(int produtoID, int dia) {
        System.out.println("STREAMING: processando dia " + dia + " sem adicionar à memória (S=" + S + " cheio)");
        
        // Carregar série do disco
        Map<Integer, List<Evento>> seriesDia = eventoRepository.carregarEventosDia(dia);
        
        // Processar eventos do produto
        List<Evento> eventos = seriesDia.get(produtoID);
        Agregacao resultado = agregarEventos(eventos);
        
        // Série é descartada automaticamente (não adicionada à memória)
        System.out.println("Série do dia " + dia + " processada e descartada (streaming)");
        
        return resultado;
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
     * Remove todas as estruturas de um dia (agregações + série)
     */
    public void limparDia(int dia) {
        writeLock.lock();
        try {
            // Remover agregações
            boolean removeuAgregacao = false;
            for (Map<Integer, Agregacao> cacheProduto : cacheAgregacoes.values()) {
                if (cacheProduto.remove(dia) != null) {
                    removeuAgregacao = true;
                }
            }
            
            // Remover série da memória
            boolean removeuSerie = seriesEmMemoria.remove(dia) != null;
            if (removeuSerie) {
                ordemAcesso.remove(Integer.valueOf(dia));
            }
            
            if (removeuAgregacao || removeuSerie) {
                System.out.println("Dia " + dia + " completamente removido da cache");
            }
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
            int totalAgregacoes = 0;
            for (Map<Integer, Agregacao> cacheProduto : cacheAgregacoes.values()) {
                totalAgregacoes += cacheProduto.size();
            }

            int totalEventos = 0;
            for (Map<Integer, List<Evento>> serie : seriesEmMemoria.values()) {
                for (List<Evento> eventos : serie.values()) {
                    totalEventos += eventos.size();
                }
            }

            return String.format(
                "Cache: %d produtos, %d agregações, %d séries em memória (%d eventos)",
                cacheAgregacoes.size(), totalAgregacoes, seriesEmMemoria.size(), totalEventos
            );
        } finally {
            readLock.unlock();
        }
    }
}