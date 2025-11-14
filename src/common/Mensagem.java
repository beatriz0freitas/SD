package src.common;

import java.io.*;

/**
 * Representa mensagens trocadas entre cliente e servidor
 */
public class Mensagem {
    
    public enum TipoOperacao {
        REGISTO,
        LOGIN,
        RESPOSTA,
        REG_EVENTO,
        AGREGACAO_INFO,
        FILTRAR,
        NOTIFICACAO,
        SUPORTE,
        DESCONHECIDO
    }
    
    private TipoOperacao tipoOperacao;
    private byte[] payload;
    
    public Mensagem(TipoOperacao tipoOperacao, byte[] payload) {
        this.tipoOperacao = tipoOperacao;
        this.payload = payload;
    }
    
    // ========== GETTERS ==========
    
    public TipoOperacao getTipoOperacao() {
        return tipoOperacao;
    }
    
    public byte[] getPayload() {
        return payload;
    }
    
    // ========== SERIALIZAÇÃO (objeto ↔ bytes em memória) ==========
    
    /**
     * Serializa esta mensagem para array de bytes
     * @return bytes representando a mensagem
     */
    public byte[] serializar() throws IOException {
        return Protocolo.serializarMensagem(this);
    }
    
    /**
     * Deserializa uma mensagem de array de bytes
     * @param bytes Array de bytes representando a mensagem
     * @return Objeto Mensagem reconstruído
     */
    public static Mensagem deserializar(byte[] bytes) throws IOException {
        return Protocolo.deserializarMensagem(bytes);
    }
    
    // ========== ESCRITA/LEITURA NA REDE (bytes → stream) ==========
    
    /**
     * Escreve esta mensagem no stream de saída (envia pela rede)
     */
    public void escrever(DataOutputStream out) throws IOException {
        Protocolo.escreverMensagem(out, this);
    }
    
    /**
     * Lê uma mensagem do stream de entrada (recebe da rede)
     */
    public static Mensagem ler(DataInputStream in) throws IOException {
        return Protocolo.lerMensagem(in);
    }
    
    // ========== FACTORY METHODS - CRIAÇÃO DE MENSAGENS ==========
    
    /**
     * Cria mensagem de registo de utilizador
     */
    public static Mensagem criarRegistarUtilizador(String username, String password) throws IOException {
        byte[] payload = Protocolo.serializarPayloadAutenticacao(username, password);
        return new Mensagem(TipoOperacao.REGISTO, payload);
    }
    
    /**
     * Cria mensagem de autenticação (login)
     */
    public static Mensagem criarAutenticar(String username, String password) throws IOException {
        byte[] payload = Protocolo.serializarPayloadAutenticacao(username, password);
        return new Mensagem(TipoOperacao.LOGIN, payload);
    }
    
    /**
     * Cria mensagem de resposta simples
     */
    public static Mensagem criarResposta(boolean sucesso, String mensagem) throws IOException {
        byte[] payload = Protocolo.serializarPayloadResposta(sucesso, mensagem);
        return new Mensagem(TipoOperacao.RESPOSTA, payload);
    }
    
    // ========== EXTRAÇÃO DE DADOS DO PAYLOAD ==========
    
    /**
     * Extrai dados de autenticação do payload
     * @return Array com [username, password]
     */
    public String[] extrairDadosAutenticacao() throws IOException {
        if (payload == null) {
            throw new IOException("Payload vazio");
        }
        return Protocolo.deserializarPayloadAutenticacao(payload);
    }
    
    /**
     * Extrai resposta do servidor do payload
     */
    public Protocolo.RespostaSimples extrairResposta() throws IOException {
        if (payload == null) {
            throw new IOException("Payload vazio");
        }
        return Protocolo.deserializarPayloadResposta(payload);
    }
    
    /**
     * Verifica se a resposta indica sucesso (atalho)
     */
    public boolean isSuccesso() {
        try {
            return extrairResposta().isSucesso();
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Obtém mensagem de erro/sucesso (atalho)
     */
    public String getMensagemResposta() {
        try {
            return extrairResposta().getMensagem();
        } catch (IOException e) {
            return "Erro ao processar resposta";
        }
    }
    
    @Override
    public String toString() {
        return "Mensagem{" +
                "tipo=" + tipoOperacao +
                ", payloadSize=" + (payload != null ? payload.length : 0) +
                '}';
    }
}

/**
 * NOMENCLATURA:
 * 
 * serializar()    - Mensagem → byte[] (em memória)
 * deserializar()  - byte[] → Mensagem (em memória)
 * 
 * escrever()      - Mensagem → rede (via DataOutputStream)
 * ler()           - rede → Mensagem (via DataInputStream)
 */