package server.presentation.skeleton;

import common.dto.RespostaDTO;

/**
 * Interface que todos os skeletons devem implementar
 * processando requisições binárias serviceId + methodId
 */
public interface ISkeleton {
    /**
     * Processa uma requisição remota
     *
     * @param methodId ID do método a executar
     * @param parametros Parâmetros da requisição
     * @return RespostaDTO
     */
    RespostaDTO processarRequisicao(byte methodId, Object parametros);
}
