package middleware.proto;

import java.io.*;

/**
 * Handler do protocolo de comunicação
 * Formato: [tamanho:int][dados:bytes]
 */
public class ProtocoloHandler {
    private final MessageSerializer serializer;

    public ProtocoloHandler() {
        this.serializer = new MessageSerializer();
    }

    /** Envia objeto pelo stream */
    public void enviar(Object obj, DataOutputStream out) throws IOException {
        byte[] dados = serializer.serialize(obj); // 1. Serializar objeto
        out.writeInt(dados.length);               // 2. Enviar tamanho
        out.write(dados);                         // 3. Enviar dados
        out.flush();
    }

    /** Recebe objeto do stream (retorna Object; cast no chamador) */
    public Object receber(DataInputStream in) throws IOException, ClassNotFoundException {
        int tamanho = in.readInt();
        if (tamanho <= 0 || tamanho > 10_000_000) {
            throw new IOException("Tamanho inválido: " + tamanho);
        }
        byte[] dados = new byte[tamanho];
        in.readFully(dados);
        return serializer.deserialize(dados);
    }
}