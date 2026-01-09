package server.presentation.skeleton;

import common.dto.RespostaDTO;
import common.dto.UsuarioDTO;
import static middleware.MessageTypes.AUTH_LOGIN;
import static middleware.MessageTypes.AUTH_LOGIN_ADMIN;
import static middleware.MessageTypes.AUTH_REGISTRAR;
import server.business.services.ServicoAutenticacao;

public class ServicoAutenticacaoSkeleton implements ISkeleton {
    private final ServicoAutenticacao servico;

    public ServicoAutenticacaoSkeleton(ServicoAutenticacao servico) {
        this.servico = servico;
    }

    @Override
    public RespostaDTO processarRequisicao(byte methodId, Object parametros) {
        try {
            switch (methodId) {
                case AUTH_REGISTRAR:
                    return servico.registrar((UsuarioDTO) parametros);

                case AUTH_LOGIN:
                    return servico.autenticar((UsuarioDTO) parametros);

                case AUTH_LOGIN_ADMIN:
                    // Cliente envia UsuarioDTO("ADMIN", password)
                    UsuarioDTO admin = (UsuarioDTO) parametros;
                    return servico.autenticarAdmin(admin.getPassword());

                default:
                    return RespostaDTO.erro("Método desconhecido: " + methodId);
            }
        } catch (Exception e) {
            return RespostaDTO.erro("Erro no servidor: " + e.getMessage());
        }
    }
}