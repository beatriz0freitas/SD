package server.presentation.skeleton;

import static middleware.MessageTypes.*;

import java.util.Map;

import common.dto.RespostaDTO;
import common.PerformanceMetrics;
import middleware.Message;
import server.business.services.*;
import server.data.cache.CacheManager;
import server.data.repository.IEventoRepository;
import server.data.repository.RepositoryFactory;

/**
 * Cria serviços e skeletons e despacha pedidos para o skeleton correto
 * Integra métricas de performance
 */
public class RequestDispatcher {
    private final Map<Byte, ISkeleton> skeletonsPorServico;
    private final ServicoEventos servicoEventos;
    private final PerformanceMetrics metrics;
    
    private RequestDispatcher(Map<Byte, ISkeleton> skeletons, ServicoEventos servicoEventos) {
        this.skeletonsPorServico = skeletons;
        this.servicoEventos = servicoEventos;
        this.metrics = PerformanceMetrics.getInstance();
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
        return new RequestDispatcher(skeletons, servicoEventos);
    }
    
    /**
     * Despacha pedido com medição de performance
     */
    public RespostaDTO despachar(Message msg) {
        long inicio = System.nanoTime();
        boolean sucesso = false;
        
        try {
            ISkeleton skeleton = skeletonsPorServico.get(msg.getServiceId());
            
            if (skeleton == null) {
                return RespostaDTO.erro("Serviço desconhecido: " + msg.getServiceId());
            }
            
            RespostaDTO resposta = skeleton.processarRequisicao(
                msg.getMethodId(), 
                msg.getPayload()
            );
            
            sucesso = resposta.isSucesso();
            return resposta;
            
        } finally {
            // Registar métricas
            long latencia = System.nanoTime() - inicio;
            metrics.recordRequest(sucesso, latencia);
        }
    }
    
    /**
     * Encerra recursos do dispatcher
     */
    public void shutdown() {
        System.out.println("Encerrando RequestDispatcher...");
        
        // Encerrar serviços que têm recursos
        if (servicoEventos != null) {
            servicoEventos.shutdown();
        }
        
        System.out.println("RequestDispatcher encerrado");
    }
}