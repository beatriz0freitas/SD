package client.stub;

import client.ClienteMiddleware;
import common.dto.RespostaDTO;
import common.exceptions.AdminException;
import common.interfaces.IServicoAdmin;
import middleware.Protocolos;

public class ServicoAdminStub implements IServicoAdmin {
    private final ClienteMiddleware middleware;

    public ServicoAdminStub(ClienteMiddleware middleware) {
        this.middleware = middleware;
    }

    @Override
    public RespostaDTO listarClientes() throws AdminException {
        try {
            return middleware.invocar(
                Protocolos.SERVICO_ADMIN,
                Protocolos.ADMIN_LISTAR_CLIENTES,
                null
            );
        } catch (Exception e) {
            throw new AdminException("Erro ao listar clientes: " + e.getMessage(), e);
        }
    }

    @Override
    public RespostaDTO obterEstatisticas() throws AdminException {
        try {
            return middleware.invocar(
                Protocolos.SERVICO_ADMIN,
                Protocolos.ADMIN_ESTATISTICAS,
                null
            );
        } catch (Exception e) {
            throw new AdminException("Erro ao obter estatísticas: " + e.getMessage(), e);
        }
    }
}
