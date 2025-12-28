package server.config;

/**
 * Configurações do servidor
 */
public class ServerConfig {
    public static final int DEFAULT_PORT = 5001;
    public static final int DEFAULT_D = 30; // Dias de histórico
    public static final int DEFAULT_S = 10; // Séries máximas em memória

    public static final int MIN_USERNAME_LENGTH = 3;
    public static final int MIN_PASSWORD_LENGTH = 3;

    private static final String ADMIN_PASSWORD = "admin123";

    public static String getAdminPassword() {
        return ADMIN_PASSWORD;
    }
}
