package server.data.repository;

import java.util.List;

import server.business.domain.Usuario;


public interface IUsuarioRepository {
    
    Usuario buscar(String username);
    
    
    void salvar(Usuario usuario);
    
    
    void atualizar(Usuario usuario);
    
    
    void deletar(String username);
    
    
    List<Usuario> listarTodos();

    
    int contarUtilizadores() ;
    
    
    boolean existe(String username);
}