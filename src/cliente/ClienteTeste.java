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
        private final AtomicLong totalErrors = new AtomicLong(0);
        private final AtomicLong totalInterrupted = new AtomicLong(0);

        void registarSucesso(long latencyNanos) {
            totalLatencyNanos.addAndGet(latencyNanos);
            totalRequests.incrementAndGet();
        }
        
        void registarErro() {
            totalErrors.incrementAndGet();
        }
        
        void registarInterrupcao() {
            totalInterrupted.incrementAndGet();
        }

        long getTotalRequests() {
            return totalRequests.get();
        }

        long getTotalErrors() {
            return totalErrors.get();
        }
        
        long getTotalInterrupted() {
            return totalInterrupted.get();
        }

        double getLatenciaMediaMs() {
            long n = totalRequests.get();
            if (n == 0) return 0.0;
            double totalMs = totalLatencyNanos.get() / 1_000_000.0;
            return totalMs / n;
        }

        double getTaxaSucesso() {
            long total = totalRequests.get() + totalErrors.get() + totalInterrupted.get();
            if (total == 0) return 0.0;
            return (totalRequests.get() * 100.0) / total;
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

        public void run() {
            BibliotecaCliente cliente = new BibliotecaCliente(host, porta);
            String threadName = String.format("T%02d", id);

            try {
                cliente.conectar();
                System.out.printf("[%s] Ligado a %s:%d%n", threadName, host, porta);
                
                // Registar/Autenticar
                try {
                    String username = "teste_" + id;
                    String password = "pass_" + id;
                    
                    cliente.registar(username, password);
                    Mensagem respAuth = cliente.autenticar(username, password);
                    
                    if (!respAuth.isSuccesso()) {
                        System.err.printf("[%s] ✗ Autenticação falhada%n", threadName);
                        return;
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.printf("[%s] ⚠ Interrompido durante autenticação%n", threadName);
                    return;
                }
                
            } catch (IOException e) {
                System.err.printf("[%s] ✗ Falha ao conectar: %s%n", threadName, e.getMessage());
                return;
            }

            // Loop de pedidos
            for (int i = 0; i < pedidos; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    System.out.printf("[%s] ⚠ Thread interrompida, parando...%n", threadName);
                    metrics.registarInterrupcao();
                    break;
                }
                
                // Escolhe operação aleatória
                int op = random.nextInt(4);
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
                    
                    long t1 = System.nanoTime();
                    long latency = t1 - t0;
                    double latencyMs = latency / 1_000_000.0;

                    metrics.registarSucesso(latency);

                    System.out.printf("[%s] %s | produto=%d dias=%d | %.2f ms | %s%n", threadName, nomeOp, produto, dias, latencyMs,
                                        resposta != null && resposta.isSuccesso() ? "✓" : "✗");
                    
                } catch (IOException e) {
                    metrics.registarErro();
                    System.err.printf("[%s] ✗ IOException: %s%n", threadName, e.getMessage());
                    break; // Conexão perdida, para esta thread
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    metrics.registarInterrupcao();
                    System.out.printf("[%s] Interrompido%n", threadName);
                    break;
                }

                // Pausa entre pedidos (~1 segundo)
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.printf("[%s] Interrompido durante sleep%n", threadName);
                    break;
                }
            }
            
            // Desconectar
            cliente.desconectar();
            System.out.printf("[%s] ✓ Desconectado%n", threadName);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        String host       = args.length > 0 ? args[0] : "localhost";
        int porta         = args.length > 1 ? Integer.parseInt(args[1]) : 5001;
        int numThreads    = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        int produto       = args.length > 3 ? Integer.parseInt(args[3]) : 1;
        int dias          = args.length > 4 ? Integer.parseInt(args[4]) : 5;
        int pedidosThread = args.length > 5 ? Integer.parseInt(args[5]) : 20;

        System.out.println("╔════════════════════════════════════════════════╗");
        System.out.println("║       TESTE DE CONCORRÊNCIA - CLIENTE          ║");
        System.out.println("╚════════════════════════════════════════════════╝");
        System.out.printf("Host:              %s%n", host);
        System.out.printf("Porta:             %d%n", porta);
        System.out.printf("Threads:           %d%n", numThreads);
        System.out.printf("Produto:           %d%n", produto);
        System.out.printf("Dias:              %d%n", dias);
        System.out.printf("Pedidos/Thread:    %d%n", pedidosThread);
        System.out.printf("Total esperado:    %d pedidos%n", numThreads * pedidosThread);
        System.out.println("════════════════════════════════════════════════");
        System.out.println();

        Metrics metrics = new Metrics();
        List<Thread> workers = new ArrayList<>();

        // Criar e iniciar threads
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < numThreads; i++) {
            Thread t = new Thread(new Worker(i + 1, host, porta, produto, dias, pedidosThread, metrics), "Worker-" + (i + 1));
            workers.add(t);
            t.start();

            // Pequena pausa para escalonar o início
            Thread.sleep(100);
        }

        // Esperar que todas as threads acabem
        for (Thread t : workers) {
            t.join();
        }

        long endTime = System.currentTimeMillis();
        long totalTimeMs = endTime - startTime;

        // Relatório final
        long totalSucesso = metrics.getTotalRequests();
        long totalErros = metrics.getTotalErrors();
        long totalInterrupted = metrics.getTotalInterrupted();
        long totalGeral = totalSucesso + totalErros + totalInterrupted;
        double latenciaMedia = metrics.getLatenciaMediaMs();
        double taxaSucesso = metrics.getTaxaSucesso();
        double throughput = (totalSucesso * 1000.0) / totalTimeMs;

        System.out.println("\n╔════════════════════════════════════════════════╗");
        System.out.println("║           RELATÓRIO FINAL                      ║");
        System.out.println("╚════════════════════════════════════════════════╝");
        System.out.printf("Tempo total:       %.2f segundos%n", totalTimeMs / 1000.0);
        System.out.printf("Pedidos esperados: %d%n", numThreads * pedidosThread);
        System.out.printf("Pedidos enviados:  %d%n", totalGeral);
        System.out.println("────────────────────────────────────────────────");
        System.out.printf(" Sucesso:         %d (%.1f%%)%n", totalSucesso, taxaSucesso);
        System.out.printf(" Erros I/O:       %d%n", totalErros);
        System.out.printf(" Interrompidos:   %d%n", totalInterrupted);
        System.out.println("────────────────────────────────────────────────");
        System.out.printf("Latência média:    %.2f ms%n", latenciaMedia);
        System.out.printf("Throughput:        %.2f req/s%n", throughput);
        System.out.println("════════════════════════════════════════════════");
        
        // Análise
        if (taxaSucesso >= 95.0) {
            System.out.println("\n EXCELENTE: Taxa de sucesso >= 95%");
        } else if (taxaSucesso >= 80.0) {
            System.out.println("\n ACEITÁVEL: Taxa de sucesso >= 80%");
        } else {
            System.out.println("\n PROBLEMÁTICO: Taxa de sucesso < 80%");
            System.out.println("   Verifique: servidor sobrecarregado? timeouts? cache?");
        }
        
        if (latenciaMedia < 50) {
            System.out.println(" EXCELENTE: Latência média < 50ms");
        } else if (latenciaMedia < 200) {
            System.out.println(" ACEITÁVEL: Latência média < 200ms");
        } else {
            System.out.println(" LENTO: Latência média >= 200ms");
        }
    }
}
