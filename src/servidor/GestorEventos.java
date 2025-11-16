package src.servidor;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import src.uteis.Evento;

/**
 * Regista, processa e lista eventos de vendas.
 *
 * NOVO MODELO:
 * - Em memória: apenas eventos do dia atual, organizados por produto.
 *   Map<Integer, List<Evento>> eventosDiaAtualPorProduto
 *
 * - Em disco: eventos de dias anteriores, guardados por PersistenciaEventos.
 *
 * CONCORRÊNCIA:
 * - ReadWriteLock:
 *   - writeLock: iniciarNovoDia, adicionarEvento
 *   - readLock: listarEventosDiaAtual, futuras agregações
 */
public class GestorEventos {

    // Lock de leitura/escrita:
    // - readLock: consultas (listarEventosDiaAtual, agregações)
    // - writeLock: alterações (iniciarNovoDia, adicionarEvento)
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = rwLock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = rwLock.writeLock();

    // Dia atual mantido em memória
    private int diaAtual = 0;

    // Eventos do dia atual organizados por produto
    private final Map<Integer, List<Evento>> eventosDiaAtualPorProduto = new HashMap<>();

    // Persistência de eventos em disco
    private final PersistenciaEventos persistenciaEventos;

    public GestorEventos() {
        this.persistenciaEventos = new PersistenciaEventos("dados/eventos");
    }

    /**
     * Avança para um novo dia:
     * - persiste eventos do dia atual em disco
     * - limpa eventos em memória
     * - incrementa diaAtual
     */
    public void iniciarNovoDia() {
        writeLock.lock();
        try {
            // Guardar eventos do dia atual em disco
            try {
                persistenciaEventos.guardarEventosDia(diaAtual, eventosDiaAtualPorProduto);
                System.out.println("Eventos do dia " + diaAtual + " guardados em disco.");
            } catch (IOException e) {
                System.err.println("Erro ao guardar eventos do dia " + diaAtual + ": " + e.getMessage());
                // Dependendo do enunciado, podias aqui decidir não avançar o dia
                // mas para simplificar, avançamos mesmo assim.
            }

            // Limpar memória e avançar dia
            eventosDiaAtualPorProduto.clear();
            diaAtual++;
            System.out.println("Novo dia iniciado: " + diaAtual);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Adiciona evento ao dia atual, em memória.
     */
    public void adicionarEvento(int produtoID, int quantidade, double preco) {
        writeLock.lock();
        try {
            Evento e = new Evento(produtoID, quantidade, preco);
            eventosDiaAtualPorProduto
                .computeIfAbsent(produtoID, k -> new ArrayList<>())
                .add(e);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Lista apenas os eventos do dia atual, organizados por produto.
     * (Eventos de dias anteriores ficam em disco.)
     */
    public String listarEventosDiaAtual() {
        readLock.lock();
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("=== EVENTOS DO DIA ").append(diaAtual).append(" ===\n\n");

            if (eventosDiaAtualPorProduto.isEmpty()) {
                sb.append("(sem eventos)\n");
                return sb.toString();
            }

            for (Map.Entry<Integer, List<Evento>> entry : eventosDiaAtualPorProduto.entrySet()) {
                int produtoID = entry.getKey();
                List<Evento> eventos = entry.getValue();

                sb.append("Produto ").append(produtoID)
                  .append(" (").append(eventos.size()).append(" eventos):\n");

                for (int i = 0; i < eventos.size(); i++) {
                    Evento e = eventos.get(i);
                    sb.append(String.format(
                        "  %d. Qtd: %d - Preço: %.2f€ (Volume: %.2f€)\n",
                        i + 1,
                        e.getQuantidade(),
                        e.getPreco(),
                        e.getVolume()
                    ));
                }
                sb.append("\n");
            }

            return sb.toString();
        } finally {
            readLock.unlock();
        }
    }

    // No futuro aqui entram métodos de agregação (quantidadeTotal, volumeTotal, etc.),
    // todos com readLock, usando eventosDiaAtualPorProduto.
}