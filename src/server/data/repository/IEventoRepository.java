package server.data.repository;

import java.util.List;
import java.util.Map;
import server.business.domain.Agregacao;
import server.business.domain.Evento;

/**
 * Interface Repository para operações de persistência de eventos
 */
public interface IEventoRepository {
    /**
     * Salva eventos de um dia específico
     */
    void salvarEventosDia(int dia, Map<Integer, List<Evento>> eventosPorProduto);
    
    /**
     * Carrega eventos de um dia
     */
    Map<Integer, List<Evento>> carregarEventosDia(int dia);
    
    /**
     * Agrega eventos de um produto em um dia específico
     */
    Agregacao agregarEventosDia(int produtoID, int dia);
    
    /**
     * Obtém o último dia registrado
     */
    int obterUltimoDia();
    
    /**
     * Verifica se existe arquivo para um dia
     */
    boolean existeDia(int dia);
}