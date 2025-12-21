package client.stub;

import client.ClienteMiddleware;
import common.dto.AgregacaoRequestDTO;
import common.dto.RespostaDTO;
import common.exceptions.AgregacaoException;
import common.interfaces.IServicoAgregacoes;

public class ServicoAgregacoesStub implements IServicoAgregacoes {
    private final ClienteMiddleware middleware;
    
    public ServicoAgregacoesStub(ClienteMiddleware middleware) {
        this.middleware = middleware;
    }
    
    @Override
    public RespostaDTO obterQuantidadeVendas(int produtoID, int dias) 
            throws AgregacaoException {
        try {
            AgregacaoRequestDTO params = new AgregacaoRequestDTO(produtoID, dias);
            return middleware.invocar("AGREGACAO:QUANTIDADE", params);
        } catch (Exception e) {
            throw new AgregacaoException("Erro ao obter quantidade: " + e.getMessage(), e);
        }
    }
    
    @Override
    public RespostaDTO obterVolumeVendas(int produtoID, int dias) 
            throws AgregacaoException {
        try {
            AgregacaoRequestDTO params = new AgregacaoRequestDTO(produtoID, dias);
            return middleware.invocar("AGREGACAO:VOLUME", params);
        } catch (Exception e) {
            throw new AgregacaoException("Erro ao obter volume: " + e.getMessage(), e);
        }
    }
    
    @Override
    public RespostaDTO obterPrecoMedio(int produtoID, int dias) 
            throws AgregacaoException {
        try {
            AgregacaoRequestDTO params = new AgregacaoRequestDTO(produtoID, dias);
            return middleware.invocar("AGREGACAO:PRECO_MEDIO", params);
        } catch (Exception e) {
            throw new AgregacaoException("Erro ao obter preço médio: " + e.getMessage(), e);
        }
    }
    
    @Override
    public RespostaDTO obterPrecoMaximo(int produtoID, int dias) 
            throws AgregacaoException {
        try {
            AgregacaoRequestDTO params = new AgregacaoRequestDTO(produtoID, dias);
            return middleware.invocar("AGREGACAO:PRECO_MAXIMO", params);
        } catch (Exception e) {
            throw new AgregacaoException("Erro ao obter preço máximo: " + e.getMessage(), e);
        }
    }
}