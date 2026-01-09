package common.dto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;

public class NotificacaoDTO implements Serializable {

    private int arg1;
    private int arg2;

    public NotificacaoDTO() {}

    public NotificacaoDTO(int arg1, int arg2) {
        this.arg1 = arg1;
        this.arg2 = arg2;
    }

    public byte[] serialize() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);

        out.writeInt(arg1);
        out.writeInt(arg2);

        out.flush();
        return baos.toByteArray();
    }

    public static NotificacaoDTO deserialize(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream in = new DataInputStream(bais);

        NotificacaoDTO dto = new NotificacaoDTO();
        dto.arg1 = in.readInt();
        dto.arg2 = in.readInt();

        return dto;
    }

    public int getArg1() { return arg1; }
    public int getArg2() { return arg2; }
}