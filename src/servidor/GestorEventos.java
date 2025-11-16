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

    public void novoDia() {
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
}
