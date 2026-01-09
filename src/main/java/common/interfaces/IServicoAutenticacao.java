package common.interfaces;

import common.dto.RespostaDTO;
import common.dto.UsuarioDTO;
import common.exceptions.AutenticacaoException;


public interface IServicoAutenticacao {
    
    
    RespostaDTO registrar(UsuarioDTO usuario) throws AutenticacaoException;
    
    
    RespostaDTO autenticar(UsuarioDTO usuario) throws AutenticacaoException;
    
    
    RespostaDTO autenticarAdmin(String password) throws AutenticacaoException;
}
