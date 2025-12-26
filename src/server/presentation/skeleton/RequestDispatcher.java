package server.presentation.skeleton;

import common.dto.RespostaDTO;
import java.util.Map;
import static middleware.Protocolos.*;
import middleware.Requisicao;
import server.business.services.*;

/**
 * Cria serviços e skeletons e despacha pedidos para o skeleton correto
 */
public class RequestDispatcher {
    private final Map<Byte, ISkeleton> skeletonsPorServico;
    
    private RequestDispatcher(Map<Byte, ISkeleton> skeletons) {
        this.skeletonsPorServico = skeletons;
    }
    
    public static RequestDispatcher criar(int D) {
        // Criar serviços
        ServicoAutenticacao servicoAuth = new ServicoAutenticacao();
        ServicoEventos servicoEventos = new ServicoEventos(D);
        ServicoAgregacoes servicoAgregacoes = new ServicoAgregacoes(servicoEventos, D);
        ServicoAdmin servicoAdmin = new ServicoAdmin();
        
        // Criar skeletons e mapear
        Map<Byte, ISkeleton> skeletons = Map.of(
            SERVICO_AUTENTICACAO, new ServicoAutenticacaoSkeleton(servicoAuth),
            SERVICO_EVENTOS, new ServicoEventosSkeleton(servicoEventos),
            SERVICO_AGREGACOES, new ServicoAgregacoesSkeleton(servicoAgregacoes),
            SERVICO_ADMIN, new ServicoAdminSkeleton(servicoAdmin)
        );
        
        return new RequestDispatcher(skeletons);
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