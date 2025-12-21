package server.presentation.skeleton;

import common.dto.RespostaDTO;
import middleware.proto.Requisicao;

/**
 * Dispatcher que roteia requisições para os skeletons apropriados
 * 
 * Não achei muito importante ter um map de skeletons pois assim fica masi simples
 * 
 * Para o foturo: implementar um hash de skeletons baseados na operacao que podemos trocar para um enum para ser mais facil.
 */
public class RequestDispatcher {
    private final ServicoAutenticacaoSkeleton skeletonAuth;
    private final ServicoEventosSkeleton skeletonEventos;
    private final ServicoAgregacoesSkeleton skeletonAgregacoes;
    private final ServicoAdminSkeleton skeletonAdmin;
    
    public RequestDispatcher(
            ServicoAutenticacaoSkeleton skeletonAuth,
            ServicoEventosSkeleton skeletonEventos,
            ServicoAgregacoesSkeleton skeletonAgregacoes,
            ServicoAdminSkeleton skeletonAdmin) {
        
        this.skeletonAuth = skeletonAuth;
        this.skeletonEventos = skeletonEventos;
        this.skeletonAgregacoes = skeletonAgregacoes;
        this.skeletonAdmin = skeletonAdmin;
    }
    
    /**
     * Despacha requisição para o skeleton apropriado
     */
    public RespostaDTO despachar(Requisicao requisicao) {
        String operacao = requisicao.getOperacao();
        Object parametros = requisicao.getParametros();
        
        // Roteamento baseado no prefixo da operação
        if (operacao.startsWith("AUTH:")) {
            return skeletonAuth.processarRequisicao(operacao, parametros);
            
        } else if (operacao.startsWith("EVENTO:")) {
            return skeletonEventos.processarRequisicao(operacao, parametros);
            
        } else if (operacao.startsWith("AGREGACAO:")) {
            return skeletonAgregacoes.processarRequisicao(operacao, parametros);
            
        } else if (operacao.startsWith("ADMIN:")) {
            return skeletonAdmin.processarRequisicao(operacao, parametros);
            
        } else {
            return RespostaDTO.erro("Operação desconhecida: " + operacao);
        }
    }
}