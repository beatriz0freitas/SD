package middleware.protocol;

import java.io.*;

/**
 * Serializador de mensagens usando Java Serialization
 */
public class MessageSerializer {
    
    /**
     * Serializa objeto para bytes
     */
    public byte[] serialize(Object obj) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            
            oos.writeObject(obj);
            oos.flush();
            return baos.toByteArray();
        }
    }
    
    /**
     * Deserializa bytes para objeto
     */
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] data, Class<T> clazz) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            
            return (T) ois.readObject();
        }
    }
}