package server.presentation.skeleton;

import common.dto.EventoDTO;
import common.dto.RespostaDTO;
import server.business.services.ServicoEventos;

/**
 * Skeleton para serviço de eventos
 */
public class ServicoEventosSkeleton implements ISkeleton{
    private final ServicoEventos servico;
    
    public ServicoEventosSkeleton(ServicoEventos servico) {
        this.servico = servico;
    }
    
    public RespostaDTO processarRequisicao(String operacao, Object parametros) {
        try {
            switch (operacao) {
                case "EVENTO:REGISTRAR":
                    return servico.registrarEvento((EventoDTO) parametros);
                    
                case "EVENTO:LISTAR":
                    return servico.listarEventosDiaAtual();
                    
                case "EVENTO:NOVO_DIA":
                    return servico.novoDia();
                    
                default:
                    return RespostaDTO.erro("Operação desconhecida: " + operacao);
            }
        } catch (Exception e) {
            return RespostaDTO.erro("Erro no servidor: " + e.getMessage());
        }
    }
}