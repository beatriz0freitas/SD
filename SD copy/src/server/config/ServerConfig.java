package server.config;

public class ServerConfig {
    private static final String ADMIN_PASSWORD = System.getenv("ADMIN_PASSWORD");
    
    public static String getAdminPassword() {
        return ADMIN_PASSWORD != null ? ADMIN_PASSWORD : "admin123";
    }
}