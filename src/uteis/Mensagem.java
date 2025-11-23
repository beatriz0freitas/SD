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
        REGISTO, LOGIN, LOGIN_ADMIN,
        REG_EVENTO, NOVO_DIA,  LISTAR_CLIENTES, LISTAR_EVENTOS,
        QUANTIDADE_VENDAS, VOLUME_VENDAS, PRECO_MEDIO, PRECO_MAXIMO,
        FILTRAR_EVENTOS, VENDAS_SIMULTANEAS, VENDAS_CONSECUTIVAS,
        RESPOSTA_OK, RESPOSTA_ERRO, RESPOSTA_LISTA_CLIENTES,
        RESPOSTA_LISTA_EVENTOS
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

    // --- Admin ---

    public static Mensagem criarAutenticarAdmin(String password) throws IOException {
        byte[] payload = Protocolo.serializar(out -> 
            Protocolo.escreverString(out, password)
        );
        return new Mensagem(TipoOperacao.LOGIN_ADMIN, payload);
    }
    
    public static Mensagem criarListarClientes() {
        return new Mensagem(TipoOperacao.LISTAR_CLIENTES, new byte[0]);
    }
    
    public static Mensagem criarListarEventos() {
        return new Mensagem(TipoOperacao.LISTAR_EVENTOS, new byte[0]);
    }
    
    public static Mensagem criarNovoDia() {
        return new Mensagem(TipoOperacao.NOVO_DIA, new byte[0]);
    }

    // generalizado para evitar duplicação de código, recebe o tipo de operação
    public static Mensagem criarPedidoAgregacao(int produto, int dias, TipoOperacao tipo) throws IOException {
        byte[] payload = Protocolo.serializar(out -> {
            out.writeInt(produto);
            out.writeInt(dias);
        });
        return new Mensagem(tipo, payload);
    }
    
    @Override
    public String toString() {
        return "Mensagem{tipo=" + tipo + ", payloadSize=" + 
               (payload != null ? payload.length : 0) + "}";
    }
}