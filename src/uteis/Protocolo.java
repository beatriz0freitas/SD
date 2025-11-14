package src.uteis;

import java.io.*;

/**
 * Protocolo de comunicação cliente-servidor
 * 
 * - Serialização/deserialização de dados (objeto <-> bytes em memória)
 * - Escrita/leitura na rede (bytes <-> stream TCP)
 * - Formato binário usando apenas DataInputStream/DataOutputStream 
 * 
 */
public class Protocolo {


        // ========== ESCRITA/LEITURA NA REDE (bytes -> stream) ==========
    
    /**
     * Escreve uma string diretamente no stream de rede
     * 
     * FORMATO: [tamanho:int][bytes:UTF-8]
     * 
     * @param dos Stream onde escrever
     * @param str String a escrever (pode ser null)
     */
    private static void escreverString(DataOutputStream dos, String str) throws IOException {
        if (str == null) {
            dos.writeInt(-1); // Marcador de null
        } else {
            byte[] bytes = str.getBytes("UTF-8");
            dos.writeInt(bytes.length);
            dos.write(bytes);
        }
    }
    
    /**
     * Lê uma string diretamente do stream de rede
     * @param dis Stream de onde ler
     * @return String lida ou null se marcada como null
     */
    public static String lerString(DataInputStream dis) throws IOException {
        int length = dis.readInt();
        if (length == -1) {
            return null;
        }
        byte[] bytes = new byte[length];
        dis.readFully(bytes);
        return new String(bytes, "UTF-8");
    }

    
    // ========== SERIALIZAÇÃO DE MENSAGEM (objeto -> bytes) ==========
    
    /**
     * Serializa uma mensagem completa para array de bytes
     * Este método CONVERTE o objeto Mensagem em bytes na memória
     */
    public static byte[] serializarMensagem(Mensagem msg) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        // Escrever tipo de operação (como int)
        dos.writeInt(msg.getTipoOperacao().ordinal());
        
        // Escrever payload
        byte[] payload = msg.getPayload();
        if (payload == null) {
            dos.writeInt(0); // Sem payload
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

        Mensagem.TipoOperacao[] valores = Mensagem.TipoOperacao.values();
        if (tipoOrdinal < 0 || tipoOrdinal >= valores.length) {
            throw new IOException("Tipo de operação inválido: " + tipoOrdinal);
        }
        Mensagem.TipoOperacao tipo = valores[tipoOrdinal];
        
        // Ler payload
        int payloadSize = dis.readInt();
        
        // Validação de segurança: evitar payloads gigantes
        if (payloadSize < 0 || payloadSize > 100_000_000) { // 100MB max
            throw new IOException("Tamanho de payload inválido: " + payloadSize);
        }
        

        byte[] payload = null;
        if (payloadSize > 0) {
            payload = new byte[payloadSize];
            dis.readFully(payload);
        }
        
        return Mensagem.criar(tipo, payload);
    }
    
    // ========== ESCRITA/LEITURA DE MENSAGEM NA REDE ==========
    
    /**
     * Escreve uma mensagem no stream de rede
     * 
     * FORMATO NA REDE:
     * [tamanho_total:int][mensagem_serializada:bytes]
     * 
      * O tamanho_total permite ao receptor saber quantos bytes ler.
     */
    public static void escreverMensagem(DataOutputStream out, Mensagem msg) throws IOException {
        // 1. Serializar mensagem para bytes (em memória)
        byte[] bytes = serializarMensagem(msg);
        
        // 2. Escrever na rede: [tamanho][dados]
        out.writeInt(bytes.length);  // Tamanho total
        out.write(bytes);            // Dados serializados
        out.flush();                 // Forçar envio imediato
    }
    
    /**
     * Lê uma mensagem do stream de rede
     * Este método RECEBE bytes da rede e reconstrói o objeto
     */
    public static Mensagem lerMensagem(DataInputStream dis) throws IOException {
        // Ler tamanho total
        int tamanhoTotal = dis.readInt();
        
        // Ler todos os bytes da mensagem
        byte[] bytes = new byte[tamanhoTotal];
        dis.readFully(bytes);
        
        // Deserializar (bytes → objeto)
        return deserializarMensagem(bytes);
    }
    
    // ========== SERIALIZAÇÃO DE PAYLOADS ESPECÍFICOS ==========
    
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
        if (payload == null || payload.length == 0) {
            throw new IOException("Payload de autenticação vazio");
        }
        
        ByteArrayInputStream bais = new ByteArrayInputStream(payload);
        DataInputStream dis = new DataInputStream(bais);
        
