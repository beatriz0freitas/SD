//Regista, processa e dispara eventos do sistema (notificações, ações internas).

        // public void inserirEvento(Evento e)
        //         public int quantidadeTotal();
        // public double volumeTotal();
        // public double precoMedio();
        // public double precoMaximo();
        // public List<Evento> filtrarPorProdutos(Set<Integer> produtos)


package src.servidor;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import src.uteis.Evento;

public class GestorEventos {

    private final ReentrantLock lock = new ReentrantLock();
    private int diaAtual = 0;
    private Map<Integer, List<Evento>> eventosPorDia = new HashMap<>();

    public GestorEventos() {
        eventosPorDia.put(diaAtual, new ArrayList<>());
    }

    public void iniciarNovoDia() {
        lock.lock();
        try {
            diaAtual++;
            eventosPorDia.put(diaAtual, new ArrayList<>());
        } finally {
            lock.unlock();
        }
    }

    public void adicionarEvento(int produtoID, int quantidade, double preco) {
        lock.lock();
        try {
            Evento e = new Evento(produtoID, quantidade, preco);
            eventosPorDia.get(diaAtual).add(e);
        } finally {
            lock.unlock();
        }
    }


    public String listarEventos() {
        lock.lock();
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("=== EVENTOS POR DIA ===\n");
            sb.append("Dia atual: ").append(diaAtual).append("\n\n");
            
            for (Map.Entry<Integer, List<Evento>> entry : eventosPorDia.entrySet()) {
                int dia = entry.getKey();
                List<Evento> eventos = entry.getValue();
                
                sb.append("DIA ").append(dia).append(" (").append(eventos.size()).append(" eventos):\n");
                
                if (eventos.isEmpty()) {
                    sb.append("  (sem eventos)\n");
                } else {
                    for (int i = 0; i < eventos.size(); i++) {
                        Evento e = eventos.get(i);
                        sb.append(String.format("  %d. Produto %d - Qtd: %d - Preço: %.2f€ (Volume: %.2f€)\n",
                            i + 1, e.getProdutoID(), e.getQuantidade(), e.getPreco(), e.getVolume()));
                    }
                }
                sb.append("\n");
            }
            
            return sb.toString();
        } finally {
            lock.unlock();
        }
    }
}
