package server.data.repository;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import common.ErrorLogger;
import server.business.domain.Agregacao;
import server.business.domain.Evento;


public class EventoFileRepository implements IEventoRepository {
    private final String pastaBase;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();
    
    public EventoFileRepository(String pastaBase) {
        this.pastaBase = pastaBase;
        new File(pastaBase).mkdirs();
    }
    
    private File ficheiroDia(int dia) {
        return new File(pastaBase, "eventos_dia_" + dia + ".dat");
    }
    
    @Override
    public void salvarEventosDia(int dia, Map<Integer, List<Evento>> eventosPorProduto) {
        writeLock.lock();
        try {
            File ficheiro = ficheiroDia(dia);
            
            
            List<Integer> produtosOrdenados = new ArrayList<>(eventosPorProduto.keySet());
            Collections.sort(produtosOrdenados);
            
            
            try (DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(new FileOutputStream(ficheiro)))) {
                
                
                out.writeInt(produtosOrdenados.size());
                
                
                for (int produtoID : produtosOrdenados) {
                    List<Evento> eventos = eventosPorProduto.get(produtoID);
                    
                    out.writeInt(produtoID);
                    out.writeInt(eventos.size());
                    
                    
                    for (Evento e : eventos) {
                        out.writeInt(e.getQuantidade());
                        out.writeDouble(e.getPreco());
                    }
                }
                out.flush();
                
                System.out.println("Dia " + dia + " salvo: " + produtosOrdenados.size() + " produtos");
                
            } catch (IOException e) {
                ErrorLogger.getInstance().logError(
                    "EventoFileRepository.carregarEventosDia[dia=" + dia + "]", e);
            }
        } finally {
            writeLock.unlock();
        }
    }
    
    @Override
    public Map<Integer, List<Evento>> carregarEventosDia(int dia) {
        readLock.lock();
        try {
            Map<Integer, List<Evento>> resultado = new HashMap<>();
            File ficheiro = ficheiroDia(dia);
            
            if (!ficheiro.exists()) {
                return resultado;
            }
            
            try (DataInputStream in = new DataInputStream(
                    new BufferedInputStream(new FileInputStream(ficheiro)))) {
                
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
                
                System.out.println("Dia " + dia + " carregado: " + numProdutos + " produtos");
                
            } catch (IOException e) {
                System.err.println("Erro ao carregar dia " + dia + ": " + e.getMessage());
            }
            
            return resultado;
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public Agregacao agregarEventosDia(int produtoID, int dia) {
        readLock.lock();
        try {
            File ficheiro = ficheiroDia(dia);
            
            if (!ficheiro.exists()) {
                return new Agregacao(); 
            }
            
            
            try (RandomAccessFile raf = new RandomAccessFile(ficheiro, "r")) {
                int numProdutos = raf.readInt();
                
                for (int i = 0; i < numProdutos; i++) {
                    int pid = raf.readInt();
                    int numEventos = raf.readInt();
                    
                    if (pid == produtoID) {
                        
                        Agregacao agregacao = new Agregacao();
                        for (int j = 0; j < numEventos; j++) {
                            int quantidade = raf.readInt();
                            double preco = raf.readDouble();
                            agregacao.update(quantidade, preco);
                        }
                        agregacao.updatePrecoMedio();
                        return agregacao;
                        
                    } else if (pid < produtoID) {
                        
                        raf.skipBytes(numEventos * 12);
                        
                    } else {
                        
                        break;
                    }
                }
                
            } catch (IOException e) {
                ErrorLogger.getInstance().logError(
                    "EventoFileRepository.agregarEventosDia[produto=" + produtoID + ", dia=" + dia + "]", e);
            }
            
            
            return new Agregacao();
            
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public int obterUltimoDia() {
        readLock.lock();
        try {
            File dir = new File(pastaBase);
            if (!dir.isDirectory()) {
                return -1;
            }
            
            File[] ficheiros = dir.listFiles((d, nome) ->
                nome.startsWith("eventos_dia_") && nome.endsWith(".dat")
            );
            
            if (ficheiros == null || ficheiros.length == 0) {
                return -1;
            }
            
            
            int maxDia = -1;
            for (File f : ficheiros) {
                try {
                    String nome = f.getName();
                    
                    String diaStr = nome.substring(12, nome.length() - 4);
                    int dia = Integer.parseInt(diaStr);
                    if (dia > maxDia) {
                        maxDia = dia;
                    }
                } catch (Exception e) {
                    
                }
            }
            
            return maxDia;
            
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public boolean existeDia(int dia) {
        readLock.lock();
        try {
            return ficheiroDia(dia).exists();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Agregacao agregarEventosMultiDia(int produtoID, int diaInicio, int diaFim) {
        Agregacao resultado = new Agregacao();

        readLock.lock();
        try {
            for (int dia = diaInicio; dia <= diaFim; dia++) {
                File ficheiro = ficheiroDia(dia);
                if (!ficheiro.exists()) {
                    continue;
                }

                try (RandomAccessFile raf = new RandomAccessFile(ficheiro, "r")) {
                    int numProdutos = raf.readInt();

                    for (int i = 0; i < numProdutos; i++) {
                        int pid = raf.readInt();
                        int numEventos = raf.readInt();

                        if (pid == produtoID) {
                            for (int j = 0; j < numEventos; j++) {
                                int quantidade = raf.readInt();
                                double preco = raf.readDouble();
                                resultado.update(quantidade, preco);
                            }
                            break;
                        } else {
                            raf.skipBytes(numEventos * 12);
                        }
                    }
                } catch (IOException e) {
                    ErrorLogger.getInstance().logError(
                        "EventoFileRepository.agregarEventosMultiDia[produto=" + produtoID + ", dias=" + diaInicio + "-" + diaFim + ", dia=" + dia + "]", 
                        e
                    );
                }
            }
            resultado.updatePrecoMedio();
            return resultado;

        } finally {
            readLock.unlock();
        }
    }

}