package server.presentation.skeleton;

import static middleware.MessageTypes.*;

import common.dto.RespostaDTO;
import server.business.services.ServicoAdmin;

public class ServicoAdminSkeleton implements ISkeleton {
    private final ServicoAdmin servico; 

    public ServicoAdminSkeleton(ServicoAdmin servico) {
        this.servico = servico;
    }

    @Override
    public RespostaDTO processarRequisicao(byte methodId, Object parametros) {
        try {
            switch (methodId) {
                case ADMIN_LISTAR_CLIENTES:
                    return servico.listarClientes();
                case ADMIN_ESTATISTICAS:
                    return servico.obterEstatisticas();
                case ADMIN_METRICAS:
                    return servico.obterMetricas();
                default:
                    return RespostaDTO.erro("Método desconhecido: " + methodId);
            }
        } catch (Exception e) {
            return RespostaDTO.erro("Erro no servidor: " + e.getMessage());
        }
    }
}