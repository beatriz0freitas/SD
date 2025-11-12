package src.server;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gere registo e autenticação de utilizadores com persistência
 */
public class GestorUtilizadores {
    private ConcurrentHashMap<String, String> utilizadores; // username -> password hash
    private PersistenciaUtilizadores persistencia;
    
    public GestorUtilizadores() {
        this.utilizadores = new ConcurrentHashMap<>();
        this.persistencia = new PersistenciaUtilizadores();
        carregarUtilizadores();
    }
    
    /**
     * Regista um novo utilizador
     * @return true se registado com sucesso, false se já existe
     */
    public synchronized boolean registar(String username, String password) {
        if (utilizadores.containsKey(username)) {
            return false;
        }
        
        String passwordHash = hashPassword(password);
        utilizadores.put(username, passwordHash);
        
        // Persistir imediatamente após registo
        try {
            persistencia.guardarUtilizadores(utilizadores);
            return true;
        } catch (IOException e) {
            System.err.println("Erro ao guardar utilizadores: " + e.getMessage());
            utilizadores.remove(username); // Rollback
            return false;
        }
    }
    
    /**
     * Autentica um utilizador
     * @return true se credenciais válidas, false caso contrário
     */
    public boolean autenticar(String username, String password) {
        String storedHash = utilizadores.get(username);
        if (storedHash == null) {
            return false;
        }
        
        String passwordHash = hashPassword(password);
        return storedHash.equals(passwordHash);
    }
    
    /**
     * Verifica se um utilizador existe
     */
    public boolean existeUtilizador(String username) {
        return utilizadores.containsKey(username);
    }
    
    /**
     * Obtém número de utilizadores registados
     */
    public int getNumUtilizadores() {
        return utilizadores.size();
    }
    
    /**
     * Carrega utilizadores do disco
     */
    private void carregarUtilizadores() {
        try {
            Map<String, String> carregados = persistencia.carregarUtilizadores();
            utilizadores.putAll(carregados);
        } catch (IOException e) {
            System.err.println("Erro ao carregar utilizadores: " + e.getMessage());
            System.err.println("A iniciar com lista vazia de utilizadores.");
        }
    }
    
    /**
     * Calcula hash SHA-256 da password
     */
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Erro ao criar hash da password", e);
        }
    }
}