package server.business.services;

import common.dto.RespostaDTO;
import common.exceptions.AdminException;
import common.interfaces.IServicoAdmin;
import java.util.List;
import server.business.domain.Usuario;
import server.data.repository.IUsuarioRepository;
import server.data.repository.RepositoryFactory;

/**
 * Serviço de negócio para operações administrativas
 */
public class ServicoAdmin implements IServicoAdmin {
    private final IUsuarioRepository usuarioRepository;
    
    public ServicoAdmin() {
        this.usuarioRepository = RepositoryFactory.getInstance().getUsuarioRepository();
    }
    
    @Override
    public RespostaDTO listarClientes() throws AdminException {
        List<Usuario> usuarios = usuarioRepository.listarTodos();
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== CLIENTES REGISTADOS ===\n");
        sb.append("Total: ").append(usuarios.size()).append("\n\n");
        
        for (Usuario u : usuarios) {
            sb.append("- ").append(u.getUsername()).append("\n");
        }
        
        return RespostaDTO.sucesso(sb.toString());
    }
    
    @Override
    public RespostaDTO obterEstatisticas() throws AdminException {
        int totalUsuarios = usuarioRepository.listarTodos().size();
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== ESTATÍSTICAS DO SISTEMA ===\n");
        sb.append("Utilizadores registados: ").append(totalUsuarios).append("\n");
        
        return RespostaDTO.sucesso(sb.toString());
    }
}