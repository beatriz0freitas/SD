package server.presentation.skeleton;

import common.dto.RespostaDTO;
import java.util.Map;
import static middleware.Protocolos.*;
import middleware.Requisicao;
import server.business.services.*;
import server.data.cache.CacheManager;
import server.data.repository.IEventoRepository;
import server.data.repository.RepositoryFactory;

/**
 * Cria serviços e skeletons e despacha pedidos para o skeleton correto
 */
public class RequestDispatcher {
    private final Map<Byte, ISkeleton> skeletonsPorServico;
    
    private RequestDispatcher(Map<Byte, ISkeleton> skeletons) {
        this.skeletonsPorServico = skeletons;
    }
    
    public static RequestDispatcher criar(int D, int S) {
        // Obter repository
        IEventoRepository eventoRepository = RepositoryFactory.getInstance().getEventoRepository();
        
        // Criar cache manager
        CacheManager cacheManager = new CacheManager(eventoRepository, S);

        // Criar serviços (injeção de dependências via construtor)
        ServicoAutenticacao servicoAuth = new ServicoAutenticacao();
        ServicoEventos servicoEventos = new ServicoEventos(eventoRepository, cacheManager, D);
        ServicoAgregacoes servicoAgregacoes = new ServicoAgregacoes(cacheManager, servicoEventos, eventoRepository, D);
        ServicoAdmin servicoAdmin = new ServicoAdmin();
        
        // Criar skeletons e mapear
        Map<Byte, ISkeleton> skeletons = Map.of(
            SERVICO_AUTENTICACAO, new ServicoAutenticacaoSkeleton(servicoAuth),
            SERVICO_EVENTOS, new ServicoEventosSkeleton(servicoEventos),
            SERVICO_AGREGACOES, new ServicoAgregacoesSkeleton(servicoAgregacoes),
            SERVICO_ADMIN, new ServicoAdminSkeleton(servicoAdmin)
        );
        
        System.out.println("RequestDispatcher criado com D=" + D + " e S=" + S);
        
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