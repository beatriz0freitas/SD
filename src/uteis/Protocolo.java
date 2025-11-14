package src.uteis;

import java.io.*;

/**
 * Protocolo de comunicação cliente-servidor
 * Separa claramente serialização (objeto→bytes) de escrita/leitura (bytes→rede)
 * 
 * RESUMO DA NOMENCLATURA CORRETA:
 * 
 * SERIALIZAÇÃO (em memória):
 * - serializarString()   : String → byte[]
 * - deserializarString() : byte[] → String
 * - serializarMensagem() : Mensagem → byte[]
 * - deserializarMensagem() : byte[] → Mensagem
 * 
 * ESCRITA/LEITURA (na rede):
 * - escreverString()  : String → DataOutputStream (rede)
 * - lerString()       : DataInputStream (rede) → String
 * - escreverMensagem() : Mensagem → DataOutputStream (rede)
 * - lerMensagem()     : DataInputStream (rede) → Mensagem
 */
public class Protocolo {
    
    // ========== SERIALIZAÇÃO DE STRING (objeto → bytes) ==========
    
    /**
     * Serializa uma string para array de bytes
     * @return bytes representando a string (inclui tamanho)
     */
    public static byte[] serializarString(String str) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        if (str == null) {
            dos.writeInt(-1); // Marca como null
        } else {
            byte[] stringBytes = str.getBytes("UTF-8");
            dos.writeInt(stringBytes.length);
            dos.write(stringBytes);
        }
        
        dos.flush();
        return baos.toByteArray();
    }
    
    /**
     * Deserializa uma string de array de bytes
     */
    public static String deserializarString(byte[] bytes) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        DataInputStream dis = new DataInputStream(bais);
        
        int length = dis.readInt();
        if (length == -1) {
            return null;
        }
        
        byte[] stringBytes = new byte[length];
        dis.readFully(stringBytes);
        return new String(stringBytes, "UTF-8");
    }
    
    // ========== ESCRITA/LEITURA NA REDE (bytes → stream) ==========
    
    /**
     * Escreve uma string diretamente no stream de rede
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
     * Lê uma string diretamente do stream de rede
     */
    public static String lerString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length == -1) {
            return null;
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, "UTF-8");
    }
    
    // ========== SERIALIZAÇÃO DE MENSAGEM (objeto → bytes) ==========
    
    /**
     * Serializa uma mensagem completa para array de bytes
     * Este método CONVERTE o objeto Mensagem em bytes na memória
     */
    public static byte[] serializarMensagem(Mensagem msg) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        // Escrever tipo de operação
        dos.writeInt(msg.getTipoOperacao().ordinal());
        
        // Escrever payload
        byte[] payload = msg.getPayload();
        if (payload == null) {
            dos.writeInt(0);
        } else {
            dos.writeInt(payload.length);
            dos.write(payload);
        }
        
        dos.flush();
        return baos.toByteArray();
    }
    
    /**
     * Deserializa uma mensagem de array de bytes
     * Este método RECONSTRÓI o objeto Mensagem a partir de bytes
     */
    public static Mensagem deserializarMensagem(byte[] bytes) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        DataInputStream dis = new DataInputStream(bais);
        
        // Ler tipo de operação
        int tipoOrdinal = dis.readInt();
        Mensagem.TipoOperacao tipo = Mensagem.TipoOperacao.values()[tipoOrdinal];
        
        // Ler payload
        int payloadSize = dis.readInt();
        byte[] payload = null;
        if (payloadSize > 0) {
            payload = new byte[payloadSize];
            dis.readFully(payload);
        }
        
        return new Mensagem(tipo, payload);
    }
    
    // ========== ESCRITA/LEITURA DE MENSAGEM NA REDE ==========
    
    /**
     * Escreve uma mensagem no stream de rede
     * Este método ENVIA os bytes pela rede
     */
    public static void escreverMensagem(DataOutputStream out, Mensagem msg) throws IOException {
        // Serializar primeiro (objeto → bytes)
        byte[] bytes = serializarMensagem(msg);
        
        // Depois escrever na rede (bytes → stream)
        out.writeInt(bytes.length);  // Tamanho total
        out.write(bytes);            // Dados
        out.flush();
    }
    
    /**
     * Lê uma mensagem do stream de rede
     * Este método RECEBE bytes da rede e reconstrói o objeto
     */
    public static Mensagem lerMensagem(DataInputStream in) throws IOException {
        // Ler tamanho total
        int tamanhoTotal = in.readInt();
        
        // Ler todos os bytes da mensagem
        byte[] bytes = new byte[tamanhoTotal];
        in.readFully(bytes);
        
        // Deserializar (bytes → objeto)
        return deserializarMensagem(bytes);
    }
    
    // ========== SERIALIZAÇÃO DE PAYLOADS ESPECÍFICOS ==========
    //todo: nao conseguimos melhorar isto??
    
    /**
     * Serializa payload de autenticação (username + password)
     * Retorna apenas os BYTES do payload, não a mensagem completa
     */
    public static byte[] serializarPayloadAutenticacao(String username, String password) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        escreverString(dos, username);
        escreverString(dos, password);
        
        dos.flush();
        return baos.toByteArray();
    }
    
    /**
     * Deserializa payload de autenticação
     */
    public static String[] deserializarPayloadAutenticacao(byte[] payload) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(payload);
        DataInputStream dis = new DataInputStream(bais);
        
        String username = lerString(dis);
        String password = lerString(dis);
        
        return new String[]{username, password};
    }
    
    /**
     * Serializa payload de resposta (sucesso + mensagem)
     */
    public static byte[] serializarPayloadResposta(boolean sucesso, String mensagem) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        dos.writeBoolean(sucesso);
        escreverString(dos, mensagem);
        
        dos.flush();
        return baos.toByteArray();
    }
    
    /**
     * Deserializa payload de resposta
     */
    public static RespostaSimples deserializarPayloadResposta(byte[] payload) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(payload);
        DataInputStream dis = new DataInputStream(bais);
        
        boolean sucesso = dis.readBoolean();
        String mensagem = lerString(dis);
        
        return new RespostaSimples(sucesso, mensagem);
    }

}

