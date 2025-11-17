package src.uteis;

import java.io.*;

/**
 * Protocolo de comunicação cliente-servidor.
 * Responsabilidade: serialização de primitivos E mensagens.
 */
public class Protocolo {
    
    // ========== PRIMITIVOS ==========
    
    // assume sempre str != null
    public static void escreverString(DataOutputStream out, String str) throws IOException {
        out.writeUTF(str); 
    }
    
    // assume sempre str != null
    public static String lerString(DataInputStream in) throws IOException {
        return in.readUTF();
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
    
    public static byte[] serializar(PayloadWriter writer) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            writer.write(out);
            out.flush();
            return baos.toByteArray();
        }
    }
    
    public static <T> T deserializar(byte[] payload, PayloadReader<T> reader) throws IOException {
        if (payload == null || payload.length == 0) {
            throw new IOException("Payload vazio");
        }
        ByteArrayInputStream bais = new ByteArrayInputStream(payload);
        try (DataInputStream in = new DataInputStream(bais)) {
            return reader.read(in);
        }
    }
    
    // ========== MENSAGEM I/O ==========
    
    /**
     * Escreve mensagem completa no stream.
     * FORMATO: [tamanho_total:int][tipo:int][tamanho_payload:int][payload:bytes]
     */
    public static void escreverMensagem(Mensagem msg, DataOutputStream out) throws IOException {
        // Serializar em buffer temporário para calcular tamanho
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream temp = new DataOutputStream(buffer);
        
        // Escrever tipo no buffer
        temp.writeInt(msg.getTipo().ordinal());

        //Escrever payload no buffer
        byte[] payload = msg.getPayload();
        temp.writeInt(payload != null ? payload.length : 0);
        if (payload != null && payload.length > 0) {
            temp.write(payload);
        }
        temp.flush();
        
        byte[] dados = buffer.toByteArray();
        
        // Escrever na rede: [tamanho][dados]
        out.writeInt(dados.length);
        out.write(dados);
        out.flush();
    }
    
    /**
     * Lê mensagem completa do stream
     */
    public static Mensagem lerMensagem(DataInputStream in) throws IOException {
        // Ler tamanho total
        int tamanhoTotal = in.readInt();
        
        // Validação de segurança
        if (tamanhoTotal < 0 || tamanhoTotal > 100_000_000) {
            throw new IOException("Tamanho inválido: " + tamanhoTotal);
        }
        
        // Ler dados completos
        byte[] dados = new byte[tamanhoTotal];
        in.readFully(dados);
        
        // Processar dados em buffer temporário
        DataInputStream temp = new DataInputStream(new ByteArrayInputStream(dados));
        
        // Ler tipo da mensagem 
        int tipoOrdinal = temp.readInt();
        if (tipoOrdinal < 0 || tipoOrdinal >= Mensagem.TipoOperacao.values().length) {
            throw new IOException("Tipo inválido: " + tipoOrdinal);
        }
        Mensagem.TipoOperacao tipo = Mensagem.TipoOperacao.values()[tipoOrdinal];
        
        // Ler payload da mensagem
        int payloadSize = temp.readInt();
        if (payloadSize < 0 || payloadSize > 100_000_000) {
            throw new IOException("Payload inválido: " + payloadSize);
        }
        
        byte[] payload = null;
        if (payloadSize > 0) {
            payload = new byte[payloadSize];
            temp.readFully(payload);
        }
        
        return Mensagem.criar(tipo, payload);
    }
}