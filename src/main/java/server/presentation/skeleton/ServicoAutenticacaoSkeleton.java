package server.presentation.skeleton;

import static middleware.MessageTypes.*;

import common.dto.RespostaDTO;
import common.dto.UsuarioDTO;
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
                    return servico.autenticarAdmin((String) parametros);
                default:
                    return RespostaDTO.erro("Método desconhecido: " + methodId);
            }
        } catch (Exception e) {
            return RespostaDTO.erro("Erro no servidor: " + e.getMessage());
        }
    }
}