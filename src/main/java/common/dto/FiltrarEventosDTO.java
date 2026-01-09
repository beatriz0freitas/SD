package common.dto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

class FiltrarEventosDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Set<Integer> produtosIDs;
    private int diaAnterior;
    
    public FiltrarEventosDTO() {}
    
    public FiltrarEventosDTO(Set<Integer> produtosIDs, int diaAnterior) {
        this.produtosIDs = produtosIDs;
        this.diaAnterior = diaAnterior;
    }
    
    public byte[] serialize() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        
        out.writeInt(diaAnterior);
        
        if (produtosIDs != null) {
            out.writeInt(produtosIDs.size());
            for (Integer id : produtosIDs) {
                out.writeInt(id);
            }
        } else {
            out.writeInt(0);
        }
        
        out.flush();
        return baos.toByteArray();
    }
    
    public static FiltrarEventosDTO deserialize(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream in = new DataInputStream(bais);
        
        FiltrarEventosDTO dto = new FiltrarEventosDTO();
        dto.diaAnterior = in.readInt();
        
        int tamanho = in.readInt();
        dto.produtosIDs = new HashSet<>();
        for (int i = 0; i < tamanho; i++) {
            dto.produtosIDs.add(in.readInt());
        }
        
        return dto;
    }
    
    public Set<Integer> getProdutosIDs() { return produtosIDs; }
    public void setProdutosIDs(Set<Integer> produtosIDs) { this.produtosIDs = produtosIDs; }
    public int getDiaAnterior() { return diaAnterior; }
    public void setDiaAnterior(int diaAnterior) { this.diaAnterior = diaAnterior; }
}