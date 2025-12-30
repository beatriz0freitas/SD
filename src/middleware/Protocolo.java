package middleware;

import java.io.*;

/**
 * Handler do protocolo de comunicação
 * Formato: [tamanho:int][dados:bytes]
 */
public class Protocolo {
    
    public void enviar(Object obj, DataOutputStream out) throws IOException {
        // Serializa diretamente para ByteArray
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
            oos.flush();
            
            byte[] dados = baos.toByteArray();
            out.writeInt(dados.length);
            out.write(dados);
            out.flush();
        }
    }

    public Object receber(DataInputStream in) throws IOException, ClassNotFoundException {
        int tamanho = in.readInt();
        if (tamanho <= 0 || tamanho > 10_000_000) {
            throw new IOException("Tamanho inválido: " + tamanho);
        }
        
        byte[] dados = new byte[tamanho];
        in.readFully(dados);
        
        // Deserializa diretamente
        try (ByteArrayInputStream bais = new ByteArrayInputStream(dados);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return ois.readObject();
        }
    }
}