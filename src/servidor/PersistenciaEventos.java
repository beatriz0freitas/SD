package src.servidor;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import src.uteis.Evento;

/**
 * Responsável por ler/escrever ficheiros de eventos do disco.
 *
 * Formato simples:
 * ficheiro por dia:  <pastaBase>/eventos_dia_<dia>.dat
 *
 * Estrutura do ficheiro:
 *   [numProdutos:int]
 *   repetido numProdutos vezes:
 *     [produtoID:int]
 *     [numEventos:int]
 *     repetido numEventos vezes:
 *       [quantidade:int]
 *       [preco:double]
 */
public class PersistenciaEventos {

    private final String pastaBase;

    public PersistenciaEventos(String pastaBase) {
        this.pastaBase = pastaBase;
        File dir = new File(pastaBase);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private File ficheiroDia(int dia) {
        return new File(pastaBase, "eventos_dia_" + dia + ".dat");
    }

    /**
     * Guarda todos os eventos de um dia em disco.
     * Sobrescreve o ficheiro do dia, se já existir.
     */
    public void guardarEventosDia(int dia, Map<Integer, List<Evento>> eventosPorProduto) throws IOException {
        File ficheiro = ficheiroDia(dia);

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(ficheiro)))) {

            // número de produtos
            out.writeInt(eventosPorProduto.size());

            for (Map.Entry<Integer, List<Evento>> entry : eventosPorProduto.entrySet()) {
                int produtoID = entry.getKey();
                List<Evento> lista = entry.getValue();

                out.writeInt(produtoID);
                out.writeInt(lista.size()); // número de eventos deste produto

                for (Evento e : lista) {
                    out.writeInt(e.getQuantidade());
                    out.writeDouble(e.getPreco());
                }
            }
            out.flush();
        }
    }

    /**
     * Carrega todos os eventos de um dia a partir do disco.
     * Se o ficheiro não existir, devolve mapa vazio.
     */
    public Map<Integer, List<Evento>> carregarEventosDia(int dia) throws IOException {
        File ficheiro = ficheiroDia(dia);
        Map<Integer, List<Evento>> eventosPorProduto = new HashMap<>();

        if (!ficheiro.exists()) {
            return eventosPorProduto; // dia sem ficheiro → sem eventos
        }

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(ficheiro)))) {

            int numProdutos = in.readInt();

            for (int i = 0; i < numProdutos; i++) {
                int produtoID = in.readInt();
                int numEventos = in.readInt();

                List<Evento> lista = new ArrayList<>(numEventos);
                for (int j = 0; j < numEventos; j++) {
                    int quantidade = in.readInt();
                    double preco = in.readDouble();
                    lista.add(new Evento(produtoID, quantidade, preco));
                }
                eventosPorProduto.put(produtoID, lista);
            }
        }

        return eventosPorProduto;
    }

    /**
     * Determina o maior número de dia presente nos ficheiros de eventos em disco.
     * Retorna -1 se não existir nenhum ficheiro de eventos.
     */
    public int obterUltimoDia() {
        File dir = new File(pastaBase);
        if (!dir.isDirectory()) {
            return -1;
        }
    
        File[] ficheiros = dir.listFiles((d, name) ->
            name.startsWith("eventos_dia_") && name.endsWith(".dat")
        );
    
        if (ficheiros == null || ficheiros.length == 0) {
            return -1;
        }
    
        int maxDia = -1;
        final String prefix = "eventos_dia_";
        final String suffix = ".dat";
    
        for (File f : ficheiros) {
            String nome = f.getName();
            try {
                String diaStr = nome.substring(prefix.length(), nome.length() - suffix.length());
                int dia = Integer.parseInt(diaStr);
                if (dia > maxDia) {
                    maxDia = dia;
                }
            } catch (RuntimeException e) { // NumberFormatException + StringIndexOutOfBoundsException
                System.err.println("Ficheiro com formato inválido ignorado: " + nome);
            }
        }
    
        return maxDia;
    }

    /**
     * Agrega os dados de um dia na estrutura de Agregacao
     * Retorna null se nao existir no ficheiro
     */
    public Agregacao agregarEventosDia(int produto, int dia) throws IOException {
        File ficheiro = ficheiroDia(dia);
        Agregacao agregacao = null;

        if (!ficheiro.exists()) {
            return null; // dia sem ficheiro → sem eventos
        }
        
        try (RandomAccessFile raf = new RandomAccessFile(ficheiro, "r")){
            int numProdutos = raf.readInt();
                
            for(int i = 0; i < numProdutos; i++){
                int produtoID  = raf.readInt();
                int numEventos = raf.readInt();

                if(produtoID < produto){
                    raf.skipBytes(numEventos * 12);
                } else if (produtoID == produto) {
                    agregacao = new Agregacao();

                    for (int j = 0; j < numEventos; j++) {
                        int quantidade = raf.readInt();
                        double preco = raf.readDouble();
                        agregacao.update(quantidade, preco);
                    }
                    agregacao.updatePrecoMedio();
                    break; // interrompe a leitura do ficheiro
                } else { // se produto nao existe no ficheiro (assumindo ordem crescente no ficheiro)
                    break;
                }
            }
        }
        return agregacao;
    }
}