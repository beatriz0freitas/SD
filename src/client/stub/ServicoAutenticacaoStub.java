package client.stub;

import client.ClienteMiddleware;
import common.dto.RespostaDTO;
import common.dto.UsuarioDTO;
import common.exceptions.AutenticacaoException;
import common.interfaces.IServicoAutenticacao;
import middleware.Protocolos;

public class ServicoAutenticacaoStub implements IServicoAutenticacao {
    private final ClienteMiddleware middleware;

    public ServicoAutenticacaoStub(ClienteMiddleware middleware) {
        this.middleware = middleware;
    }

    @Override
    public RespostaDTO registrar(UsuarioDTO usuario) throws AutenticacaoException {
        try {
            return middleware.invocar(
                Protocolos.SERVICO_AUTENTICACAO,
                Protocolos.AUTH_REGISTRAR,
                usuario
            );
        } catch (Exception e) {
            throw new AutenticacaoException("Erro ao registrar: " + e.getMessage(), e);
        }
    }

    @Override
    public RespostaDTO autenticar(UsuarioDTO usuario) throws AutenticacaoException {
        try {
            return middleware.invocar(
                Protocolos.SERVICO_AUTENTICACAO,
                Protocolos.AUTH_LOGIN,
                usuario
            );
        } catch (Exception e) {
            throw new AutenticacaoException("Erro ao autenticar: " + e.getMessage(), e);
        }
    }

    @Override
    public RespostaDTO autenticarAdmin(String password) throws AutenticacaoException {
        try {
            return middleware.invocar(
                Protocolos.SERVICO_AUTENTICACAO,
                Protocolos.AUTH_LOGIN_ADMIN,
                password
            );
        } catch (Exception e) {
            throw new AutenticacaoException("Erro ao autenticar admin: " + e.getMessage(), e);
        }
    }
}
