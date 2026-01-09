package server.data.repository;

import java.util.concurrent.locks.ReentrantReadWriteLock;


public class RepositoryFactory {
    private static RepositoryFactory instance;
    private static final ReentrantReadWriteLock instanceLock = new ReentrantReadWriteLock();
    
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private IUsuarioRepository usuarioRepository;
    private IEventoRepository eventoRepository;
    
    private RepositoryFactory() {
        
    }
    
    public static RepositoryFactory getInstance() {
        
        if (instance == null) {
            instanceLock.writeLock().lock();
            try {
                if (instance == null) {
                    instance = new RepositoryFactory();
                }
            } finally {
                instanceLock.writeLock().unlock();
            }
        }
        return instance;
    }
    
    public IUsuarioRepository getUsuarioRepository() {
        
        lock.readLock().lock();
        try {
            if (usuarioRepository != null) {
                return usuarioRepository;
            }
        } finally {
            lock.readLock().unlock();
        }
        
        
        lock.writeLock().lock();
        try {
            if (usuarioRepository == null) {
                usuarioRepository = new UsuarioFileRepository();
            }
            return usuarioRepository;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public IEventoRepository getEventoRepository() {
        
        lock.readLock().lock();
        try {
            if (eventoRepository != null) {
                return eventoRepository;
            }
        } finally {
            lock.readLock().unlock();
        }
        
        
        lock.writeLock().lock();
        try {
            if (eventoRepository == null) {
                eventoRepository = new EventoFileRepository("dados/eventos");
            }
            return eventoRepository;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    
    public void setUsuarioRepository(IUsuarioRepository repository) {
        lock.writeLock().lock();
        try {
            this.usuarioRepository = repository;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void setEventoRepository(IEventoRepository repository) {
        lock.writeLock().lock();
        try {
            this.eventoRepository = repository;
        } finally {
            lock.writeLock().unlock();
        }
    }
}