package server.presentation.skeleton;

import common.dto.RespostaDTO;
import java.util.Map;
import middleware.proto.Requisicao;

/**
 * Dispatcher que roteia requisições para os skeletons apropriados
 */
public class RequestDispatcher {
    private final Map<String, ISkeleton> porPrefixo;

    public RequestDispatcher(
            ServicoAutenticacaoSkeleton skeletonAuth,
            ServicoEventosSkeleton skeletonEventos,
            ServicoAgregacoesSkeleton skeletonAgregacoes,
            ServicoAdminSkeleton skeletonAdmin) {

        // registro por prefixo
        this.porPrefixo = Map.of(
            "AUTH:", skeletonAuth,
            "EVENTO:", skeletonEventos,
            "AGREGACAO:", skeletonAgregacoes,
            "ADMIN:", skeletonAdmin
        );
    }

    public RespostaDTO despachar(Requisicao requisicao) {
        String operacao = requisicao.getOperacao();
        Object parametros = requisicao.getParametros();

        for (Map.Entry<String, ISkeleton> e : porPrefixo.entrySet()) {
            if (operacao.startsWith(e.getKey())) {
                return e.getValue().processarRequisicao(operacao, parametros);
            }
        }
        return RespostaDTO.erro("Operação desconhecida: " + operacao);
    }
}