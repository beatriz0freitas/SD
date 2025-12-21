package client.Stub;

import client.ClienteMiddleware;
import common.dto.EventoDTO;
import common.dto.RespostaDTO;
import common.exceptions.EventoException;
import common.interfaces.IServicoEventos;

/**
 * Stub (Stub) para serviço de eventos
 */
public class ServicoEventosStub implements IServicoEventos {
    private final ClienteMiddleware middleware;
    
    public ServicoEventosStub(ClienteMiddleware middleware) {
        this.middleware = middleware;
    }
    
    @Override
    public RespostaDTO registrarEvento(EventoDTO evento) throws EventoException {
        try {
            return middleware.invocar("EVENTO:REGISTRAR", evento);
        } catch (Exception e) {
            throw new EventoException("Erro ao registrar evento: " + e.getMessage(), e);
        }
    }
    
    @Override
    public RespostaDTO listarEventosDiaAtual() throws EventoException {
        try {
            return middleware.invocar("EVENTO:LISTAR", null);
        } catch (Exception e) {
            throw new EventoException("Erro ao listar eventos: " + e.getMessage(), e);
        }
    }
    
    @Override
    public RespostaDTO novoDia() throws EventoException {
        try {
            return middleware.invocar("EVENTO:NOVO_DIA", null);
        } catch (Exception e) {
            throw new EventoException("Erro ao avançar dia: " + e.getMessage(), e);
        }
    }
}
