package server.presentation.skeleton;

import java.io.IOException;
import java.util.Map;

import common.PerformanceMetrics;
import common.dto.AgregacaoRequestDTO;
import common.dto.EventoDTO;
import common.dto.FiltrarEventosDTO;
import common.dto.NotificacaoDTO;
import common.dto.RespostaDTO;
import common.dto.UsuarioDTO;
import middleware.Message;
import static middleware.MessageTypes.EVENTO_FILTRAR;
import static middleware.MessageTypes.EVENTO_LISTAR;
import static middleware.MessageTypes.EVENTO_NOTIFICAR_VENDAS_CONSECUTIVAS;
import static middleware.MessageTypes.EVENTO_NOTIFICAR_VENDA_ESPECIFICA;
import static middleware.MessageTypes.EVENTO_NOVO_DIA;
import static middleware.MessageTypes.EVENTO_REGISTRAR;
import static middleware.MessageTypes.SERVICO_ADMIN;
import static middleware.MessageTypes.SERVICO_AGREGACOES;
import static middleware.MessageTypes.SERVICO_AUTENTICACAO;
import static middleware.MessageTypes.SERVICO_EVENTOS;
import server.business.services.ServicoAdmin;
import server.business.services.ServicoAgregacoes;
import server.business.services.ServicoAutenticacao;
import server.business.services.ServicoEventos;
import server.data.cache.CacheManager;
import server.data.repository.IEventoRepository;
import server.data.repository.RepositoryFactory;

/**
 * Despachador de Requests - Mapeia chamadas remotas para serviços.
 * 
 * Padrão: Dispatcher + Skeleton (similar a RPC)
 * 
 * Responsabilidades:
 * 1. Receber Message com serviceId + methodId + payload
 * 2. Identificar skeleton correto
 * 3. Descodificar parametros do payload
 * 4. Invocar skeleton.processarRequisicao()
 * 5. Registar métricas (latência, sucesso/erro)
 * 
 * Injeção de dependências:
 * - CacheManager: cache de agregações
 * - Serviços: lógica de negócio
 * - Skeletons: adaptadores para cada serviço
 * 
 * Fluxo:
 * Message -> dispatcher.despachar() -> skeleton.processarRequisicao()
 *         -> servicoEventos/Agregacoes/etc.metodo()
 */
public class RequestDispatcher {
    private final Map<Byte, ISkeleton> skeletonsPorServico;  // Mapa de serviços
    private final ServicoEventos servicoEventos;             // Referência para persistência
    private final PerformanceMetrics metrics;                // Métricas globais

    private RequestDispatcher(Map<Byte, ISkeleton> skeletons, ServicoEventos servicoEventos) {
        this.skeletonsPorServico = skeletons;
        this.servicoEventos = servicoEventos;
        this.metrics = PerformanceMetrics.getInstance();
    }

    public static RequestDispatcher criar(int D, int S) {
        IEventoRepository eventoRepository = RepositoryFactory.getInstance().getEventoRepository();
        CacheManager cacheManager = new CacheManager(eventoRepository, S);

        ServicoAutenticacao servicoAuth = new ServicoAutenticacao();
        ServicoEventos servicoEventos = new ServicoEventos(eventoRepository, cacheManager, D);
        ServicoAgregacoes servicoAgregacoes = new ServicoAgregacoes(cacheManager, servicoEventos, eventoRepository, D);
        ServicoAdmin servicoAdmin = new ServicoAdmin();

        Map<Byte, ISkeleton> skeletons = Map.of(
                SERVICO_AUTENTICACAO, new ServicoAutenticacaoSkeleton(servicoAuth),
                SERVICO_EVENTOS, new ServicoEventosSkeleton(servicoEventos),
                SERVICO_AGREGACOES, new ServicoAgregacoesSkeleton(servicoAgregacoes),
                SERVICO_ADMIN, new ServicoAdminSkeleton(servicoAdmin)
        );

        System.out.println("RequestDispatcher criado com D=" + D + " e S=" + S);
        return new RequestDispatcher(skeletons, servicoEventos);
    }

    public RespostaDTO despachar(Message msg) {
        long inicio = System.nanoTime();
        boolean sucesso = false;

        try {
            ISkeleton skeleton = skeletonsPorServico.get(msg.getServiceId());
            if (skeleton == null) {
                return RespostaDTO.erro("Serviço desconhecido: " + msg.getServiceId());
            }

            Object parametros;
            try {
                parametros = decodeParametros(msg.getServiceId(), msg.getMethodId(), msg.getPayload());
            } catch (IOException e) {
                return RespostaDTO.erro("Parâmetros inválidos: " + e.getMessage());
            }

            RespostaDTO resposta = skeleton.processarRequisicao(msg.getMethodId(), parametros);
            sucesso = resposta.isSucesso();
            return resposta;

        } finally {
            long latencia = System.nanoTime() - inicio;
            metrics.recordRequest(sucesso, latencia);
        }
    }

    
    private Object decodeParametros(byte serviceId, byte methodId, byte[] payload) throws IOException {
        
        if (payload == null || payload.length == 0) {
            return null;
        }

        switch (serviceId) {
            case SERVICO_AUTENTICACAO:
                
                return UsuarioDTO.deserialize(payload);

            case SERVICO_EVENTOS:
                switch (methodId) {
                    case EVENTO_REGISTRAR:
                        return EventoDTO.deserialize(payload);
                    case EVENTO_NOTIFICAR_VENDA_ESPECIFICA:
                    case EVENTO_NOTIFICAR_VENDAS_CONSECUTIVAS:
                        return NotificacaoDTO.deserialize(payload);
                    case EVENTO_FILTRAR:
                        return FiltrarEventosDTO.deserialize(payload);
                    case EVENTO_LISTAR:
                    case EVENTO_NOVO_DIA:
                        return null;
                    default:
                        throw new IOException("Método eventos desconhecido: " + methodId);
                }

            case SERVICO_AGREGACOES:
                return AgregacaoRequestDTO.deserialize(payload);

            case SERVICO_ADMIN:
                
                return null;

            default:
                throw new IOException("Serviço desconhecido: " + serviceId);
        }
    }

    public void shutdown() {
        System.out.println("Encerrando RequestDispatcher...");
        if (servicoEventos != null) servicoEventos.shutdown();
        System.out.println("RequestDispatcher encerrado");
    }
}