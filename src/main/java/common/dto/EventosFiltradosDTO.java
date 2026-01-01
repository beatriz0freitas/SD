package common.dto;

import java.io.Serializable;
import java.util.List;

/**
 * DTO para resposta de eventos filtrados
 * 
 * Exemplo:
 * Sem compactação (repetição): 
 *   [{prodID:1,qtd:5,preco:20}, {prodID:1,qtd:3,preco:22}, {prodID:2,qtd:2,preco:50}]
 *   
 * Com compactação (agrupamento):
 *   {1: [{qtd:5,preco:20}, {qtd:3,preco:22}], 2: [{qtd:2,preco:50}]}
 */
public class EventosFiltradosDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // Map: produtoID -> lista de eventos desse produto - Evita repetir produtoID em cada evento
    private java.util.Map<Integer, List<EventoCompacto>> eventosPorProduto;
    private int dia;
    private int totalEventos;
    
    public EventosFiltradosDTO() {}
    
    public EventosFiltradosDTO(java.util.Map<Integer, List<EventoCompacto>> eventos, int dia) {
        this.eventosPorProduto = eventos;
        this.dia = dia;
        this.totalEventos = eventos.values().stream()
            .mapToInt(List::size)
            .sum();
    }
    
    public java.util.Map<Integer, List<EventoCompacto>> getEventosPorProduto() {
        return eventosPorProduto;
    }
    
    public int getDia() {
        return dia;
    }
    
    public int getTotalEventos() {
        return totalEventos;
    }
    
    /**
     * Evento compacto: apenas quantidade e preço
     * O produtoID está na chave do Map pai
     */
    public static class EventoCompacto implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private int quantidade;
        private double preco;
        
        public EventoCompacto() {}
        
        public EventoCompacto(int quantidade, double preco) {
            this.quantidade = quantidade;
            this.preco = preco;
        }
        
        public int getQuantidade() {
            return quantidade;
        }
        
        public double getPreco() {
            return preco;
        }
        
        @Override
        public String toString() {
            return String.format("Qtd:%d Preço:%.2f€", quantidade, preco);
        }
    }
    
    @Override
    public String toString() {
        return String.format("EventosFiltrados{dia=%d, produtos=%d, eventos=%d}",
            dia, eventosPorProduto.size(), totalEventos);
    }
}