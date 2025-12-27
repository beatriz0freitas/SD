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
 */
public class CacheManager {
    private final IEventoRepository eventoRepository;
    private final int D; // Número de dias a considerar
    private final int S; // Máximo de séries em memória

    // Cache: produtoID -> dia -> Agregacao
    private final Map<Integer, Map<Integer, Agregacao>> cacheAgregacoes = new HashMap<>();
    
    // Séries em memória: dia -> Map<produtoID, List<Evento>>
    private final Map<Integer, Map<Integer, List<Evento>>> seriesEmMemoria = new HashMap<>();
    
    // Lista para controlar ordem de acesso 
    private final List<Integer> ordemAcesso = new ArrayList<>();

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    public CacheManager(IEventoRepository eventoRepository, int D, int S) {
        this.eventoRepository = eventoRepository;
        this.D = D;
        this.S = S;
    }

    /**
     * Obtém entrada da cache para um produto em um dia
     * Se não existir, calcula e adiciona à cache
     */
    private Agregacao obterEntradaDia(int produtoID, int dia) {
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

            // Calcular agregação
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
     * Gerencia memória respeitando limite S
     */
    private Agregacao calcularAgregacaoDia(int produtoID, int dia) {
        // Verificar se série já está em memória
        if (seriesEmMemoria.containsKey(dia)) {
            // Atualizar ordem de acesso
            ordemAcesso.remove(Integer.valueOf(dia));
            ordemAcesso.add(dia);
            
            // Usar série da memória
            Map<Integer, List<Evento>> seriesDia = seriesEmMemoria.get(dia);
            List<Evento> eventos = seriesDia.get(produtoID);
            return agregarEventos(eventos);
        }

        // Série não está em memória - precisa carregar do disco
        // Verificar se excedemos limite S
        if (seriesEmMemoria.size() >= S) {
            // Remover série mais antiga (primeira da lista)
            int diaRemover = ordemAcesso.remove(0);
            seriesEmMemoria.remove(diaRemover);
            System.out.println("Série do dia " + diaRemover + " removida (limite S=" + S + ")");
        }

        // Carregar série do disco
        Map<Integer, List<Evento>> seriesDia = eventoRepository.carregarEventosDia(dia);
        seriesEmMemoria.put(dia, seriesDia);
        ordemAcesso.add(dia);
        System.out.println("Série do dia " + dia + " carregada do disco");

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
     * Obtém agregação dos últimos N dias (lazy, on-demand)
     */
    private Agregacao obterAgregacaoMultiplosDias(int produtoID, int dias) {
        int ultimoDia = eventoRepository.obterUltimoDia();
        
        if (ultimoDia < 0) {
            return new Agregacao();
        }
        
        int diasReais = Math.min(dias, ultimoDia + 1);
        
        // Acumular agregações dos últimos N dias
        Agregacao resultado = new Agregacao();

        for (int i = 0; i < diasReais; i++) {
            int dia = ultimoDia - i;
            if (dia < 0) break;

            Agregacao entradaDia = obterEntradaDia(produtoID, dia);
            if (entradaDia != null) {
                resultado.acumular(entradaDia);
            }
        }

        resultado.updatePrecoMedio();
        return resultado;
    }

    public int obterQuantidade(int produtoID, int dias) {
        return obterAgregacaoMultiplosDias(produtoID, dias).getQuantidadeVendas();
    }

    public double obterVolume(int produtoID, int dias) {
        return obterAgregacaoMultiplosDias(produtoID, dias).getVolumeVendas();
    }

    public double obterPrecoMedio(int produtoID, int dias) {
        return obterAgregacaoMultiplosDias(produtoID, dias).getPrecoMedio();
    }

    public double obterPrecoMaximo(int produtoID, int dias) {
        return obterAgregacaoMultiplosDias(produtoID, dias).getPrecoMaximo();
    }

    /**
     * Limpa cache de agregações de um dia específico
     */
    public void limparAgregacoesDia(int dia) {
        writeLock.lock();
        try {
            for (Map<Integer, Agregacao> cacheProduto : cacheAgregacoes.values()) {
                if (cacheProduto.remove(dia) != null) {
                    System.out.println("Agregações do dia " + dia + " removidas da cache");
                }
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
            if (seriesEmMemoria.remove(dia) != null) {
                ordemAcesso.remove(Integer.valueOf(dia));
                System.out.println("Série do dia " + dia + " removida da memória");
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