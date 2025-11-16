package src.uteis;

import java.io.*;

/**
 * Representa mensagens trocadas entre cliente e servidor
 * 
 * RESPONSABILIDADE:
 * - Encapsula tipo de operação + payload
 * - Factory methods para criar mensagens comuns
 * - Métodos de conveniência para extrair dados
 * 
 * NOMENCLATURA:
 * serializar()    - Mensagem → byte[] (em memória)
 * deserializar()  - byte[] → Mensagem (em memória)
 * 
 * escrever()      - Mensagem → rede (via DataOutputStream)
 * ler()           - rede → Mensagem (via DataInputStream)
 */

public class Mensagem {
    
    /**
     * Tipos de operações suportadas pelo protocolo.
     * Cada operação corresponde a uma funcionalidade do enunciado.
     */
    public enum TipoOperacao {
        // Autenticação 
        REGISTO, // Registo de utilizador
        LOGIN,   // Autenticação (login)
        
        // Registo de eventos 
        REG_EVENTO, 
        NOVO_DIA, 
        
        // Agregação 
        QUANTIDADE_VENDAS, 
        VOLUME_VENDAS,  
        PRECO_MEDIO,
        PRECO_MAXIMO,
        
        // Filtrar 
        FILTRAR_EVENTOS,
        
        // Notificações 
        VENDAS_SIMULTANEAS,     
        VENDAS_CONSECUTIVAS, 
        
        // Respostas
        RESPOSTA_OK, 
        RESPOSTA_ERRO 
    }
    
    private TipoOperacao tipoOperacao;
    private byte[] payload;
    
     /**
     * Construtor privado - usar factory methods para criar mensagens
     */
    private Mensagem(TipoOperacao tipoOperacao, byte[] payload) {
        this.tipoOperacao = tipoOperacao;
        this.payload = payload;
    }
        
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
    public void escrever(DataOutputStream output) throws IOException {
        Protocolo.escreverMensagem(output, this);
    }
    
    /**
     * Lê uma mensagem do stream de entrada (recebe da rede)
     */
    public static Mensagem ler(DataInputStream input) throws IOException {
        return Protocolo.lerMensagem(input);
    }
    


    // ========== FACTORY METHODS - CRIAÇÃO DE MENSAGENS ==========
    
    public static Mensagem criar(TipoOperacao tipo, byte[] payload) {
        return new Mensagem(tipo, payload);
    }

    /**
     * Cria mensagem de resposta de sucesso.
     * 
     * @param mensagem Mensagem descritiva do resultado
     */
    public static Mensagem criarRespostaOk(String mensagem) throws IOException {
        return new Mensagem(TipoOperacao.RESPOSTA_OK, Protocolo.payloadResposta(mensagem));
    }
    
    /** Cria mensagem de resposta de erro.
     * 
     * @param mensagem Descrição do erro
     */
    public static Mensagem criarRespostaErro(String mensagem) throws IOException {
        return new Mensagem(TipoOperacao.RESPOSTA_ERRO, Protocolo.payloadResposta(mensagem));
    }

    /**
     * Cria mensagem de registo de utilizador (signup)
     */
    public static Mensagem criarRegistarUtilizador(String username, String password) throws IOException {
        return new Mensagem(TipoOperacao.REGISTO, Protocolo.payloadAutenticacao(username, password));
    }

    /**
     * Cria mensagem de autenticação (login)
     */
    public static Mensagem criarAutenticar(String username, String password) throws IOException {
        return new Mensagem(TipoOperacao.LOGIN, Protocolo.payloadAutenticacao(username, password));
    }
    
    /**
     * Cria mensagem de registo de evento
     */
    public static Mensagem criarRegistarEvento(int produtoID, int quantidade, double preco) throws IOException {
        return new Mensagem(TipoOperacao.REG_EVENTO, Protocolo.payloadEvento(produtoID, quantidade, preco));
    }
    
    
    
    // ========== EXTRAÇÃO DE DADOS DO PAYLOAD ==========
    
    /**
     * Extrai resposta do servidor do payload
     */
    // public Protocolo.RespostaSimples extrairResposta() throws IOException {
    //     if (payload == null) {
    //         throw new IOException("Payload vazio");
    //     }
    //     return Protocolo.deserializarPayloadResposta(payload);
    // }
    

    // ========== MÉTODOS UTEIS ==========

    /**
     * Verifica se a resposta indica sucesso (atalho).
     * Útil para validação rápida no cliente.
     * 
     * @return true se RESPOSTA_OK, false caso contrário
     */
    public boolean isSuccesso() {
        return TipoOperacao.RESPOSTA_OK.equals(tipoOperacao);
    }
    
    //todo: perceber se esta definicao nao faz mais sentido
    //public boolean isSuccesso() {
    //  return tipoOperacao == TipoOperacao.RESPOSTA_OK;
    //}
    
    /**
     * Obtém mensagem de erro/sucesso (atalho)
     */
    // public String getMensagemResposta() {
    //     try {
    //         return extrairResposta().getMensagem();
    //     } catch (IOException e) {
    //         return "Erro ao processar resposta";
    //     }
    // }



 

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Mensagem mensagem = (Mensagem) obj;
        if (tipoOperacao != mensagem.tipoOperacao) return false;
        if (payload == null && mensagem.payload == null) return true;
        if (payload == null || mensagem.payload == null) return false;
        if (payload.length != mensagem.payload.length) return false;
        
        for (int i = 0; i < payload.length; i++) {
            if (payload[i] != mensagem.payload[i]) return false;
        }
        return true;
    }
    
    @Override
    public String toString() {
        return "Mensagem{" +
                "tipo=" + tipoOperacao +
                ", payloadSize=" + (payload != null ? payload.length : 0) +
                '}';
    }
}


//=================================TIRAR DEPOIS================================//

/**
 * ============================================================================
 * NOMENCLATURA DO PROTOCOLO:
 * ============================================================================
 * 
 * SERIALIZAÇÃO (operações em memória):
 *   serializar()    - Objeto → byte[]
 *   deserializar()  - byte[] → Objeto
 * 
 * ESCRITA/LEITURA (operações na rede):
 *   escrever()      - Objeto → DataOutputStream (socket)
 *   ler()           - DataInputStream (socket) → Objeto
 * 
 * ============================================================================
 * FLUXO TÍPICO:
 * ============================================================================
 * 
 * CLIENTE ENVIA:
 *   1. Mensagem.criarAutenticar("user", "pass")  // cria objeto
 *   2. mensagem.escrever(output)                 // envia pela rede
 * 
 * SERVIDOR RECEBE:
 *   1. Mensagem pedido = Mensagem.ler(input)     // recebe da rede
 *   2. String[] dados = pedido.extrairDadosAutenticacao() // processa
 * 
 * SERVIDOR RESPONDE:
 *   1. Mensagem.criarRespostaOk("Login OK")      // cria resposta
 *   2. resposta.escrever(output)                 // envia pela rede
 * 
 * CLIENTE RECEBE:
 *   1. Mensagem resposta = Mensagem.ler(input)   // recebe da rede
 *   2. if (resposta.isSuccesso()) { ... }        // valida
 * ============================================================================
 */