package server.presentation.skeleton;

import common.dto.RespostaDTO;
import java.util.Map;
import static middleware.proto.Protocolos.*;
import middleware.proto.Requisicao;

public class RequestDispatcher {
    private final Map<Byte, ISkeleton> skeletonsPorServico;
    
    public RequestDispatcher(
            ServicoAutenticacaoSkeleton skeletonAuth,
            ServicoEventosSkeleton skeletonEventos,
            ServicoAgregacoesSkeleton skeletonAgregacoes,
            ServicoAdminSkeleton skeletonAdmin) {
        
        this.skeletonsPorServico = Map.of(
            SERVICO_AUTENTICACAO, skeletonAuth,
            SERVICO_EVENTOS, skeletonEventos,
            SERVICO_AGREGACOES, skeletonAgregacoes,
            SERVICO_ADMIN, skeletonAdmin
        );
    }
    
    public RespostaDTO despachar(Requisicao requisicao) {
        ISkeleton skeleton = skeletonsPorServico.get(requisicao.getServiceId());
        
        if (skeleton == null) {
            return RespostaDTO.erro("Serviço desconhecido: " + requisicao.getServiceId());
        }
        
        return skeleton.processarRequisicao(
            requisicao.getMethodId(), 
            requisicao.getParametros()
        );
    }
}