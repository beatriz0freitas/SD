
package common.dto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;


class RespostaDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private boolean sucesso;
    private String mensagem;
    private Object dados;
    
    public RespostaDTO() {}
    
    public RespostaDTO(boolean sucesso, String mensagem) {
        this.sucesso = sucesso;
        this.mensagem = mensagem;
    }
    
    public RespostaDTO(boolean sucesso, String mensagem, Object dados) {
        this.sucesso = sucesso;
        this.mensagem = mensagem;
        this.dados = dados;
    }
    
    public byte[] serialize() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        
        out.writeBoolean(sucesso);
        out.writeUTF(mensagem != null ? mensagem : "");
        
        // Serializa dados se presente (usando serialização Java padrão para Object)
        if (dados != null) {
            out.writeBoolean(true);
            ByteArrayOutputStream tempBaos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(tempBaos)) {
                oos.writeObject(dados);
            }
            byte[] dadosBytes = tempBaos.toByteArray();
            out.writeInt(dadosBytes.length);
            out.write(dadosBytes);
        } else {
            out.writeBoolean(false);
        }
        
        out.flush();
        return baos.toByteArray();
    }
    
    public static RespostaDTO deserialize(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream in = new DataInputStream(bais);
        
        RespostaDTO dto = new RespostaDTO();
        dto.sucesso = in.readBoolean();
        dto.mensagem = in.readUTF();
        
        boolean temDados = in.readBoolean();
        if (temDados) {
            int tamanho = in.readInt();
            byte[] dadosBytes = new byte[tamanho];
            in.readFully(dadosBytes);
            
            try (ObjectInputStream ois = new ObjectInputStream(
                    new ByteArrayInputStream(dadosBytes))) {
                dto.dados = ois.readObject();
            } catch (ClassNotFoundException e) {
                throw new IOException("Erro ao deserializar dados", e);
            }
        }
        
        return dto;
    }
    
    public static RespostaDTO sucesso(String mensagem) {
        return new RespostaDTO(true, mensagem);
    }
    
    public static RespostaDTO sucesso(String mensagem, Object dados) {
        return new RespostaDTO(true, mensagem, dados);
    }
    
    public static RespostaDTO erro(String mensagem) {
        return new RespostaDTO(false, mensagem);
    }
    
    public boolean isSucesso() { return sucesso; }
    public void setSucesso(boolean sucesso) { this.sucesso = sucesso; }
    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }
    public Object getDados() { return dados; }
    public void setDados(Object dados) { this.dados = dados; }
    
    @Override
    public String toString() {
        return String.format("RespostaDTO{sucesso=%b, mensagem='%s'}", sucesso, mensagem);
    }
}
