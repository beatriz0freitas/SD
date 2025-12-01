package src.cliente;

import src.uteis.Mensagem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cliente de teste de concorrência.
 *
 * - Cria N threads, cada uma com a sua própria BibliotecaCliente (logo, a sua própria ligação TCP).
 * - Cada thread faz pedidos de métricas em loop, com pausa ~1 segundo entre pedidos.
 * - Mede tempos de resposta individuais e uma média global no fim.
 *
 * Argumentos (todos opcionais):
 *   0: host          (default: 127.0.0.1)
 *   1: porta         (default: 12345)
 *   2: numThreads    (default: 4)
 *   3: produto       (default: 1)
 *   4: dias          (default: 5)
 *   5: pedidosPorThread (default: 20)
 */

// TODO:
//  - colocar ficheiros de dia
//. - colocar admin a avançar dias
//  - 
public class ClienteTeste {

    // Métricas globais partilhadas entre threads
    private static class Metrics {
        private final AtomicLong totalLatencyNanos = new AtomicLong(0);
        private final AtomicLong totalRequests = new AtomicLong(0);

        void registar(long latencyNanos) {
            totalLatencyNanos.addAndGet(latencyNanos);
            totalRequests.incrementAndGet();
        }

        long getTotalRequests() {
            return totalRequests.get();
        }

        double getLatenciaMediaMs() {
            long n = totalRequests.get();
            if (n == 0) return 0.0;
            double totalMs = totalLatencyNanos.get() / 1_000_000.0;
            return totalMs / n;
        }
    }

    private static class Worker implements Runnable {
        private final int id;
        private final String host;
        private final int porta;
        private final int produto;
        private final int dias;
        private final int pedidos;
        private final Metrics metrics;
        private final Random random = new Random();

        Worker(int id, String host, int porta, int produto, int dias, int pedidos, Metrics metrics) {
            this.id = id;
            this.host = host;
            this.porta = porta;
            this.produto = produto;
            this.dias = dias;
            this.pedidos = pedidos;
            this.metrics = metrics;
        }

        @Override
        public void run() {
            BibliotecaCliente cliente = new BibliotecaCliente(host, porta);
            try {
                cliente.conectar();
                System.out.printf("[T%02d] Ligado a %s:%d%n", id, host, porta);
                cliente.registar("bolas", "bolas");
                cliente.autenticar("bolas", "bolas");
            } catch (IOException e) {
                System.err.printf("[T%02d] Falha ao conectar: %s%n", id, e.getMessage());
                return;
            }

            try {
                for (int i = 0; i < pedidos; i++) {
                    // Escolhe aleatoriamente que métrica pedir (todas usam o mesmo produto/dias -> ótimo para testar cache)
                    int op = random.nextInt(4); // 0..3
                    String nomeOp;
                    Mensagem resposta = null;

                    long t0 = System.nanoTime();
                    try {
                        switch (op) {
                            case 0:
                                nomeOp = "QUANTIDADE_VENDAS";
                                resposta = cliente.quantidadeVendas(produto, dias);
                                break;
                            case 1:
                                nomeOp = "VOLUME_VENDAS";
                                resposta = cliente.volumeVendas(produto, dias);
                                break;
                            case 2:
                                nomeOp = "PRECO_MEDIO";
                                resposta = cliente.precoMedio(produto, dias);
                                break;
                            case 3:
                            default:
                                nomeOp = "PRECO_MAXIMO";
                                resposta = cliente.precoMaximo(produto, dias);
                                break;
                        }
                    } catch (IOException e) {
                        System.err.printf("[T%02d] Erro ao enviar pedido: %s%n", id, e.getMessage());
                        break; // esta thread pára
                    }
                    long t1 = System.nanoTime();
                    long latency = t1 - t0;
                    double latencyMs = latency / 1_000_000.0;

                    metrics.registar(latency);

                    System.out.printf(
                            "[T%02d] op=%s produto=%d dias=%d latencia=%.3f ms resposta=%s%n",
                            id, nomeOp, produto, dias, latencyMs,
                            resposta != null ? resposta.toString() : "null"
                    );

                    // Pausa "realista" de ~1 segundo entre pedidos
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        System.out.printf("[T%02d] Interrompida.%n", id);
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                cliente.desconectar();
                System.out.printf("[T%02d] Ligação fechada.%n", id);
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        String host       = args.length > 0 ? args[0] : "localhost";
        int porta         = args.length > 1 ? Integer.parseInt(args[1]) : 5001;
        int numThreads    = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        int produto       = args.length > 3 ? Integer.parseInt(args[3]) : 1;
        int dias          = args.length > 4 ? Integer.parseInt(args[4]) : 5;
        int pedidosThread = args.length > 5 ? Integer.parseInt(args[5]) : 20;

        System.out.printf(
                "ClienteTeste: host=%s porta=%d threads=%d produto=%d dias=%d pedidosPorThread=%d%n",
                host, porta, numThreads, produto, dias, pedidosThread
        );

        Metrics metrics = new Metrics();
        List<Thread> workers = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            Thread t = new Thread(new Worker(i + 1, host, porta, produto, dias, pedidosThread, metrics));
            workers.add(t);
            t.start();
        }

        // Esperar que todas as threads acabem
        for (Thread t : workers) {
            t.join();
        }

        long total = metrics.getTotalRequests();
        double media = metrics.getLatenciaMediaMs();

        System.out.println("==================================================");
        System.out.printf("Teste terminado. Total de pedidos: %d%n", total);
        System.out.printf("Latência média global: %.3f ms%n", media);
        System.out.println("==================================================");
        System.out.println("Dica: aumenta o nº de threads e repete para ver o impacto na cache e na carga do servidor.");
    }
}
