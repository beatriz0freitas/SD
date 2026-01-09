package common.dto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;


// ============= AgregacaoDTO =============
class AgregacaoDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private int produtoID;
    private int dias;
    private int quantidadeVendas;
    private double volumeVendas;
    private double precoMedio;
    private double precoMaximo;
    
    public AgregacaoDTO() {}
    
    public AgregacaoDTO(int produtoID, int dias) {
        this.produtoID = produtoID;
        this.dias = dias;
    }
    
    public byte[] serialize() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        
        out.writeInt(produtoID);
        out.writeInt(dias);
        out.writeInt(quantidadeVendas);
        out.writeDouble(volumeVendas);
        out.writeDouble(precoMedio);
        out.writeDouble(precoMaximo);
        
        out.flush();
        return baos.toByteArray();
    }
    
    public static AgregacaoDTO deserialize(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream in = new DataInputStream(bais);
        
        AgregacaoDTO dto = new AgregacaoDTO();
        dto.produtoID = in.readInt();
        dto.dias = in.readInt();
        dto.quantidadeVendas = in.readInt();
        dto.volumeVendas = in.readDouble();
        dto.precoMedio = in.readDouble();
        dto.precoMaximo = in.readDouble();
        
        return dto;
    }
    
    public int getProdutoID() { return produtoID; }
    public void setProdutoID(int produtoID) { this.produtoID = produtoID; }
    public int getDias() { return dias; }
    public void setDias(int dias) { this.dias = dias; }
    public int getQuantidadeVendas() { return quantidadeVendas; }
    public void setQuantidadeVendas(int quantidadeVendas) { this.quantidadeVendas = quantidadeVendas; }
    public double getVolumeVendas() { return volumeVendas; }
    public void setVolumeVendas(double volumeVendas) { this.volumeVendas = volumeVendas; }
    public double getPrecoMedio() { return precoMedio; }
    public void setPrecoMedio(double precoMedio) { this.precoMedio = precoMedio; }
    public double getPrecoMaximo() { return precoMaximo; }
    public void setPrecoMaximo(double precoMaximo) { this.precoMaximo = precoMaximo; }
    
    @Override
    public String toString() {
        return String.format(
            "AgregacaoDTO{produto=%d, dias=%d, qtd=%d, volume=%.2f, medio=%.2f, max=%.2f}",
            produtoID, dias, quantidadeVendas, volumeVendas, precoMedio, precoMaximo
        );
    }
}