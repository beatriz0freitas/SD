package server.business.services;

import common.dto.RespostaDTO;
import common.exceptions.AgregacaoException;
import common.interfaces.IServicoAgregacoes;
import server.data.cache.CacheManager;
import server.data.repository.IEventoRepository;
import server.data.repository.RepositoryFactory;

/**
 * Serviço de negócio para agregações
 */
public class ServicoAgregacoes implements IServicoAgregacoes {
    private final CacheManager cacheManager;
    private final ServicoEventos servicoEventos;
    
    public ServicoAgregacoes(ServicoEventos servicoEventos, int D) {
        this.servicoEventos = servicoEventos;
        IEventoRepository repository = RepositoryFactory.getInstance().getEventoRepository();
        this.cacheManager = new CacheManager(repository, D);
    }
    
    @Override
    public RespostaDTO obterQuantidadeVendas(int produtoID, int dias) 
            throws AgregacaoException {
        validarParametros(produtoID, dias);
        
        try {
            int quantidade = cacheManager.obterQuantidade(produtoID, dias);
            String msg = String.format(
                "Quantidade de Vendas nos últimos %d dias: %d", dias, quantidade);
            return RespostaDTO.sucesso(msg, quantidade);
            
        } catch (Exception e) {
            throw new AgregacaoException("Erro ao calcular quantidade", e);
        }
    }
    
    @Override
    public RespostaDTO obterVolumeVendas(int produtoID, int dias) 
            throws AgregacaoException {
        validarParametros(produtoID, dias);
        
        try {
            double volume = cacheManager.obterVolume(produtoID, dias);
            String msg = String.format(
                "Volume de Vendas nos últimos %d dias: %.2f", dias, volume);
            return RespostaDTO.sucesso(msg, volume);
            
        } catch (Exception e) {
            throw new AgregacaoException("Erro ao calcular volume", e);
        }
    }
    
    @Override
    public RespostaDTO obterPrecoMedio(int produtoID, int dias) 
            throws AgregacaoException {
        validarParametros(produtoID, dias);
        
        try {
            double medio = cacheManager.obterPrecoMedio(produtoID, dias);
            String msg = String.format(
                "Preço Médio nos últimos %d dias: %.2f", dias, medio);
            return RespostaDTO.sucesso(msg, medio);
            
        } catch (Exception e) {
            throw new AgregacaoException("Erro ao calcular preço médio", e);
        }
    }
    
    @Override
    public RespostaDTO obterPrecoMaximo(int produtoID, int dias) 
            throws AgregacaoException {
        validarParametros(produtoID, dias);
        
        try {
            double maximo = cacheManager.obterPrecoMaximo(produtoID, dias);
            String msg = String.format(
                "Preço Máximo nos últimos %d dias: %.2f", dias, maximo);
            return RespostaDTO.sucesso(msg, maximo);
            
        } catch (Exception e) {
            throw new AgregacaoException("Erro ao calcular preço máximo", e);
        }
    }
    
    private void validarParametros(int produtoID, int dias) throws AgregacaoException {
        if (produtoID <= 0) {
            throw new AgregacaoException("ID de produto inválido");
        }
        if (dias <= 0) {
            throw new AgregacaoException("Número de dias inválido");
        }
        
        int diaAtual = servicoEventos.getDiaAtual();
        if (dias > diaAtual) {
            throw new AgregacaoException(
                "Número de dias excede histórico disponível (máx: " + diaAtual + ")");
        }
    }
}