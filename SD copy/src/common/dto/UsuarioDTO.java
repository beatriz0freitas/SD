package common.dto;

import java.io.Serializable;

public class UsuarioDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String username;
    private String password;
    
    public UsuarioDTO() {}
    
    public UsuarioDTO(String username, String password) {
        this.username = username;
        this.password = password;
    }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    @Override
    public String toString() {
        return "UsuarioDTO{username='" + username + "'}";
    }
}