package client.Stub;

import client.ClienteMiddleware;
import common.dto.RespostaDTO;
import common.dto.UsuarioDTO;
import common.exceptions.AutenticacaoException;
import common.interfaces.IServicoAutenticacao;

/**
 * Stub (Stub) para serviço de autenticação
 * Representa o serviço remoto no lado do cliente
 */
public class ServicoAutenticacaoStub implements IServicoAutenticacao {
    private final ClienteMiddleware middleware;
    
    public ServicoAutenticacaoStub(ClienteMiddleware middleware) {
        this.middleware = middleware;
    }
    
    @Override
    public RespostaDTO registrar(UsuarioDTO usuario) throws AutenticacaoException {
        try {
            return middleware.invocar("AUTH:REGISTRAR", usuario);
        } catch (Exception e) {
            throw new AutenticacaoException("Erro ao registrar: " + e.getMessage(), e);
        }
    }
    
    @Override
    public RespostaDTO autenticar(UsuarioDTO usuario) throws AutenticacaoException {
        try {
            return middleware.invocar("AUTH:LOGIN", usuario);
        } catch (Exception e) {
            throw new AutenticacaoException("Erro ao autenticar: " + e.getMessage(), e);
        }
    }
    
    @Override
    public RespostaDTO autenticarAdmin(String password) throws AutenticacaoException {
        try {
            return middleware.invocar("AUTH:LOGIN_ADMIN", password);
        } catch (Exception e) {
            throw new AutenticacaoException("Erro ao autenticar admin: " + e.getMessage(), e);
        }
    }
}
