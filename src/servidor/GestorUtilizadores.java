package src.servidor;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64; //Para codificação em Base64 (hash da password)
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Gere registo e autenticação de utilizadores com persistência
 *
 * OTIMIZAÇÃO DE CONCORRÊNCIA:
 * - Usa ReentrantReadWriteLock:
 *   - readLock: autenticar, existeUtilizador, getNumUtilizadores, listarUtilizadores
 *   - writeLock: registar, carregarUtilizadores
 * - Permite múltiplas leituras em paralelo, bloqueando apenas quando há escrita/persistência.
 * 
 * - readLock (leitura):
 *   -várias threads podem ter o readLock ao mesmo tempo;
 *   -desde que nenhuma tenha o writeLock.
 * 
 * - writeLock (escrita):
 *   -só uma thread pode ter o writeLock;
 *   -e enquanto o writeLock está ativo, nenhuma leitura nem outra escrita entram.
 *
 *
 */
public class GestorUtilizadores {
    // username -> password hash
    private final Map<String, String> utilizadores;
    private final PersistenciaUtilizadores persistencia;

    // Lock de leitura/escrita
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = rwLock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = rwLock.writeLock();

    public GestorUtilizadores() {
        this.utilizadores = new HashMap<>();
        this.persistencia = new PersistenciaUtilizadores();
        carregarUtilizadores();
    }

    /**
     * Calcula hash da password (para quem tiver acesso ao ficheiro não ver a password em texto claro)
     */
    //FIXME: dar clean up nisto
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
     * Verifica se um utilizador existe (leitura concorrente)
     */
    public boolean existeUtilizador(String username) {
        readLock.lock();
        try {
            return utilizadores.containsKey(username);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Obtém número de utilizadores registados (leitura concorrente)
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
     * Usa writeLock porque:
     * - altera o mapa
     * - faz persistência em disco
     */
    public boolean registar(String username, String password) {
        writeLock.lock();
        try {
            if (utilizadores.containsKey(username)) {
                return false;
            }

            String passwordHash = hashPassword(password);
            utilizadores.put(username, passwordHash);

            try {
                // Persistir snapshot atual
                persistencia.guardarUtilizadores(utilizadores);
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
     * Apenas leitura: usa readLock.
     */
    public boolean autenticar(String username, String password) {
        readLock.lock();
        try {
            String storedPassword = utilizadores.get(username);
            if (storedPassword == null) return false;

            String passwordHash = hashPassword(password);
            return storedPassword.equals(passwordHash);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Carrega utilizadores do disco
     * Esta operação corre no construtor, por isso não é concorrida com outras,
     * mas usamos writeLock por consistência caso no futuro seja reaproveitada.
     */
    private void carregarUtilizadores() {
        writeLock.lock();
        try {
            Map<String, String> carregados = persistencia.carregarUtilizadores();
            utilizadores.clear();
            utilizadores.putAll(carregados);
        } catch (IOException e) {
            System.err.println("Erro ao carregar utilizadores: " + e.getMessage());
            System.err.println("A iniciar com lista vazia de utilizadores.");
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Devolve string com lista de utilizadores (apenas leitura)
     */
    public String listarUtilizadores() {
        readLock.lock();
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
            readLock.unlock();
        }
    }
}