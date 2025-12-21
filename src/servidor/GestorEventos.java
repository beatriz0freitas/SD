package src.servidor;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
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
 * - Cache: agregações calculadas para consultas rápidas.
 * 
 * INICIALIZAÇÃO:
 * - O diaAtual é inicializado com o valor do último dia persistido em disco + 1.
 * - Se não existirem eventos em disco, diaAtual começa em 0.
 * - Isto permite retomar o estado correto após reinicialização do sistema.
 *
 * CONCORRÊNCIA:
 * - ReadWriteLock (Capítulo 4 da sebenta):
 *   - writeLock: iniciarNovoDia, adicionarEvento
 *   - readLock: listarEventosDiaAtual, consultas de agregação
 * - Permite múltiplas leituras concorrentes
 * - Escritas são exclusivas
 */
public class GestorEventos {

    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    private final int D;  // Dias anteriores a considerar
    private final int S;  // Máximo de séries em memória

    // Dia atual mantido em memória
    private volatile int diaAtual = 0;

    // Eventos do dia atual organizados por produto
    // Protegido por rwLock (write ao adicionar, read ao listar)
    private final Map<Integer, List<Evento>> eventosDiaAtualPorProduto = new HashMap<>();

    // Cache de Agregações (já tem seu próprio ReadWriteLock interno)
    private final CacheAgregacoes cache;

    // Persistência de eventos em disco
    private final PersistenciaEventos persistenciaEventos;

    public GestorEventos(int D, int S) {
        if (D <= 0 || S <= 0) {
            throw new IllegalArgumentException("D e S devem ser positivos");
        }
        if (S >= D) {
            throw new IllegalArgumentException("S deve ser menor que D");
        }
        
        this.D = D;
        this.S = S;
        this.persistenciaEventos = new PersistenciaEventos("dados/eventos");
        int ultimoDia = persistenciaEventos.obterUltimoDia();
        this.diaAtual = (ultimoDia >= 0) ? ultimoDia + 1 : 0;
        this.cache = new CacheAgregacoes(persistenciaEventos, D);
        
        System.out.println("GestorEventos inicializado:");
        System.out.println("  - D (dias histórico): " + D);
        System.out.println("  - S (séries em memória): " + S);
        System.out.println("  - Dia atual: " + diaAtual);
    }

