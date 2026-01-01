package server.data.repository;

/**
 * Factory para criação de Repositories
 */
public class RepositoryFactory {
    private static RepositoryFactory instance;
    
    private IUsuarioRepository usuarioRepository;
    private IEventoRepository eventoRepository;
    
    private RepositoryFactory() {
        // Singleton
    }
    
    public static synchronized RepositoryFactory getInstance() {
        if (instance == null) {
            instance = new RepositoryFactory();
        }
        return instance;
    }
    
    public IUsuarioRepository getUsuarioRepository() {
        if (usuarioRepository == null) {
            usuarioRepository = new UsuarioFileRepository();
        }
        return usuarioRepository;
    }
    
    public IEventoRepository getEventoRepository() {
        if (eventoRepository == null) {
            eventoRepository = new EventoFileRepository("dados/eventos");
        }
        return eventoRepository;
    }
    
    // Para testes - permite injetar mocks
    public void setUsuarioRepository(IUsuarioRepository repository) {
        this.usuarioRepository = repository;
    }
    
    public void setEventoRepository(IEventoRepository repository) {
        this.eventoRepository = repository;
    }
}