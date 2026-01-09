package common.interfaces;

import common.dto.EventoDTO;
import common.dto.FiltrarEventosDTO;
import common.dto.NotificacaoDTO;
import common.dto.RespostaDTO;
import common.exceptions.EventoException;


public interface IServicoEventos {
    
    
    RespostaDTO registrarEvento(EventoDTO evento) throws EventoException;
    
    
    RespostaDTO notificarVendaEspecifica(NotificacaoDTO notificacao) throws EventoException;

    
    RespostaDTO notificarVendasConsecutivas(NotificacaoDTO notificacao) throws EventoException;

    
    RespostaDTO filtrarEventos(FiltrarEventosDTO filtro) throws EventoException;
    
    
    RespostaDTO listarEventosDiaAtual() throws EventoException;
    
    
    RespostaDTO novoDia() throws EventoException;

    
}