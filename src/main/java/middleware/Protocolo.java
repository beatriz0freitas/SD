package middleware;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

/**
 * Handler do protocolo de comunicação
 * Formato: [tamanho:int][dados:bytes]
 * 
 * Agora usa métodos serialize() dos DTOs
 */
public class Protocolo {
    
    public void enviar(Object obj, DataOutputStream out) throws IOException {
        byte[] dados;
        
        if (obj instanceof Serializable) {
            // Chama método serialize() do objeto
            try {
                java.lang.reflect.Method serializeMethod = obj.getClass().getMethod("serialize");
                dados = (byte[]) serializeMethod.invoke(obj);
            } catch (Exception e) {
                throw new IOException("Erro ao serializar objeto: " + e.getMessage(), e);
            }
        } else {
            throw new IOException("Objeto não é serializável: " + obj.getClass().getName());
        }
        
        out.writeInt(dados.length);
        out.write(dados);
        out.flush();
    }
    
    public Object receber(DataInputStream in) throws IOException, ClassNotFoundException {
        int tamanho = in.readInt();
        if (tamanho <= 0 || tamanho > 10_000_000) {
            throw new IOException("Tamanho inválido: " + tamanho);
        }
        
        byte[] dados = new byte[tamanho];
        in.readFully(dados);
        
        // Fallback para deserialização Java padrão
        try (ByteArrayInputStream bais = new ByteArrayInputStream(dados);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return ois.readObject();
        }
    }
}