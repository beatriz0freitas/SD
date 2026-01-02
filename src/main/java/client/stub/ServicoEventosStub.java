package client.stub;

import client.ClienteMiddleware;
import common.ErrorLogger;
import common.dto.*;
import common.exceptions.EventoException;
import common.interfaces.IServicoEventos;
import middleware.MessageTypes;

public class ServicoEventosStub implements IServicoEventos {
    private final ClienteMiddleware middleware;

    public ServicoEventosStub(ClienteMiddleware middleware) {
        this.middleware = middleware;
    }

    @Override
    public RespostaDTO registrarEvento(EventoDTO evento) throws EventoException {
        try {
            return middleware.invocar(
                MessageTypes.SERVICO_EVENTOS,
                MessageTypes.EVENTO_REGISTRAR,
                evento
            );
        } catch (Exception e) {
            throw new EventoException("Erro ao registrar evento: " + e.getMessage(), e);
        }
    }

    @Override
    public RespostaDTO notificarVendaEspecifica(NotificacaoDTO notificacao) throws EventoException {
        try {
            return middleware.invocar(
                MessageTypes.SERVICO_EVENTOS,
                MessageTypes.EVENTO_NOTIFICAR_VENDA_ESPECIFICA,
                notificacao
            );
        } catch (Exception e) {
            throw new EventoException("Erro ao notificar venda específica: " + e.getMessage(), e);
        }
    }

    @Override
    public RespostaDTO notificarVendasConsecutivas(NotificacaoDTO notificacao) throws EventoException {
        try {
            return middleware.invocar(
                MessageTypes.SERVICO_EVENTOS,
                MessageTypes.EVENTO_NOTIFICAR_VENDAS_CONSECUTIVAS,
                notificacao
            );
        } catch (Exception e) {
            throw new EventoException("Erro ao notificar vendas consecutivas: " + e.getMessage(), e);
        }
    }

    @Override
    public RespostaDTO filtrarEventos(FiltrarEventosDTO filtro) throws EventoException {
        try {
            return middleware.invocar(
                MessageTypes.SERVICO_EVENTOS,
                MessageTypes.EVENTO_FILTRAR,
                filtro
            );
        } catch (Exception e) {
            throw new EventoException("Erro ao filtrar eventos: " + e.getMessage(), e);
        }
    }

    @Override
    public RespostaDTO listarEventosDiaAtual() throws EventoException {
        try {
            return middleware.invocar(
                MessageTypes.SERVICO_EVENTOS,
                MessageTypes.EVENTO_LISTAR,
                null
            );
        } catch (Exception e) {
            throw new EventoException("Erro ao listar eventos: " + e.getMessage(), e);
        }
    }

    @Override
    public RespostaDTO novoDia() throws EventoException {
        try {
            return middleware.invocar(
                MessageTypes.SERVICO_EVENTOS,
                MessageTypes.EVENTO_NOVO_DIA,
                null
            );
        } catch (Exception e) {
            ErrorLogger.getInstance().logError("ServicoEventos.novoDia", e);
            throw new EventoException("Erro ao avançar dia: " + e.getMessage(), e);
        }
    }
}