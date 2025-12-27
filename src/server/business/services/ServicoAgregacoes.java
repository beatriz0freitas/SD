package server.business.services;

import common.dto.RespostaDTO;
import common.exceptions.AgregacaoException;
import common.interfaces.IServicoAgregacoes;
import server.data.cache.CacheManager;

/**
 * Serviço de agregações de vendas
 * Delega cálculos para o CacheManager (lazy, on-demand)
 */
public class ServicoAgregacoes implements IServicoAgregacoes {
    private final CacheManager cacheManager;
    private final ServicoEventos servicoEventos;
    private final int D; // Janela máxima de dias
    
    public ServicoAgregacoes(CacheManager cacheManager, ServicoEventos servicoEventos, int D) {
        this.cacheManager = cacheManager;
        this.servicoEventos = servicoEventos;
        this.D = D;
    }
    
    @Override
    public RespostaDTO obterQuantidadeVendas(int produtoID, int dias) throws AgregacaoException {
        validarParametros(produtoID, dias);
        
        int quantidade = cacheManager.obterQuantidade(produtoID, dias);
        String mensagem = String.format(
            "Quantidade de Vendas nos últimos %d dias: %d", 
            dias, quantidade
        );
        
        return RespostaDTO.sucesso(mensagem, quantidade);
    }
    
    @Override
    public RespostaDTO obterVolumeVendas(int produtoID, int dias) throws AgregacaoException {
        validarParametros(produtoID, dias);
        
        double volume = cacheManager.obterVolume(produtoID, dias);
        String mensagem = String.format(
            "Volume de Vendas nos últimos %d dias: %.2f€", 
            dias, volume
        );
        
        return RespostaDTO.sucesso(mensagem, volume);
    }
    
    @Override
    public RespostaDTO obterPrecoMedio(int produtoID, int dias) throws AgregacaoException {
        validarParametros(produtoID, dias);
        
        double medio = cacheManager.obterPrecoMedio(produtoID, dias);
        String mensagem = String.format(
            "Preço Médio nos últimos %d dias: %.2f€", 
            dias, medio
        );
        
        return RespostaDTO.sucesso(mensagem, medio);
    }
    
    @Override
    public RespostaDTO obterPrecoMaximo(int produtoID, int dias) throws AgregacaoException {
        validarParametros(produtoID, dias);
        
        double maximo = cacheManager.obterPrecoMaximo(produtoID, dias);
        String mensagem = String.format(
            "Preço Máximo nos últimos %d dias: %.2f€", 
            dias, maximo
        );
        
        return RespostaDTO.sucesso(mensagem, maximo);
    }
    
    private void validarParametros(int produtoID, int dias) throws AgregacaoException {
        // Validar produto
        if (produtoID <= 0) {
            throw new AgregacaoException("ID de produto inválido");
        }
        
        // Validar dias
        if (dias <= 0) {
            throw new AgregacaoException("Número de dias inválido");
        }
        
        if (dias > D) {
            throw new AgregacaoException(
                "Número de dias excede configuração do servidor (máx: " + D + ")"
            );
        }
        
        // Verificar histórico disponível
        int diaAtual = servicoEventos.getDiaAtual();
        int diasDisponiveis = diaAtual; // dias 0 até diaAtual-1
        
        if (dias > diasDisponiveis) {
            throw new AgregacaoException(
                "Número de dias excede histórico disponível (máx: " + diasDisponiveis + ")"
            );
        }
    }
}