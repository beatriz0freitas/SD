package server.data.dao;

/**
 * Factory para criação de DAOs
 */
public class DAOFactory {
    private static DAOFactory instance;
    
    private IUsuarioDAO usuarioDAO;
    private IEventoDAO eventoDAO;
    
    private DAOFactory() {
        // Singleton
    }
    
    public static synchronized DAOFactory getInstance() {
        if (instance == null) {
            instance = new DAOFactory();
        }
        return instance;
    }
    
    public IUsuarioDAO getUsuarioDAO() {
        if (usuarioDAO == null) {
            usuarioDAO = new UsuarioDAOImpl();
        }
        return usuarioDAO;
    }
    
    public IEventoDAO getEventoDAO() {
        if (eventoDAO == null) {
            eventoDAO = new EventoDAOImpl("dados/eventos");
        }
        return eventoDAO;
    }
    
    // Para testes - permite injetar mocks
    public void setUsuarioDAO(IUsuarioDAO dao) {
        this.usuarioDAO = dao;
    }
    
    public void setEventoDAO(IEventoDAO dao) {
        this.eventoDAO = dao;
    }
}