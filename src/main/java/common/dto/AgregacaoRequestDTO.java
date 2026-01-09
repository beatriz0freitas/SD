package common.dto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;

public class AgregacaoRequestDTO implements Serializable {

    private int produtoID;
    private int dias;

    public AgregacaoRequestDTO() {}

    public AgregacaoRequestDTO(int produtoID, int dias) {
        this.produtoID = produtoID;
        this.dias = dias;
    }

    public byte[] serialize() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);

        out.writeInt(produtoID);
        out.writeInt(dias);

        out.flush();
        return baos.toByteArray();
    }

    public static AgregacaoRequestDTO deserialize(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream in = new DataInputStream(bais);

        AgregacaoRequestDTO dto = new AgregacaoRequestDTO();
        dto.produtoID = in.readInt();
        dto.dias = in.readInt();

        return dto;
    }

    public int getProdutoID() { return produtoID; }
    public void setProdutoID(int produtoID) { this.produtoID = produtoID; }
    public int getDias() { return dias; }
    public void setDias(int dias) { this.dias = dias; }
}