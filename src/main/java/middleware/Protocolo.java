package middleware;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;


/**
 * Protocolo de comunicação entre cliente e servidor.
 * Serializa/deserializa mensagens com validação de tamanho.
 * 
 * Formato na rede: [tamanho:4bytes][dados:Nbytes]
 * Garante robustez contra corrupção de dados e ataques.
 */
public class Protocolo {
    private static final int MAX_TAMANHO = 10_000_000;  // Limite de proteção

    /**
     * Envia mensagem ao receptor
     * Formato: [int length][byte[] data]
     * @param msg Mensagem a enviar
     * @param out Stream de saída
     * @throws IOException se tamanho inválido
     */
    public void enviar(Message msg, DataOutputStream out) throws IOException {
        byte[] data = msg.serialize();
        if (data.length <= 0 || data.length > MAX_TAMANHO) {
            throw new IOException("Tamanho inválido: " + data.length);
        }
        out.writeInt(data.length);
        out.write(data);
        out.flush();  // Crucial para garantir envio
    }

    /**
     * Recebe mensagem do remetente (bloqueia até chegar completa)
     * @param in Stream de entrada
     * @return Mensagem desserializada
     * @throws IOException se tamanho inválido
     */
    public Message receber(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len <= 0 || len > MAX_TAMANHO) {
            throw new IOException("Tamanho inválido: " + len);
        }
        byte[] data = new byte[len];
        in.readFully(data);  // Bloqueia até ler todos os bytes
        return Message.deserialize(data);
    }
}