package middleware;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Framing:
 *   [len:int][messageBytes...]
 * messageBytes = Message.serialize()
 */
public class Protocolo {
    private static final int MAX_TAMANHO = 10_000_000;

    public void enviar(Message msg, DataOutputStream out) throws IOException {
        byte[] data = msg.serialize();
        if (data.length <= 0 || data.length > MAX_TAMANHO) {
            throw new IOException("Tamanho inválido: " + data.length);
        }
        out.writeInt(data.length);
        out.write(data);
        out.flush();
    }

    public Message receber(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len <= 0 || len > MAX_TAMANHO) {
            throw new IOException("Tamanho inválido: " + len);
        }
        byte[] data = new byte[len];
        in.readFully(data);
        return Message.deserialize(data);
    }
}