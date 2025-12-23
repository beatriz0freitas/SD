package common.interfaces;

import common.dto.EventoDTO;
import common.dto.RespostaDTO;
import common.exceptions.EventoException;

/**
 * Interface remota para gestão de eventos de vendas
 */
public interface IServicoEventos {
    
    /**
     * Registra novo evento de venda
     * @param evento dados do evento
     * @return resposta confirmando registro
     * @throws EventoException se dados inválidos
     */
    RespostaDTO registrarEvento(EventoDTO evento) throws EventoException;
    
    /**
     * Lista eventos do dia atual (apenas admin)
     * @return lista de eventos
     * @throws EventoException se não autorizado
     */
    RespostaDTO listarEventosDiaAtual() throws EventoException;
    
    /**
     * Avança para novo dia (apenas admin)
     * @return resposta confirmando avanço
     * @throws EventoException se não autorizado
     */
    RespostaDTO novoDia() throws EventoException;

    
}