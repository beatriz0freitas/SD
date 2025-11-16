package src.servidor;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import src.uteis.Evento;

public class GestorEventos {

    // Lock de leitura/escrita:
    // - readLock: consultas (listarEventos, agregações)
    // - writeLock: alterações (iniciarNovoDia, adicionarEvento)
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = rwLock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = rwLock.writeLock();

    private int diaAtual = 0;
    private final Map<Integer, List<Evento>> eventosPorDia = new HashMap<>();

    public GestorEventos() {
        eventosPorDia.put(diaAtual, new ArrayList<>());
    }

    // --------- ESCRITAS (writeLock) ---------

    public void iniciarNovoDia() {
        writeLock.lock();
        try {
            diaAtual++;
            eventosPorDia.put(diaAtual, new ArrayList<>());
            // Aqui tambem podemos invalidar a cache depois (ler todo abaixo)
        } finally {
            writeLock.unlock();
        }
    }

    public void adicionarEvento(int produtoID, int quantidade, double preco) {
        writeLock.lock();
        try {
            Evento e = new Evento(produtoID, quantidade, preco);
            eventosPorDia.get(diaAtual).add(e);
            // TODO para quando tivermos cache:invalidar cache para o diaAtual (ou seja como houve alteracoes nos eventos, as agregacoes que estao na cache ficam desatualizadas)
        } finally {
            writeLock.unlock();
        }
    }

    // --------- LEITURAS (readLock) ---------

    public String listarEventos() {
        readLock.lock();
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("=== EVENTOS POR DIA ===\n");
            sb.append("Dia atual: ").append(diaAtual).append("\n\n");

            for (Map.Entry<Integer, List<Evento>> entry : eventosPorDia.entrySet()) {
                int dia = entry.getKey();
                List<Evento> eventos = entry.getValue();

                sb.append("DIA ").append(dia)
                  .append(" (").append(eventos.size()).append(" eventos):\n");

                if (eventos.isEmpty()) {
                    sb.append("  (sem eventos)\n");
                } else {
                    for (int i = 0; i < eventos.size(); i++) {
                        Evento e = eventos.get(i);
                        sb.append(String.format(
                            "  %d. Produto %d - Qtd: %d - Preço: %.2f€ (Volume: %.2f€)\n",
                            i + 1,
                            e.getProdutoID(),
                            e.getQuantidade(),
                            e.getPreco(),
                            e.getVolume()
                        ));
                    }
                }
                sb.append("\n");
            }

            return sb.toString();
        } finally {
            readLock.unlock();
        }
    }

    // adicionar métodos de agregação,
    // TODO com readLock: quantidadeTotal, volumeTotal, precoMedio, etc.
}