package server.data.repository;

import java.util.List;

import server.business.domain.Usuario;

/**
 * Interface Repository para operações de persistência de usuários
 */
public interface IUsuarioRepository {
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
     * Conta o número total de usuários
     */
    int contarUtilizadores() ;
    
    /**
     * Verifica se usuário existe
     */
    boolean existe(String username);
}