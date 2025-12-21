package middleware.protocol;

import java.io.*;

/**
 * Handler do protocolo de comunicação
 * Formato: [tamanho:int][dados:bytes]
 */
public class ProtocoloHandler {
    private final MessageSerializer serializer;
    
    public ProtocoloHandler() {
        this.serializer = new MessageSerializer();
    }
    
    /**
     * Envia objeto pelo stream
     */
    public void enviar(Object obj, DataOutputStream out) throws IOException {
        // 1. Serializar objeto
        byte[] dados = serializer.serialize(obj);
        
        // 2. Enviar tamanho
        out.writeInt(dados.length);
        
        // 3. Enviar dados
        out.write(dados);
        out.flush();
    }
    
    /**
     * Recebe objeto do stream
     */
    public <T> T receber(DataInputStream in, Class<T> clazz) 
            throws IOException, ClassNotFoundException {
        
        // 1. Ler tamanho
        int tamanho = in.readInt();
        
        if (tamanho <= 0 || tamanho > 10_000_000) {
            throw new IOException("Tamanho inválido: " + tamanho);
        }
        
        // 2. Ler dados
        byte[] dados = new byte[tamanho];
        in.readFully(dados);
        
        // 3. Deserializar
        return serializer.deserialize(dados, clazz);
    }
}
