package server.data.repository;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import server.business.domain.Usuario;

/**
 * Persistência de utilizadores em ficheiro binário
 * Thread-safe com armazenamento em memória
 */
public class UsuarioFileRepository implements IUsuarioRepository {
    private static final String FICHEIRO = "dados/utilizadores.dat";
    
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();
    
    private final Map<String, Usuario> utilizadores = new HashMap<>();
    private boolean modificado = false; // Flag para saber se precisa persistir
    
    public UsuarioFileRepository() {
        carregarTodos();
        
        // Persistir ao encerrar JVM
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (modificado) {
                System.out.println("Persistindo utilizadores antes de encerrar...");
                persistirTodos();
            }
        }));
    }
    
    @Override
    public Usuario buscar(String username) {
        readLock.lock();
        try {
            return utilizadores.get(username);
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public void salvar(Usuario usuario) {
        writeLock.lock();
        try {
            utilizadores.put(usuario.getUsername(), usuario);
            modificado = true;
            // Não persiste imediatamente - será persistido no shutdown
        } finally {
            writeLock.unlock();
        }
    }
    
    @Override
    public void atualizar(Usuario usuario) {
        salvar(usuario);
    }
    
    @Override
    public void deletar(String username) {
        writeLock.lock();
        try {
            utilizadores.remove(username);
            modificado = true;
        } finally {
            writeLock.unlock();
        }
    }
    
    @Override
    public List<Usuario> listarTodos() {
        readLock.lock();
        try {
            return new ArrayList<>(utilizadores.values());
        } finally {
            readLock.unlock();
        }
    }


    @Override
public int contarUtilizadores() {
    readLock.lock();
    try {
        return utilizadores.size();
    } finally {
        readLock.unlock();
    }
}

    
    @Override
    public boolean existe(String username) {
        readLock.lock();
        try {
            return utilizadores.containsKey(username);
        } finally {
            readLock.unlock();
        }
    }
    
    /**
     * Força persistência imediata (para comandos admin)
     */
    public void persistirAgora() {
        writeLock.lock();
        try {
            if (modificado) {
                persistirTodos();
                modificado = false;
            }
        } finally {
            writeLock.unlock();
        }
    }
    
    // Métodos privados de persistência
    
    private void carregarTodos() {
        File ficheiro = new File(FICHEIRO);
        if (!ficheiro.exists()) {
            return;
        }
        
        writeLock.lock();
        try {
            try (DataInputStream in = new DataInputStream(
                    new BufferedInputStream(new FileInputStream(ficheiro)))) {
                
                int count = in.readInt();
                for (int i = 0; i < count; i++) {
                    String username = in.readUTF();
                    String passwordHash = in.readUTF();
                    utilizadores.put(username, new Usuario(username, passwordHash));
                }
                
                System.out.println("Carregados " + count + " utilizadores do disco");
                
            } catch (IOException e) {
                System.err.println("Erro ao carregar utilizadores: " + e.getMessage());
            }
        } finally {
            writeLock.unlock();
        }
    }
    
    private void persistirTodos() {
        File ficheiro = new File(FICHEIRO);
        ficheiro.getParentFile().mkdirs();
        
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(ficheiro)))) {
            
            out.writeInt(utilizadores.size());
            for (Usuario u : utilizadores.values()) {
                out.writeUTF(u.getUsername());
                out.writeUTF(u.getPasswordHash());
            }
            out.flush();
            
            System.out.println("Persistidos " + utilizadores.size() + " utilizadores");
            
        } catch (IOException e) {
            System.err.println("Erro ao persistir utilizadores: " + e.getMessage());
        }
    }
}