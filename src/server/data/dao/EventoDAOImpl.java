package server.data.dao;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import server.business.domain.Agregacao;
import server.business.domain.Evento;

/**
 * Implementação do DAO de eventos com persistência em arquivos
 */
public class EventoDAOImpl implements IEventoDAO {
    private final String pastaBase;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    public EventoDAOImpl(String pastaBase) {
        this.pastaBase = pastaBase;
        new File(pastaBase).mkdirs();
    }
    
    private File ficheiroDia(int dia) {
        return new File(pastaBase, "eventos_dia_" + dia + ".dat");
    }
    
    @Override
    public void salvarEventosDia(int dia, Map<Integer, List<Evento>> eventosPorProduto) {
        lock.writeLock().lock();
        try {
            File file = ficheiroDia(dia);
            try (DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(new FileOutputStream(file)))) {
                
                // ordenar keys para garantir leitura ordenada em agregarEventosDia
                List<Integer> produtos = new ArrayList<>(eventosPorProduto.keySet());
                Collections.sort(produtos);

                out.writeInt(produtos.size()); // número de produtos
                
                for (int produtoID : produtos) {
                    List<Evento> eventos = eventosPorProduto.get(produtoID);
                    
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
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public Map<Integer, List<Evento>> carregarEventosDia(int dia) {
        lock.readLock().lock();
        try {
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
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public Agregacao agregarEventosDia(int produtoID, int dia) {
        lock.readLock().lock();
        try {
            File file = ficheiroDia(dia);
            if (!file.exists()) return null;
            
            Agregacao agregacao = null;
            
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                int numProdutos = raf.readInt();
                
                for (int i = 0; i < numProdutos; i++) {
                    int pid = raf.readInt();
                    int numEventos = raf.readInt();
                    
                    if (pid < produtoID) {
                        raf.skipBytes(numEventos * 12); // int + double = 12 bytes
                    } else if (pid == produtoID) {
                        agregacao = new Agregacao();
                        for (int j = 0; j < numEventos; j++) {
                            int quantidade = raf.readInt();
                            double preco = raf.readDouble();
                            agregacao.update(quantidade, preco);
                        }
                        agregacao.updatePrecoMedio();
                        break;
                    } else {
                        break; // produtos assumidos em ordem crescente
                    }
                }
                
            } catch (IOException e) {
                System.err.println("Erro ao agregar eventos: " + e.getMessage());
            }
            
            return agregacao;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public int obterUltimoDia() {
        lock.readLock().lock();
        try {
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
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public boolean existeDia(int dia) {
        lock.readLock().lock();
        try {
            return ficheiroDia(dia).exists();
        } finally {
            lock.readLock().unlock();
        }
    }
}