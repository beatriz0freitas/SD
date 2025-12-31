package SD.testes;

/**
 * Executor principal de todos os testes
 */
public class TestRunner {
    
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════╗");
        System.out.println("║   SUITE DE TESTES - SISTEMA DISTRIBUÍDO       ║");
        System.out.println("╚════════════════════════════════════════════════╝");
        System.out.println();
        
        boolean runAll = args.length == 0 || args[0].equals("all");
        boolean runUnit = runAll || args[0].equals("unit");
        boolean runIntegration = runAll || args[0].equals("integration");
        
        long inicio = System.currentTimeMillis();
        
        try {
            if (runUnit) {
                executarTestesUnitarios();
            }
            
            if (runIntegration) {
                executarTestesIntegracao();
            }
            
            long duracao = System.currentTimeMillis() - inicio;
            
            System.out.println("\n╔════════════════════════════════════════════════╗");
            System.out.println("║   TESTES CONCLUÍDOS                            ║");
            System.out.println("║   Tempo total: " + formatarTempo(duracao) + "                         ║");
            System.out.println("╚════════════════════════════════════════════════╝");
            
        } catch (Exception e) {
            System.err.println("\n✗ ERRO CRÍTICO NA EXECUÇÃO DOS TESTES");
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void executarTestesUnitarios() {
        System.out.println("┌────────────────────────────────────────────────┐");
        System.out.println("│         TESTES UNITÁRIOS                       │");
        System.out.println("└────────────────────────────────────────────────┘");
        System.out.println();
        
        // ThreadPool
        System.out.println("▶ ThreadPoolTest");
        System.out.println("─────────────────────────────────────────────────");
        ThreadPoolTest.main(new String[]{});
        
        aguardar(1000);
        
        // ConnectionPool
        System.out.println("\n▶ ConnectionPoolTest");
        System.out.println("─────────────────────────────────────────────────");
        ConnectionPoolTest.main(new String[]{});
        
        aguardar(1000);
        
        // NotificationManager
        System.out.println("\n▶ NotificationManagerTest");
        System.out.println("─────────────────────────────────────────────────");
        NotificationManagerTest.main(new String[]{});
        
        aguardar(1000);
    }
    
    private static void executarTestesIntegracao() {
        System.out.println("\n┌────────────────────────────────────────────────┐");
        System.out.println("│         TESTES DE INTEGRAÇÃO                   │");
        System.out.println("└────────────────────────────────────────────────┘");
        System.out.println();
        
        System.out.println("▶ IntegrationTest");
        System.out.println("─────────────────────────────────────────────────");
        IntegrationTest.main(new String[]{});
    }
    
    private static void aguardar(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private static String formatarTempo(long millis) {
        long segundos = millis / 1000;
        long ms = millis % 1000;
        
        if (segundos > 0) {
            return segundos + "s " + ms + "ms";
        } else {
            return ms + "ms";
        }
    }
}