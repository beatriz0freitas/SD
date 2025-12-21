package server.presentation.skeleton;

import common.dto.RespostaDTO;
import common.dto.UsuarioDTO;
import server.business.services.ServicoAutenticacao;

/**
 * Skeleton para serviço de autenticação
 * Recebe requisições e delega para o serviço de negócio
 */
public class ServicoAutenticacaoSkeleton implements ISkeleton{
    private final ServicoAutenticacao servico;
    
    public ServicoAutenticacaoSkeleton(ServicoAutenticacao servico) {
        this.servico = servico;
    }
    
    public RespostaDTO processarRequisicao(String operacao, Object parametros) {
        try {
            switch (operacao) {
                case "AUTH:REGISTRAR":
                    return servico.registrar((UsuarioDTO) parametros);
                    
                case "AUTH:LOGIN":
                    return servico.autenticar((UsuarioDTO) parametros);
                    
                case "AUTH:LOGIN_ADMIN":
                    return servico.autenticarAdmin((String) parametros);
                    
                default:
                    return RespostaDTO.erro("Operação desconhecida: " + operacao);
            }
        } catch (Exception e) {
            return RespostaDTO.erro("Erro no servidor: " + e.getMessage());
        }
    }
} 
    
