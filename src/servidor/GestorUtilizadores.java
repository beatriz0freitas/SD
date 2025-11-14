package src.servidor;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64; //Para codificação em Base64 (hash da password)
import java.util.HashMap; 
import java.util.Map;

/**
 * Gere registo e autenticação de utilizadores com persistência
 */
public class GestorUtilizadores {
    private Map<String, String> utilizadores; // username -> password hash
    private PersistenciaUtilizadores persistencia; // Classe de persistência
    
    public GestorUtilizadores() {
        this.utilizadores = new HashMap<>();
        this.persistencia = new PersistenciaUtilizadores();
        carregarUtilizadores();
    }
    
    /**
     * Regista um novo utilizador
     * @return true se registado com sucesso, false se já existe
     */
    public boolean registar(String username, String password) {
        if (utilizadores.containsKey(username)) {
            return false;
        }
        
        String passwordHash = hashPassword(password); // Hash da password para segurança (não guardar em texto claro)
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

        // Obter password armazenada
        String storedPassword = utilizadores.get(username); 
        if (storedPassword == null) {
            return false;
        }
        
        // Comparar hash da password
        String passwordHash = hashPassword(password);
        return storedPassword.equals(passwordHash);
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
     * Calcula hash da password (para quem tiver acesso ao ficheiro não ver a password em texto claro)
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