    /**
     * Avança para um novo dia:
     * - persiste eventos do dia atual em disco
     * - limpa eventos em memória
     * - incrementa diaAtual
     * - limpa cache de agregações antigas
     * 
     * USA writeLock: operação exclusiva, modifica estado
     */
    public void iniciarNovoDia() {
        writeLock.lock();
        try {
            System.out.println("\n=== INICIANDO NOVO DIA ===");
            System.out.println("Dia atual: " + diaAtual + " -> " + (diaAtual + 1));
            
            // Guardar eventos do dia atual em disco
            if (!eventosDiaAtualPorProduto.isEmpty()) {
                try {
                    persistenciaEventos.guardarEventosDia(diaAtual, eventosDiaAtualPorProduto);
                    System.out.println("✓ Eventos do dia " + diaAtual + " guardados em disco");
                    System.out.println("  Total de produtos: " + eventosDiaAtualPorProduto.size());
                    
                    int totalEventos = eventosDiaAtualPorProduto.values().stream()
                        .mapToInt(List::size)
                        .sum();
                    System.out.println("  Total de eventos: " + totalEventos);
                    
                } catch (IOException e) {
                    System.err.println("✗ ERRO ao guardar eventos do dia " + diaAtual + ": " + e.getMessage());
                    // DECISÃO: continuar mesmo com erro (log para análise posterior)
                    // Alternativa: lançar exceção e abortar transição de dia
                    //todo: decidir mais sensata
                }
            } else {
                System.out.println("⚠ Dia " + diaAtual + " sem eventos para guardar");
            }

            eventosDiaAtualPorProduto.clear();
            diaAtual++;
            
            // Limpar cache antiga (mantém apenas últimos D dias)
            cache.clearOld(diaAtual);
            
            System.out.println("✓ Novo dia iniciado: " + diaAtual);
            
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Adiciona evento ao dia atual, em memória.
     * 
     * USA writeLock: modifica eventosDiaAtualPorProduto
     */
    public void adicionarEvento(int produtoID, int quantidade, double preco) {
        if (quantidade <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser positiva");
        }
        if (preco < 0) {
            throw new IllegalArgumentException("Preço não pode ser negativo");
        }
        
        writeLock.lock();
        try {
            Evento e = new Evento(produtoID, quantidade, preco);
            
            eventosDiaAtualPorProduto
                .computeIfAbsent(produtoID, k -> new ArrayList<>())
                .add(e);
            
            System.out.println("Evento registado [dia=" + diaAtual + 
                             ", produto=" + produtoID + ", qtd=" + quantidade + 
                             ", preço=" + String.format("%.2f", preco) + "€]");
                             
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Lista apenas os eventos do dia atual, organizados por produto.
     * (Eventos de dias anteriores ficam em disco.)     
     * USA readLock: apenas leitura, permite concorrência
     */
    public String listarEventosDiaAtual() {
        readLock.lock();
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("=== EVENTOS DO DIA ").append(diaAtual).append(" ===\n\n");

            if (eventosDiaAtualPorProduto.isEmpty()) {
                sb.append("(sem eventos registados hoje)\n");
                return sb.toString();
            }

            // Ordena produtos por ID para output consistente
            List<Integer> produtosOrdenados = new ArrayList<>(eventosDiaAtualPorProduto.keySet());
            Collections.sort(produtosOrdenados);
            
            for (int produtoID : produtosOrdenados) {
                List<Evento> eventos = eventosDiaAtualPorProduto.get(produtoID);
                
                sb.append("┌─ Produto ").append(produtoID)
                  .append(" (").append(eventos.size()).append(" eventos)\n");

                for (int i = 0; i < eventos.size(); i++) {
                    Evento e = eventos.get(i);
                    String prefixo = (i == eventos.size() - 1) ? "└─" : "├─";
                    
                    sb.append(String.format(
                        "%s %d. Quantidade: %d | Preço: %.2f€ | Volume: %.2f€\n",
                        prefixo,
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

    /**
     * Validação centralizada de parâmetros
     */
    private void validarParametrosConsulta(int produto, int dias) {
        if (produto < 0) {
            throw new IllegalArgumentException("ID de produto inválido: " + produto);
        }
        if (dias < 0) {
            throw new IllegalArgumentException("Número de dias inválido: " + dias);
        }
    }

    public int getCacheQuantidade(int produto, int dias) throws IOException {
        validarParametrosConsulta(produto, dias);

        if (dias > diaAtual) dias = diaAtual;
        if (dias == 0) return 0;
        return cache.getQuantidade(produto, dias);
    }

    public double getCacheVolume(int produto, int dias) throws IOException {
        validarParametrosConsulta(produto, dias);

        if (dias > diaAtual) dias = diaAtual;
        if (dias == 0) return 0.0;
        return cache.getVolume(produto, dias);
    }

    public double getCachePrecoMedio(int produto, int dias) throws IOException {
        validarParametrosConsulta(produto, dias);

        if (dias > diaAtual) dias = diaAtual;
        if (dias == 0) return 0.0;
        return cache.getPrecoMedio(produto, dias);
    }

    public double getCachePrecoMaximo(int produto, int dias) throws IOException {
        validarParametrosConsulta(produto, dias);

        if (dias > diaAtual) dias = diaAtual;
        if (dias == 0) return 0.0;
        return cache.getPrecoMaximo(produto, dias);
    }

    /**
     * Estatísticas para debugging (usa readLock)
     */
    public String getStats() {
        readLock.lock();
        try {
            int numProdutos = eventosDiaAtualPorProduto.size();
            int numEventos = eventosDiaAtualPorProduto.values().stream().mapToInt(List::size).sum();
            
            return String.format(
                "GestorEventos[dia=%d, produtos=%d, eventos_hoje=%d, D=%d, S=%d]\n%s",
                diaAtual, numProdutos, numEventos, D, S, cache.getStats()
            );
        } finally {
            readLock.unlock();
        }
    }
    
    /**
     * Getter thread-safe para dia atual
     */
    public int getDiaAtual() {
        return diaAtual; // volatile garante visibilidade
    }
}
    // No futuro aqui entram métodos de agregação (quantidadeTotal, volumeTotal, etc.),
    // todos com readLock, usando eventosDiaAtualPorProduto.