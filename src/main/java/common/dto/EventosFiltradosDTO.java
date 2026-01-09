package common.dto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventosFiltradosDTO implements Serializable {

    private Map<Integer, List<EventoCompacto>> eventosPorProduto;
    private int dia;
    private int totalEventos;

    public EventosFiltradosDTO() {}

    public EventosFiltradosDTO(Map<Integer, List<EventoCompacto>> eventos, int dia) {
        this.eventosPorProduto = eventos;
        this.dia = dia;
        this.totalEventos = eventos.values().stream().mapToInt(List::size).sum();
    }

    public byte[] serialize() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);

        out.writeInt(dia);
        out.writeInt(totalEventos);

        if (eventosPorProduto != null) {
            out.writeInt(eventosPorProduto.size());
            for (Map.Entry<Integer, List<EventoCompacto>> entry : eventosPorProduto.entrySet()) {
                out.writeInt(entry.getKey());

                List<EventoCompacto> eventos = entry.getValue();
                out.writeInt(eventos.size());
                for (EventoCompacto evento : eventos) {
                    byte[] eventoBytes = evento.serialize();
                    out.write(eventoBytes); 
                }
            }
        } else {
            out.writeInt(0);
        }

        out.flush();
        return baos.toByteArray();
    }

    public static EventosFiltradosDTO deserialize(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream in = new DataInputStream(bais);

        EventosFiltradosDTO dto = new EventosFiltradosDTO();
        dto.dia = in.readInt();
        dto.totalEventos = in.readInt();

        int numProdutos = in.readInt();
        dto.eventosPorProduto = new HashMap<>();

        for (int i = 0; i < numProdutos; i++) {
            int produtoID = in.readInt();
            int numEventos = in.readInt();

            List<EventoCompacto> eventos = new ArrayList<>();
            for (int j = 0; j < numEventos; j++) {
                byte[] eventoBytes = new byte[12];
                in.readFully(eventoBytes);
                eventos.add(EventoCompacto.deserialize(eventoBytes));
            }

            dto.eventosPorProduto.put(produtoID, eventos);
        }

        return dto;
    }

    public Map<Integer, List<EventoCompacto>> getEventosPorProduto() { return eventosPorProduto; }
    public int getDia() { return dia; }
    public int getTotalEventos() { return totalEventos; }

    @Override
    public String toString() {
        return String.format("EventosFiltrados{dia=%d, produtos=%d, eventos=%d}",
                dia, eventosPorProduto.size(), totalEventos);
    }

    public static class EventoCompacto implements Serializable {
        private static final long serialVersionUID = 1L;

        private int quantidade;
        private double preco;

        public EventoCompacto() {}

        public EventoCompacto(int quantidade, double preco) {
            this.quantidade = quantidade;
            this.preco = preco;
        }

        public byte[] serialize() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);

            out.writeInt(quantidade);
            out.writeDouble(preco);

            out.flush();
            return baos.toByteArray();
        }

        public static EventoCompacto deserialize(byte[] data) throws IOException {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            DataInputStream in = new DataInputStream(bais);

            EventoCompacto evento = new EventoCompacto();
            evento.quantidade = in.readInt();
            evento.preco = in.readDouble();

            return evento;
        }

        public int getQuantidade() { return quantidade; }
        public double getPreco() { return preco; }

        @Override
        public String toString() {
            return String.format("Qtd:%d Preço:%.2f€", quantidade, preco);
        }
    }
}