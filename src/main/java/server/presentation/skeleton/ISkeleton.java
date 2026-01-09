package server.presentation.skeleton;

import common.dto.RespostaDTO;


public interface ISkeleton {
    RespostaDTO processarRequisicao(byte methodId, Object parametros);
}