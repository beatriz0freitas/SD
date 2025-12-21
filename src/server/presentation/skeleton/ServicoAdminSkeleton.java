package server.presentation.skeleton;

import common.dto.RespostaDTO;
import server.business.services.ServicoAdmin;

/**
 * Skeleton para serviço administrativo
 */
public class ServicoAdminSkeleton implements ISkeleton {
    private final ServicoAdmin servico;
    
    public ServicoAdminSkeleton(ServicoAdmin servico) {
        this.servico = servico;
    }
    
    public RespostaDTO processarRequisicao(String operacao, Object parametros) {
        try {
            switch (operacao) {
                case "ADMIN:LISTAR_CLIENTES":
                    return servico.listarClientes();
                    
                case "ADMIN:ESTATISTICAS":
                    return servico.obterEstatisticas();
                    
                default:
                    return RespostaDTO.erro("Operação desconhecida: " + operacao);
            }
        } catch (Exception e) {
            return RespostaDTO.erro("Erro no servidor: " + e.getMessage());
        }
    }
}