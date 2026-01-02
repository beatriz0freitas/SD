package common.interfaces;

import common.dto.EventoDTO;
import common.dto.FiltrarEventosDTO;
import common.dto.NotificacaoDTO;
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
     * Notifica quando ocorrerem vendas específicas de dois produtos no dia atual
     * @param notificacao DTO contendo os IDs dos dois produtos
     * @return true quando ambos os produtos forem vendidos
     * @throws EventoException se ocorrer erro na notificação
     */
    RespostaDTO notificarVendaEspecifica(NotificacaoDTO notificacao) throws EventoException;

    /**
     * Notifica quando um produto atingir um número de vendas consecutivas no dia atual
     * @param notificacao DTO contendo o ID do produto e o número de vendas consecutivas
     * @return resposta confirmando notificação
     * @throws EventoException se ocorrer erro na notificação
     */
    RespostaDTO notificarVendasConsecutivas(NotificacaoDTO notificacao) throws EventoException;

    /**
     * Filtra eventos de uma série temporal específica
     * @param filtro conjunto de produtos e dia anterior
     * @return eventos filtrados
     * @throws EventoException se parâmetros inválidos
     */
    RespostaDTO filtrarEventos(FiltrarEventosDTO filtro) throws EventoException;
    
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