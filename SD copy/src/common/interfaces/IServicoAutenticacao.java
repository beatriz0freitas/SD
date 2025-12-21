package common.interfaces;

import common.dto.UsuarioDTO;
import common.dto.RespostaDTO;
import common.exceptions.AutenticacaoException;

/**
 * Interface remota para serviço de autenticação
 */
public interface IServicoAutenticacao {
    
    /**
     * Registra novo utilizador
     * @param usuario dados do utilizador
     * @return resposta com sucesso ou erro
     * @throws AutenticacaoException se credenciais inválidas
     */
    RespostaDTO registrar(UsuarioDTO usuario) throws AutenticacaoException;
    
    /**
     * Autentica utilizador existente
     * @param usuario credenciais
     * @return resposta com token de sessão se sucesso
     * @throws AutenticacaoException se credenciais inválidas
     */
    RespostaDTO autenticar(UsuarioDTO usuario) throws AutenticacaoException;
    
    /**
     * Autentica administrador
     * @param password senha de admin
     * @return resposta com token de sessão se sucesso
     * @throws AutenticacaoException se senha inválida
     */
    RespostaDTO autenticarAdmin(String password) throws AutenticacaoException;
}
