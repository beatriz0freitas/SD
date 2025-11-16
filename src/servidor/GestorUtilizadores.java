package src.servidor;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64; //Para codificação em Base64 (hash da password)
import java.util.HashMap; 
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Gere registo e autenticação de utilizadores com persistência
 */
public class GestorUtilizadores {
    private Map<String, String> utilizadores; // username -> password hash
    private PersistenciaUtilizadores persistencia; // Classe de persistência
    private final ReentrantLock lock = new ReentrantLock();
    
    public GestorUtilizadores() {
        this.utilizadores = new HashMap<>();
        this.persistencia = new PersistenciaUtilizadores();
        carregarUtilizadores();
    }
  
    /**
     * Calcula hash da password (para quem tiver acesso ao ficheiro não ver a password em texto claro)
     */
    //todo: clean up
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Erro ao criar hash da password", e);
        }
    }

    /**
     * Verifica se um utilizador existe
     */
    public boolean existeUtilizador(String username) {
        lock.lock();
        try {
            return utilizadores.containsKey(username);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Obtém número de utilizadores registados
     */
    public int getNumUtilizadores() {
        lock.lock();
        try {
            return utilizadores.size();
        } finally {
            lock.unlock();
        }
    }

    public PersistenciaUtilizadores getPersistencia() {
        return persistencia;
    }

    /**
     * Regista um novo utilizador
     * @return true se registado com sucesso, false se já existe
     */
    public boolean registar(String username, String password) {
        lock.lock();
        try {
            if (utilizadores.containsKey(username)) {
                return false;
            }
            String passwordHash = hashPassword(password);
            utilizadores.put(username, passwordHash);

            try {
                persistencia.guardarUtilizadores(utilizadores);
                return true;
            } catch (IOException e) {
                System.err.println("Erro ao guardar utilizadores: " + e.getMessage());
                utilizadores.remove(username); // rollback
                return false;
            }
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Autentica um utilizador
     * @return true se credenciais válidas, false caso contrário
     */
    public boolean autenticar(String username, String password) {
        lock.lock();
        try {
            String storedPassword = utilizadores.get(username);
            if (storedPassword == null) return false;
            String passwordHash = hashPassword(password);
            return storedPassword.equals(passwordHash);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Carrega utilizadores do disco
     */
    private void carregarUtilizadores() {
        lock.lock();
        try {
            Map<String, String> carregados = persistencia.carregarUtilizadores();
            utilizadores.putAll(carregados);
        } catch (IOException e) {
            System.err.println("Erro ao carregar utilizadores: " + e.getMessage());
            System.err.println("A iniciar com lista vazia de utilizadores.");
        } finally {
            lock.unlock();
        }
    }

    public String listarUtilizadores() {
        lock.lock();
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("=== CLIENTES REGISTADOS ===\n");
            sb.append("Total de utilizadores: ").append(utilizadores.size()).append("\n\n");
            sb.append("Utilizadores:\n");
            for (String user : utilizadores.keySet()) {
                sb.append("- ").append(user).append("\n");
            }
            return sb.toString();
        } finally {
            lock.unlock();
        }
    }
    
    
}