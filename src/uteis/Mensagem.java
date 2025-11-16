package src.uteis;

import java.io.*;

/**
 * Mensagem auto-suficiente: sabe como se serializar e deserializar.
 * NÃO delega para Protocolo (exceto para primitivos).
 */
public class Mensagem {
    
    public enum TipoOperacao {
        REGISTO, LOGIN,
        REG_EVENTO, NOVO_DIA,
        QUANTIDADE_VENDAS, VOLUME_VENDAS, PRECO_MEDIO, PRECO_MAXIMO,
        FILTRAR_EVENTOS, VENDAS_SIMULTANEAS, VENDAS_CONSECUTIVAS,
        RESPOSTA_OK, RESPOSTA_ERRO
    }
    
    private final TipoOperacao tipo;
    private final byte[] payload;
    
    // ========== CONSTRUTOR ==========
    
    private Mensagem(TipoOperacao tipo, byte[] payload) {
        this.tipo = tipo;
        this.payload = payload;
    }
    
    public TipoOperacao getTipoOperacao() {
        return tipo;
    }
    
    public byte[] getPayload() {
        return payload;
    }
    
    public boolean isSuccesso() {
        return tipo == TipoOperacao.RESPOSTA_OK;
    }
    
    // ========== I/O: ESCREVER/LER NA REDE ==========
    
    /**
     * Escreve mensagem completa no stream.
     * FORMATO: [tamanho_total:int][tipo:int][tamanho_payload:int][payload:bytes]
     * 
     */
    public void escrever(DataOutputStream out) throws IOException {
        // Serializar em buffer temporário para calcular tamanho
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream temp = new DataOutputStream(buffer);
        
        // Escrever tipo e payload no buffer
        temp.writeInt(tipo.ordinal());
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
     * Lê mensagem completa do stream.
     */
    public static Mensagem ler(DataInputStream in) throws IOException {
        // Ler tamanho total
        int tamanhoTotal = in.readInt();
        
        // Validação de segurança
        if (tamanhoTotal < 0 || tamanhoTotal > 100_000_000) {
            throw new IOException("Tamanho inválido: " + tamanhoTotal);
        }
        
        // Ler dados completos
        byte[] dados = new byte[tamanhoTotal];
        in.readFully(dados);
        
        // Deserializar
        DataInputStream temp = new DataInputStream(new ByteArrayInputStream(dados));
        
        int tipoOrdinal = temp.readInt();
        if (tipoOrdinal < 0 || tipoOrdinal >= TipoOperacao.values().length) {
            throw new IOException("Tipo inválido: " + tipoOrdinal);
        }
        TipoOperacao tipo = TipoOperacao.values()[tipoOrdinal];
        
        int payloadSize = temp.readInt();
        if (payloadSize < 0 || payloadSize > 100_000_000) {
            throw new IOException("Payload inválido: " + payloadSize);
        }
        
        byte[] payload = null;
        if (payloadSize > 0) {
            payload = new byte[payloadSize];
            temp.readFully(payload);
        }
        
        return new Mensagem(tipo, payload);
    }
    
    // ========== FACTORY METHODS ==========
    
    public static Mensagem criar(TipoOperacao tipo, byte[] payload) {
        return new Mensagem(tipo, payload);
    }
    
    // --- Respostas ---
    
    public static Mensagem criarRespostaOk(String mensagem) throws IOException {
        byte[] payload = Protocolo.serializar(out -> 
            Protocolo.escreverString(out, mensagem)
        );
        return new Mensagem(TipoOperacao.RESPOSTA_OK, payload);
    }
    
    public static Mensagem criarRespostaErro(String mensagem) throws IOException {
        byte[] payload = Protocolo.serializar(out -> 
            Protocolo.escreverString(out, mensagem)
        );
        return new Mensagem(TipoOperacao.RESPOSTA_ERRO, payload);
    }
    
    // --- Autenticação ---
    
    public static Mensagem criarRegistarUtilizador(String username, String password) throws IOException {
        byte[] payload = Protocolo.serializar(out -> {
            Protocolo.escreverString(out, username);
            Protocolo.escreverString(out, password);
        });
        return new Mensagem(TipoOperacao.REGISTO, payload);
    }
    
    public static Mensagem criarAutenticar(String username, String password) throws IOException {
        byte[] payload = Protocolo.serializar(out -> {
            Protocolo.escreverString(out, username);
            Protocolo.escreverString(out, password);
        });
        return new Mensagem(TipoOperacao.LOGIN, payload);
    }
    
    // --- Eventos ---
    
    public static Mensagem criarRegistarEvento(int produtoID, int quantidade, double preco) throws IOException {
        byte[] payload = Protocolo.serializar(out -> {
            out.writeInt(produtoID);
            out.writeInt(quantidade);
            out.writeDouble(preco);
        });
        return new Mensagem(TipoOperacao.REG_EVENTO, payload);
    }
    
    public static Mensagem criarNovoDia() {
        return new Mensagem(TipoOperacao.NOVO_DIA, new byte[0]);
    }
    
    @Override
    public String toString() {
        return "Mensagem{tipo=" + tipo + ", payloadSize=" + 
               (payload != null ? payload.length : 0) + "}";
    }
}