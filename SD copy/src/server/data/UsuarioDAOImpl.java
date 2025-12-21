package server.data;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import server.business.domain.Usuario;

/**
 * Implementação do DAO de usuários com persistência em arquivo
 */
public class UsuarioDAOImpl implements IUsuarioDAO {
    private static final String FICHEIRO = "dados/utilizadores.dat";
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, Usuario> cache = new HashMap<>();
    
    public UsuarioDAOImpl() {
        carregarTodos();
    }
    
    @Override
    public Usuario buscar(String username) {
        lock.readLock().lock();
        try {
            return cache.get(username);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public void salvar(Usuario usuario) {
        lock.writeLock().lock();
        try {
            cache.put(usuario.getUsername(), usuario);
            persistirTodos();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void atualizar(Usuario usuario) {
        salvar(usuario); // mesmo comportamento
    }
    
    @Override
    public void deletar(String username) {
        lock.writeLock().lock();
        try {
            cache.remove(username);
            persistirTodos();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public List<Usuario> listarTodos() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(cache.values());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public boolean existe(String username) {
        lock.readLock().lock();
        try {
            return cache.containsKey(username);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // Métodos privados de persistência
    
    private void carregarTodos() {
        File file = new File(FICHEIRO);
        if (!file.exists()) return;
        
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {
            
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                String username = in.readUTF();
                String passwordHash = in.readUTF();
                cache.put(username, new Usuario(username, passwordHash));
            }
            System.out.println("Carregados " + count + " usuários do disco");
            
        } catch (IOException e) {
            System.err.println("Erro ao carregar usuários: " + e.getMessage());
        }
    }
    
    private void persistirTodos() {
        File file = new File(FICHEIRO);
        file.getParentFile().mkdirs();
        
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(file)))) {
            
            out.writeInt(cache.size());
            for (Usuario u : cache.values()) {
                out.writeUTF(u.getUsername());
                out.writeUTF(u.getPasswordHash());
            }
            out.flush();
            
        } catch (IOException e) {
            System.err.println("Erro ao persistir usuários: " + e.getMessage());
        }
    }
}