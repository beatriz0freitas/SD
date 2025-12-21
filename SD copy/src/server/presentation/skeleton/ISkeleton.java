package server.presentation.skeleton;

import common.dto.RespostaDTO;

public interface ISkeleton {
    RespostaDTO processarRequisicao(String operacao, Object parametros);
}