package src.uteis;

import java.io.IOException;

/**
 * Representa mensagens trocadas entre cliente e servidor.
 * Responsabilidade: encapsular dados (DTO) + factory methods.
 * 
 * I/O delegado para Protocolo.
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
    
    // ========== GETTERS ==========
    
    public TipoOperacao getTipo() {
        return tipo;
    }
    
    public byte[] getPayload() {
        return payload;
    }
    
    public boolean isSuccesso() {
        return tipo == TipoOperacao.RESPOSTA_OK;
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