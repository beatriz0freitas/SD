package server.presentation.skeleton;

import common.dto.RespostaDTO;

/**
 * Skeleton recebe parâmetros já decodificados (DTO concreto ou null).
 */
public interface ISkeleton {
    RespostaDTO processarRequisicao(byte methodId, Object parametros);
}