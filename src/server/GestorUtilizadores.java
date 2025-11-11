package src.server;

import java.util.HashMap;
import java.util.Map;

//Gerencia informações dos clientes/utilizadores (autenticação, perfis, permissões).
public class GestorUtilizadores {

    private Map<String, String> utilizadores;

    public GestorUtilizadores() {
        utilizadores = new HashMap<>();
    }

    // Registar novo utilizador
    public synchronized boolean registar(String username, String password) {
        if (utilizadores.containsKey(username)) {
            return false; 
        }
        utilizadores.put(username, password);
        return true;
    }

    // Autenticar utilizador
    public synchronized boolean autenticar(String username, String password) {
        return password.equals(utilizadores.get(username));
    }


}