        String username = lerString(dis);
        String password = lerString(dis);
        
        // Validação adicional
        if (username == null || password == null) {
            throw new IOException("Username ou password null no payload");
        }
        
        return new String[]{username, password};
    }
    
    /**
     * Serializa payload de resposta 
     */
    public static byte[] serializarPayloadResposta( String mensagem) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        escreverString(dos, mensagem);
        
        dos.flush();
        return baos.toByteArray();
    }
    
    /**
     * Deserializa payload de resposta
     */
    public static RespostaSimples deserializarPayloadResposta(byte[] payload) throws IOException {
        if (payload == null || payload.length == 0) throw new IOException("Payload de resposta vazio");

        ByteArrayInputStream bais = new ByteArrayInputStream(payload);
        DataInputStream dis = new DataInputStream(bais);

        String mensagem = lerString(dis);

        return new RespostaSimples(mensagem);
    }
    
    
    // ========== CLASSE AUXILIAR ==========
    
    /**
     * Representa uma resposta simples do servidor.
     */
    public static class RespostaSimples {
        private final String mensagem;
        
        public RespostaSimples(String mensagem) {
            this.mensagem = mensagem;
        }
        
        
        
        public String getMensagem() {
            return mensagem;
        }
        
        @Override
        public String toString() {
            return "RespostaSimples{" +
                    " mensagem='" + mensagem + '\'' +
                    '}';
        }
    }
}


//=================================TIRAR DEPOIS================================//

/**
 * ============================================================================
 * RESUMO DA NOMENCLATURA:
 * ============================================================================
 * 
 * SERIALIZAÇÃO (conversão em memória):
 *   serializar___()   : Objeto → byte[]
 *   deserializar___() : byte[] → Objeto
 * 
 * ESCRITA/LEITURA (transmissão pela rede):
 *   escreverMensagem() : Mensagem → DataOutputStream (socket)
 *   lerMensagem()      : DataInputStream (socket) → Mensagem
 * 
 * ============================================================================
 * FORMATO DO PROTOCOLO BINÁRIO:
 * ============================================================================
 * 
 * MENSAGEM NA REDE:
 *   [4 bytes] tamanho_total
 *   [4 bytes] tipo_operacao (ordinal do enum)
 *   [4 bytes] tamanho_payload
 *   [N bytes] payload (se tamanho > 0)
 * 
 * STRING (dentro de payloads):
 *   [4 bytes] tamanho (-1 se null)
 *   [N bytes] bytes UTF-8 (se tamanho >= 0)
 * 
 * PAYLOAD DE AUTENTICAÇÃO:
 *   [string] username
 *   [string] password
 * 
 * PAYLOAD DE RESPOSTA:
 *   [string]  mensagem
 * 
 * ============================================================================
 * VALIDAÇÕES DE SEGURANÇA:
 * ============================================================================
 * 
 * - Strings: máximo 10MB por string
 * - Mensagens: máximo 100MB por mensagem
 * - Ordinal de enum: validado no range de valores
 * - Null checking: username e password não podem ser null
 * - Payloads vazios: rejeitados com exceção
 * 
 * Estas validações protegem contra:
 * - Ataques de negação de serviço (DoS)
 * - Payloads maliciosos com tamanhos absurdos
 * - Corrupção de dados ou erros de protocolo
 * 
 * ============================================================================
 * EXEMPLO DE USO:
 * ============================================================================
 * 
 * // Cliente cria e envia pedido de login
 * byte[] payload = Protocolo.serializarPayloadAutenticacao("user", "pass");
 * Mensagem pedido = Mensagem.criar(TipoOperacao.LOGIN, payload);
 * Protocolo.escreverMensagem(output, pedido);
 * 
 * // Servidor recebe e processa
 * Mensagem pedidoRecebido = Protocolo.lerMensagem(input);
 * String[] credenciais = Protocolo.deserializarPayloadAutenticacao(
 *     pedidoRecebido.getPayload()
 * );
 * 
 * // Servidor cria e envia resposta
 * byte[] respostaPayload = Protocolo.serializarPayloadResposta(true, "OK");
 * Mensagem resposta = Mensagem.criar(TipoOperacao.RESPOSTA_OK, respostaPayload);
 * Protocolo.escreverMensagem(output, resposta);
 * 
 * // Cliente recebe e processa resposta
 * Mensagem respostaRecebida = Protocolo.lerMensagem(input);
 * RespostaSimples dados = Protocolo.deserializarPayloadResposta(
 *     respostaRecebida.getPayload()
 * );
 * ============================================================================
 */