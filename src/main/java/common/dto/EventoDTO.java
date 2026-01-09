package common.dto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;

public class EventoDTO implements Serializable {

    private int produtoID;
    private int quantidade;
    private double preco;
    private int dia;

    public EventoDTO() {}

    public EventoDTO(int produtoID, int quantidade, double preco) {
        this.produtoID = produtoID;
        this.quantidade = quantidade;
        this.preco = preco;
    }

    public byte[] serialize() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);

        out.writeInt(produtoID);
        out.writeInt(quantidade);
        out.writeDouble(preco);
        out.writeInt(dia);

        out.flush();
        return baos.toByteArray();
    }

    public static EventoDTO deserialize(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream in = new DataInputStream(bais);

        EventoDTO dto = new EventoDTO();
        dto.produtoID = in.readInt();
        dto.quantidade = in.readInt();
        dto.preco = in.readDouble();
        dto.dia = in.readInt();

        return dto;
    }

    public int getProdutoID() { return produtoID; }
    public void setProdutoID(int produtoID) { this.produtoID = produtoID; }
    public int getQuantidade() { return quantidade; }
    public void setQuantidade(int quantidade) { this.quantidade = quantidade; }
    public double getPreco() { return preco; }
    public void setPreco(double preco) { this.preco = preco; }
    public int getDia() { return dia; }
    public void setDia(int dia) { this.dia = dia; }
    public double getVolume() { return quantidade * preco; }

    @Override
    public String toString() {
        return String.format("EventoDTO{produto=%d, qtd=%d, preco=%.2f}",
                produtoID, quantidade, preco);
    }
}