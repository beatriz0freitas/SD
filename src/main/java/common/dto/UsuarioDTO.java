package common.dto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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
    
    // Serialização customizada
    public byte[] serialize() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        
        out.writeUTF(username != null ? username : "");
        out.writeUTF(password != null ? password : "");
        
        out.flush();
        return baos.toByteArray();
    }
    
    // Deserialização customizada
    public static UsuarioDTO deserialize(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream in = new DataInputStream(bais);
        
        UsuarioDTO dto = new UsuarioDTO();
        dto.username = in.readUTF();
        dto.password = in.readUTF();
        
        return dto;
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