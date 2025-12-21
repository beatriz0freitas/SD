package server.data;

import java.io.*;
import java.util.*;

import server.business.domain.Agregacao;
import server.business.domain.Evento;

/**
 * Implementação do DAO de eventos com persistência em arquivos
 */
public class EventoDAOImpl implements IEventoDAO {
    private final String pastaBase;
    
    public EventoDAOImpl(String pastaBase) {
        this.pastaBase = pastaBase;
        new File(pastaBase).mkdirs();
    }
    
    private File ficheiroDia(int dia) {
        return new File(pastaBase, "eventos_dia_" + dia + ".dat");
    }
    
    @Override
    public void salvarEventosDia(int dia, Map<Integer, List<Evento>> eventosPorProduto) {
        File file = ficheiroDia(dia);
        
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(file)))) {
            
            out.writeInt(eventosPorProduto.size()); // número de produtos
            
            for (Map.Entry<Integer, List<Evento>> entry : eventosPorProduto.entrySet()) {
                int produtoID = entry.getKey();
                List<Evento> eventos = entry.getValue();
                
                out.writeInt(produtoID);
                out.writeInt(eventos.size());
                
                for (Evento e : eventos) {
                    out.writeInt(e.getQuantidade());
                    out.writeDouble(e.getPreco());
                }
            }
            out.flush();
            
        } catch (IOException e) {
            System.err.println("Erro ao salvar eventos do dia " + dia + ": " + e.getMessage());
        }
    }
    
    @Override
    public Map<Integer, List<Evento>> carregarEventosDia(int dia) {
        Map<Integer, List<Evento>> resultado = new HashMap<>();
        File file = ficheiroDia(dia);
        
        if (!file.exists()) return resultado;
        
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {
            
            int numProdutos = in.readInt();
            
            for (int i = 0; i < numProdutos; i++) {
                int produtoID = in.readInt();
                int numEventos = in.readInt();
                
                List<Evento> eventos = new ArrayList<>();
                for (int j = 0; j < numEventos; j++) {
                    int quantidade = in.readInt();
                    double preco = in.readDouble();
                    eventos.add(new Evento(produtoID, quantidade, preco));
                }
                
                resultado.put(produtoID, eventos);
            }
            
        } catch (IOException e) {
            System.err.println("Erro ao carregar eventos do dia " + dia + ": " + e.getMessage());
        }
        
        return resultado;
    }
    
    @Override
    public Agregacao agregarEventosDia(int produtoID, int dia) {
        File file = ficheiroDia(dia);
        if (!file.exists()) return null;
        
        Agregacao agregacao = null;
        
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            int numProdutos = raf.readInt();
            
            for (int i = 0; i < numProdutos; i++) {
                int pid = raf.readInt();
                int numEventos = raf.readInt();
                
                if (pid < produtoID) {
                    // Pular eventos deste produto
                    raf.skipBytes(numEventos * 12); // int + double = 12 bytes
                    
                } else if (pid == produtoID) {
                    // Produto encontrado - agregar
                    agregacao = new Agregacao();
                    for (int j = 0; j < numEventos; j++) {
                        int quantidade = raf.readInt();
                        double preco = raf.readDouble();
                        agregacao.update(quantidade, preco);
                    }
                    agregacao.updatePrecoMedio();
                    break;
                    
                } else {
                    // Produto não existe (assumindo ordem crescente)
                    break;
                }
            }
            
        } catch (IOException e) {
            System.err.println("Erro ao agregar eventos: " + e.getMessage());
        }
        
        return agregacao;
    }
    
    @Override
    public int obterUltimoDia() {
        File dir = new File(pastaBase);
        if (!dir.isDirectory()) return -1;
        
        File[] files = dir.listFiles((d, name) -> 
            name.startsWith("eventos_dia_") && name.endsWith(".dat"));
        
        if (files == null || files.length == 0) return -1;
        
        int maxDia = -1;
        for (File f : files) {
            try {
                String nome = f.getName();
                String diaStr = nome.substring(12, nome.length() - 4); // "eventos_dia_".length = 12
                int dia = Integer.parseInt(diaStr);
                if (dia > maxDia) maxDia = dia;
            } catch (Exception e) {
                // Ignorar arquivos com nome inválido
            }
        }
        
        return maxDia;
    }
    
    @Override
    public boolean existeDia(int dia) {
        return ficheiroDia(dia).exists();
    }
}