package common.interfaces;

import common.dto.RespostaDTO;
import common.exceptions.AgregacaoException;


public interface IServicoAgregacoes {
    
    
    RespostaDTO obterQuantidadeVendas(int produtoID, int dias) 
        throws AgregacaoException;
    
    
    RespostaDTO obterVolumeVendas(int produtoID, int dias) 
        throws AgregacaoException;
    
    
    RespostaDTO obterPrecoMedio(int produtoID, int dias) 
        throws AgregacaoException;
    
    
    RespostaDTO obterPrecoMaximo(int produtoID, int dias) 
        throws AgregacaoException;
}
