package server.data.dao;

import java.util.List;

import server.business.domain.Usuario;

/**
 * Interface DAO para operações de persistência de usuários
 */
public interface IUsuarioDAO {
    /**
     * Busca usuário por username
     */
    Usuario buscar(String username);
    
    /**
     * Salva novo usuário
     */
    void salvar(Usuario usuario);
    
    /**
     * Atualiza usuário existente
     */
    void atualizar(Usuario usuario);
    
    /**
     * Remove usuário
     */
    void deletar(String username);
    
    /**
     * Lista todos os usuários
     */
    List<Usuario> listarTodos();
    
    /**
     * Verifica se usuário existe
     */
    boolean existe(String username);
}