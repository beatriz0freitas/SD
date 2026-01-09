package server.config;


public class ServerConfig {
    public static final int DEFAULT_PORT = 5001;
    public static final int DEFAULT_D = 30; 
    public static final int DEFAULT_S = 10; 

    public static final int N_WORKERS_SERVER = 50;

    public static final int N_CLIENT_HANDLERS = 20;
public static final int REQUEST_QUEUE_SIZE = 100;


    public static final int MIN_USERNAME_LENGTH = 3;
    public static final int MIN_PASSWORD_LENGTH = 3;

    private static final String ADMIN_PASSWORD = "admin123";

    public static String getAdminPassword() {
        return ADMIN_PASSWORD;
    }

    
}
