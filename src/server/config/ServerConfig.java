package server.config;

public class ServerConfig {
    // Porta padrão
    public static final int DEFAULT_PORT = 5001;
    
    // Dias padrão
    public static final int DEFAULT_D = 30;
    
    // Password de admin
    private static final String ADMIN_PASSWORD = System.getenv("ADMIN_PASSWORD");
    
    public static String getAdminPassword() {
        return ADMIN_PASSWORD != null ? ADMIN_PASSWORD : "admin123";
    }
}
