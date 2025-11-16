package src.uteis;

import java.io.*;
import java.util.function.Consumer;

/**
 * Protocolo de comunicação cliente-servidor
 * Separa claramente serialização (objeto→bytes) de escrita/leitura (bytes→rede)
 * 
 * RESUMO DA NOMENCLATURA CORRETA:
 * 
 * SERIALIZAÇÃO (em memória):
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

     // ========== ESCRITA/LEITURA NA REDE (bytes -> stream) ==========
    
    /**
     * Escreve uma string diretamente no stream de rede
     * 
     * FORMATO: [tamanho:int][bytes:UTF-8]
     * 
     * @param dos Stream onde escrever
     * @param str String a escrever (pode ser null)
     */
    public static void escreverString(DataOutputStream output, String str) throws IOException {
        if (str == null) {
            output.writeInt(-1); // Marcador de null
        } else {
            byte[] bytes = str.getBytes("UTF-8");
            output.writeInt(bytes.length);
            output.write(bytes);
        }
    }
    
    /**
     * Lê uma string diretamente do stream de rede
     * @param dis Stream de onde ler
     * @return String lida ou null se marcada como null
     */
    public static String lerString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length == -1) {
            return null;
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, "UTF-8");
    }

    // ============================================================
    //  MÉTODOS GENÉRICOS PARA PAYLOADS
    // ============================================================
    public static byte[] escreverPayload(Consumer<DataOutputStream> writer) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(baos);
        writer.accept(output);
        output.flush();
        return baos.toByteArray();
    }

    /*
     * Lê um payload genérico usando o reader fornecido - qualquer tipo de payload distinto
     */
    public static <T> T lerPayload(byte[] payload, PayloadReader<T> reader) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        return reader.read(in);
    }

    @FunctionalInterface
    public interface PayloadReader<T> {
        T read(DataInputStream in) throws IOException;
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
    public static void escreverMensagem(DataOutputStream output, Mensagem mensagem) throws IOException {
        byte[] bytes = serializarMensagem(mensagem);
        output.writeInt(bytes.length);  // Tamanho total
        output.write(bytes);            // Dados serializados
        output.flush();                 // Forçar envio imediato
    }
    
    /**
     * Lê uma mensagem do stream de rede
     * Este método RECEBE bytes da rede e reconstrói o objeto
     */
    public static Mensagem lerMensagem(DataInputStream dis) throws IOException {
        int tamanhoTotal = dis.readInt();
        byte[] bytes = new byte[tamanhoTotal];
        dis.readFully(bytes);
        return deserializarMensagem(bytes);
    }
    

    // ========== SERIALIZAÇÃO DE MENSAGEM (objeto -> bytes) ==========
    
    /**
     * Serializa uma mensagem completa para array de bytes
     * Este método CONVERTE o objeto Mensagem em bytes na memória
     */
    public static byte[] serializarMensagem(Mensagem mensagem) throws IOException {
        return escreverPayload(out -> {
            try {
                out.writeInt(mensagem.getTipoOperacao().ordinal());
                byte[] payload = mensagem.getPayload();
                if (payload == null) {
                    out.writeInt(0);
                } else {
                    out.writeInt(payload.length);
                    out.write(payload);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Deserializa uma mensagem de array de bytes
     * Este método RECONSTRÓI o objeto Mensagem a partir de bytes
     */ 
    public static Mensagem deserializarMensagem(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));

        int tipoOrdinal = in.readInt();
        Mensagem.TipoOperacao[] valores = Mensagem.TipoOperacao.values();
        if (tipoOrdinal < 0 || tipoOrdinal >= valores.length) {
            throw new IOException("Tipo de operação inválido: " + tipoOrdinal);
        }
        Mensagem.TipoOperacao tipo = valores[tipoOrdinal];
        
        int payloadSize = in.readInt();
        // Validação de segurança: evitar payloads gigantes
        if (payloadSize < 0 || payloadSize > 100_000_000) { // 100MB max
            throw new IOException("Tamanho de payload inválido: " + payloadSize);
        }

        byte[] payload = new byte[payloadSize];
        if (payloadSize > 0) 
            in.readFully(payload);

        return Mensagem.criar(tipo, payload);
    }

    // ============================================================
    //  PAYLOADS USANDO A API GENÉRICA
    // ============================================================
    
    // ----- AUTENTICAÇÃO -----
    /**
     * Serializa payload de autenticação (username + password)
     * Retorna apenas os BYTES do payload, não a mensagem completa
     */
    public static byte[] payloadAutenticacao(String username, String password) throws IOException {
        return escreverPayload(output -> {
            try {
                escreverString(output, username);
                escreverString(output, password);
            } catch (IOException e) { throw new RuntimeException(e); }
        });
    }

    public static String[] lerAutenticacao(byte[] payload) throws IOException {
        return lerPayload(payload, input -> {
            String username = lerString(input);
            String password = lerString(input);
            if (username == null || password == null) {
                throw new IOException("Username ou password null no payload");
            }

            return new String[]{username, password};
        });
    }

    // ----- RESPOSTA -----
    public static byte[] payloadResposta(String mensagem) throws IOException {
        return escreverPayload(output -> {
            try { escreverString(output, mensagem); }
            catch (IOException e) { throw new RuntimeException(e); }
        });
    }

    public static String lerResposta(byte[] payload) throws IOException {
        return lerPayload(payload, Protocolo::lerString);
    }

    // ----- EVENTO -----
    public static byte[] payloadEvento(int produtoID, int quantidade, double preco) throws IOException {
        return escreverPayload(output -> {
            try {
                output.writeInt(produtoID);
                output.writeInt(quantidade);
                output.writeDouble(preco);
            } catch (IOException e) { throw new RuntimeException(e); }
        });
    }

    public static Evento lerEvento(byte[] payload) throws IOException {
        return lerPayload(payload, input -> {
            int produtoID = input.readInt();
            int quantidade = input.readInt();
            double preco = input.readDouble();
            return new Evento(produtoID, quantidade, preco);
        });
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
