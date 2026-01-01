package common.interfaces;

import common.dto.RespostaDTO;
import common.exceptions.AgregacaoException;

/**
 * Interface remota para consultas de agregações
 */
public interface IServicoAgregacoes {
    
    /**
     * Obtém quantidade total de vendas
     * @param produtoID ID do produto
     * @param dias número de dias anteriores
     * @return agregação com quantidade
     * @throws AgregacaoException se parâmetros inválidos
     */
    RespostaDTO obterQuantidadeVendas(int produtoID, int dias) 
        throws AgregacaoException;
    
    /**
     * Obtém volume total de vendas (quantidade * preço)
     * @param produtoID ID do produto
     * @param dias número de dias anteriores
     * @return agregação com volume
     * @throws AgregacaoException se parâmetros inválidos
     */
    RespostaDTO obterVolumeVendas(int produtoID, int dias) 
        throws AgregacaoException;
    
    /**
     * Obtém preço médio de vendas
     * @param produtoID ID do produto
     * @param dias número de dias anteriores
     * @return agregação com preço médio
     * @throws AgregacaoException se parâmetros inválidos
     */
    RespostaDTO obterPrecoMedio(int produtoID, int dias) 
        throws AgregacaoException;
    
    /**
     * Obtém preço máximo de vendas
     * @param produtoID ID do produto
     * @param dias número de dias anteriores
     * @return agregação com preço máximo
     * @throws AgregacaoException se parâmetros inválidos
     */
    RespostaDTO obterPrecoMaximo(int produtoID, int dias) 
        throws AgregacaoException;
}
