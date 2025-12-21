package client.stub;

import client.ClienteMiddleware;
import common.interfaces.*;

/**
 * Factory para criação de proxies
 */
public class StubFactory {
    private final ClienteMiddleware middleware;
    
    public StubFactory(ClienteMiddleware middleware) {
        this.middleware = middleware;
    }
    
    public IServicoAutenticacao criarStubAutenticacao() {
        return new ServicoAutenticacaoStub(middleware);
    }
    
    public IServicoEventos criarStubEventos() {
        return new ServicoEventosStub(middleware);
    }
    
    public IServicoAgregacoes criarStubAgregacoes() {
        return new ServicoAgregacoesStub(middleware);
    }
    
    public IServicoAdmin criarStubAdmin() {
        return new ServicoAdminStub(middleware);
    }
}