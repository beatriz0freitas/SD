package src.servidor;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64; //Para codificação em Base64 (hash da password)
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Gere registo e autenticação de utilizadores com persistência
 *
 * OTIMIZAÇÃO DE CONCORRÊNCIA:
 * - Usa ReentrantReadWriteLock:
 *   - readLock: autenticar, existeUtilizador, getNumUtilizadores, listarUtilizadores
 *   - writeLock: registar, carregarUtilizadores
 * - Permite múltiplas autenticações simultâneas (caso comum)
 * - Registo é raro, pode ser mais lento
 * 
* SEGURANÇA:
 * - Passwords guardadas como hash SHA-256
 * - Persistência atómica (write to temp + rename)
 */
public class GestorUtilizadores {
   
    // username -> password hash
    private final Map<String, String> utilizadores;
    private final PersistenciaUtilizadores persistencia;

    // ReadWriteLock para otimizar leituras concorrentes
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    public GestorUtilizadores() {
        this.utilizadores = new HashMap<>();
        this.persistencia = new PersistenciaUtilizadores();
        carregarUtilizadores();
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
            // SHA-256 está sempre disponível em Java moderno
            throw new RuntimeException("SHA-256 não disponível", e);
        }
    }

    /**
     * Verifica se um utilizador existe (leitura concorrente)
     * USA readLock: operação de leitura, permite concorrência
     */
    public boolean existeUtilizador(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        
        readLock.lock();
        try {
            return utilizadores.containsKey(username);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Obtém número de utilizadores registados (leitura concorrente)
     * USA readLock: operação de leitura
     */
    public int getNumUtilizadores() {
        readLock.lock();
        try {
            return utilizadores.size();
        } finally {
            readLock.unlock();
        }
    }

    public PersistenciaUtilizadores getPersistencia() {
        return persistencia;
    }

    /**
     * Regista um novo utilizador
     * @return true se registado com sucesso, false se já existe
     *
     * Usa writeLock: altera o mapa e faz persistência em disco
     */
    public boolean registar(String username, String password) {
        // Validação antes de adquirir lock (fail fast)
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username inválido");
        }
        if (password == null || password.length() < 4) {
            throw new IllegalArgumentException("Password deve ter pelo menos 4 caracteres");
        }

        writeLock.lock();
        try {
            if (utilizadores.containsKey(username)) {
                System.out.println("✗ Registo falhado: username '" + username + "' já existe");
                return false;
            }

            String passwordHash = hashPassword(password);
            utilizadores.put(username, passwordHash);

            try {
                // Persistir snapshot atual
                persistencia.guardarUtilizadores(utilizadores);
                System.out.println("✓ Utilizador registado: " + username + " (total: " + utilizadores.size() + ")");
                return true;
            } catch (IOException e) {
                System.err.println("Erro ao guardar utilizadores: " + e.getMessage());
                
                // Rollback da inserção em memória
                utilizadores.remove(username);
                return false;
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Autentica um utilizador
     * @return true se credenciais válidas, false caso contrário
     *
     * USA readLock: apenas leitura, permite múltiplas autenticações simultâneas
     */
    public boolean autenticar(String username, String password) {
        if (username == null || password == null) {
            return false;
        }

        readLock.lock();
        try {
            String storedPasswordHash = utilizadores.get(username);
            if (storedPasswordHash == null) return false;

            String passwordHash = hashPassword(password);
            // Compara hashes (timing-safe comparison seria ideal)
            boolean autenticado = storedPasswordHash.equals(passwordHash);
            
            if (autenticado) {
                System.out.println("✓ Autenticação bem-sucedida: " + username);
            } else {
                System.out.println("✗ Autenticação falhada: " + username + " (password incorreta)");
            }
            
            return autenticado;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Carrega utilizadores do disco
     * USA writeLock: modifica mapa (mas não há concorrência no construtor)
     * 
     * NOTA: Corre apenas no construtor, logo não há contenção.
     * writeLock usado por consistência (caso método seja reutilizado).
     */
    private void carregarUtilizadores() {
        
        writeLock.lock();
        try {
            Map<String, String> carregados = persistencia.carregarUtilizadores();
            utilizadores.clear();
            utilizadores.putAll(carregados);

            if (carregados.isEmpty()) {
                System.out.println("⚠ Nenhum utilizador encontrado em disco (primeira execução?)");
            } else {
                System.out.println("✓ Carregados " + carregados.size() + " utilizadores do disco");
            }
        } catch (IOException e) {
            System.err.println("Erro ao carregar utilizadores: " + e.getMessage());
            System.err.println("A iniciar com lista vazia de utilizadores.");
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Devolve string com lista de utilizadores
     * USA readLock: apenas leitura
     */
    public String listarUtilizadores() {
        readLock.lock();
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("=== CLIENTES REGISTADOS ===\n");
            sb.append("Total de utilizadores: ").append(utilizadores.size()).append("\n\n");
            if (utilizadores.isEmpty()) {
                sb.append("(nenhum utilizador registado)\n");
            } else {
                sb.append("Utilizadores:\n");
                
                // Ordena por ordem alfabética
                utilizadores.keySet().stream().sorted().forEach(username -> 
                        sb.append("  • ").append(username).append("\n"));
            }  
            return sb.toString();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Estatísticas para debugging
     */
    public String getStats() {
        readLock.lock();
        try {
            return String.format(
                "GestorUtilizadores[utilizadores=%d, persistido=%b]",
                utilizadores.size(),
                persistencia.existeFicheiro()
            );
        } finally {
            readLock.unlock();
        }
    }
}