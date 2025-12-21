package server.business.services;

import common.dto.RespostaDTO;
import common.exceptions.AdminException;
import common.interfaces.IServicoAdmin;
import java.util.List;

import server.business.domain.Usuario;
import server.data.dao.DAOFactory;
import server.data.dao.IUsuarioDAO;

/**
 * Serviço de negócio para operações administrativas
 */
public class ServicoAdmin implements IServicoAdmin {
    private final IUsuarioDAO usuarioDAO;
    
    public ServicoAdmin() {
        this.usuarioDAO = DAOFactory.getInstance().getUsuarioDAO();
    }
    
    @Override
    public RespostaDTO listarClientes() throws AdminException {
        List<Usuario> usuarios = usuarioDAO.listarTodos();
        
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
        int totalUsuarios = usuarioDAO.listarTodos().size();
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== ESTATÍSTICAS DO SISTEMA ===\n");
        sb.append("Utilizadores registados: ").append(totalUsuarios).append("\n");
        
        return RespostaDTO.sucesso(sb.toString());
    }
}