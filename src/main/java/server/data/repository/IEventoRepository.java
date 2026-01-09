package server.data.repository;

import java.util.List;
import java.util.Map;

import server.business.domain.Agregacao;
import server.business.domain.Evento;


public interface IEventoRepository {
    
    void salvarEventosDia(int dia, Map<Integer, List<Evento>> eventosPorProduto);
    
    
    Map<Integer, List<Evento>> carregarEventosDia(int dia);
    
    
    Agregacao agregarEventosDia(int produtoID, int dia);
    
    
    int obterUltimoDia();
    
    
    boolean existeDia(int dia);

    
    Agregacao agregarEventosMultiDia(int produtoID, int diaInicio, int diaFim);
}