package server.business.validators;

import common.dto.UsuarioDTO;
import common.exceptions.DadosInvalidosException;

/**
 * Validador de regras de negócio para usuários
 */
public class UsuarioValidator {
    
    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_USERNAME_LENGTH = 50;
    private static final int MIN_PASSWORD_LENGTH = 4;
    
    /**
     * Valida dados para registro
     */
    public void validarRegistro(UsuarioDTO dto) throws DadosInvalidosException {
        validarUsername(dto.getUsername());
        validarPassword(dto.getPassword());
    }
    
    /**
     * Valida dados para autenticação
     */
    public void validarAutenticacao(UsuarioDTO dto) throws DadosInvalidosException {
        if (dto == null) {
            throw new DadosInvalidosException("Dados de autenticação não fornecidos");
        }
        
        if (dto.getUsername() == null || dto.getUsername().isBlank()) {
            throw new DadosInvalidosException("Username não pode estar vazio");
        }
        
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new DadosInvalidosException("Password não pode estar vazia");
        }
    }
    
    private void validarUsername(String username) throws DadosInvalidosException {
        if (username == null || username.isBlank()) {
            throw new DadosInvalidosException("Username não pode estar vazio");
        }
        
        username = username.trim();
        
        if (username.length() < MIN_USERNAME_LENGTH) {
            throw new DadosInvalidosException(
                "Username deve ter pelo menos " + MIN_USERNAME_LENGTH + " caracteres");
        }
        
        if (username.length() > MAX_USERNAME_LENGTH) {
            throw new DadosInvalidosException(
                "Username não pode exceder " + MAX_USERNAME_LENGTH + " caracteres");
        }
        
        // Apenas letras, números e underscore
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            throw new DadosInvalidosException(
                "Username só pode conter letras, números e underscore");
        }
    }
    
    private void validarPassword(String password) throws DadosInvalidosException {
        if (password == null || password.isEmpty()) {
            throw new DadosInvalidosException("Password não pode estar vazia");
        }
        
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new DadosInvalidosException(
                "Password deve ter pelo menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }
    }
}
