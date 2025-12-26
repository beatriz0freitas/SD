package server.presentation.skeleton;

import static middleware.Protocolos.*;

import common.dto.EventoDTO;
import common.dto.RespostaDTO;
import server.business.services.ServicoEventos;

public class ServicoEventosSkeleton implements ISkeleton {
    private final ServicoEventos servico; 

    public ServicoEventosSkeleton(ServicoEventos servico) {
        this.servico = servico;
    }

    @Override
    public RespostaDTO processarRequisicao(byte methodId, Object parametros) {
        try {
            switch (methodId) {
                case EVENTO_REGISTRAR:
                    return servico.registrarEvento((EventoDTO) parametros);
                case EVENTO_LISTAR:
                    return servico.listarEventosDiaAtual();
                case EVENTO_NOVO_DIA:
                    return servico.novoDia();
                default:
                    return RespostaDTO.erro("Método desconhecido: " + methodId);
            }
        } catch (Exception e) {
            return RespostaDTO.erro("Erro no servidor: " + e.getMessage());
        }
    }
}