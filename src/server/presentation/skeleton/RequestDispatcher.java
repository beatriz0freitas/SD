package server.presentation.skeleton;

import common.dto.RespostaDTO;
import java.util.Map;
import static middleware.proto.Protocolos.*;
import middleware.proto.Requisicao;
import server.business.services.*;

public class RequestDispatcher {
    private final Map<Byte, ISkeleton> skeletonsPorServico;
    
    private RequestDispatcher(
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
    
    /**
     * Factory method - cria dispatcher com serviços configurados
     * Usa CLASSES CONCRETAS porque não há múltiplas implementações no servidor
     */
    public static RequestDispatcher criar(int D) {
        // Criar serviços - CLASSES CONCRETAS
        ServicoAutenticacao servicoAuth = new ServicoAutenticacao();
        ServicoEventos servicoEventos = new ServicoEventos(D);
        ServicoAgregacoes servicoAgregacoes = new ServicoAgregacoes(servicoEventos, D);
        ServicoAdmin servicoAdmin = new ServicoAdmin();
        
        // Criar skeletons - passam classes concretas
        ServicoAutenticacaoSkeleton skeletonAuth = 
            new ServicoAutenticacaoSkeleton(servicoAuth);
        ServicoEventosSkeleton skeletonEventos = 
            new ServicoEventosSkeleton(servicoEventos);
        ServicoAgregacoesSkeleton skeletonAgregacoes = 
            new ServicoAgregacoesSkeleton(servicoAgregacoes);
        ServicoAdminSkeleton skeletonAdmin = 
            new ServicoAdminSkeleton(servicoAdmin);
        
        return new RequestDispatcher(
            skeletonAuth, skeletonEventos, skeletonAgregacoes, skeletonAdmin
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