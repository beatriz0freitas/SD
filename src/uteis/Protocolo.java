package src.uteis;

import java.io.*;

/**
 * Utilitários de baixo nível para serialização.
 * Responsabilidade: apenas primitivos e helpers genéricos.
 * 
 * NÃO contém lógica específica de Mensagem (isso está em Mensagem.java)
 */
public class Protocolo {
    
    // ========== PRIMITIVOS ==========
    
    /**
     * Escreve string no stream.
     * FORMATO: [tamanho:int][-1 se null | bytes UTF-8]
     */
    public static void escreverString(DataOutputStream out, String str) throws IOException {
        if (str == null) {
            out.writeInt(-1);
        } else {
            byte[] bytes = str.getBytes("UTF-8");
            out.writeInt(bytes.length);
            out.write(bytes);
        }
    }
    
    /**
     * Lê string do stream
     */
    public static String lerString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length == -1) {
            return null;
        }
        if (length < 0 || length > 10_000_000) { // 10MB max por string
            throw new IOException("String muito grande: " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, "UTF-8");
    }
    
    // ========== API GENÉRICA PARA PAYLOADS ==========
    
    @FunctionalInterface
    public interface PayloadWriter {
        void write(DataOutputStream out) throws IOException;
    }
    
    @FunctionalInterface
    public interface PayloadReader<T> {
        T read(DataInputStream in) throws IOException;
    }
    
    /**
     * Serializa payload usando writer genérico.
     * Retorna apenas os bytes do payload.
     */
    public static byte[] serializar(PayloadWriter writer) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        writer.write(out);
        out.flush();
        return baos.toByteArray();
    }
    
    /**
     * Deserializa payload usando reader genérico
     */
    public static <T> T deserializar(byte[] payload, PayloadReader<T> reader) throws IOException {
        if (payload == null || payload.length == 0) {
            throw new IOException("Payload vazio");
        }
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        return reader.read(in);
    }
}