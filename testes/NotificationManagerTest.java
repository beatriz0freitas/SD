package SD.testes;

import server.business.services.NotificationManager;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Testes para NotificationManager
 */
public class NotificationManagerTest {
    
    public static void main(String[] args) {
        System.out.println("=== TESTES DO NOTIFICATIONMANAGER ===\n");
        
        testeVendaEspecificaSimples();
        testeVendaEspecificaMultiplosInteressados();
        testeVendasConsecutivas();
        testeLimpezaDia();
        testeNotificacoesAssincronas();
        testeConcorrencia();
        
        System.out.println("\n=== TODOS OS TESTES CONCLUÍDOS ===");
    }
    
    private static void testeVendaEspecificaSimples() {
        System.out.println("1. Teste Venda Específica - Notificação básica");
        
        NotificationManager manager = new NotificationManager();
        AtomicInteger notificado = new AtomicInteger(0);
        int diaAtual = 1;
        
        // Registrar interesse
        manager.registarVendaEspecifica(1, 2, diaAtual, msg -> {
            notificado.set(1);
        });
        
        // Simular venda do produto 1 (ainda não notifica)
        Map<Integer, List<?>> eventosDia = new HashMap<>();
        eventosDia.put(1, Arrays.asList("evento1"));
        
        manager.notificarEvento(1, diaAtual, eventosDia, 1, 1);
        
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        if (notificado.get() == 0) {
            // Correto, ainda não deve notificar
            
            // Agora vender produto 2 (deve notificar)
            eventosDia.put(2, Arrays.asList("evento2"));
            manager.notificarEvento(2, diaAtual, eventosDia, 2, 1);
            
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            if (notificado.get() == 1) {
                System.out.println("   ✓ PASSOU - Notificação disparada corretamente\n");
            } else {
                System.out.println("   ✗ FALHOU - Notificação não disparada\n");
            }
        } else {
            System.out.println("   ✗ FALHOU - Notificação prematura\n");
        }
        
        manager.shutdown();
    }
    
    private static void testeVendaEspecificaMultiplosInteressados() {
        System.out.println("2. Teste Venda Específica - Múltiplos interessados");
        
        NotificationManager manager = new NotificationManager();
        AtomicInteger notificados = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3);
        int diaAtual = 1;
        
        // 3 callbacks interessados
        for (int i = 0; i < 3; i++) {
            manager.registarVendaEspecifica(1, 2, diaAtual, msg -> {
                notificados.incrementAndGet();
                latch.countDown();
            });
        }
        
        // Vender ambos produtos
        Map<Integer, List<?>> eventosDia = new HashMap<>();
        eventosDia.put(1, Arrays.asList("evento1"));
        eventosDia.put(2, Arrays.asList("evento2"));
        
        manager.notificarEvento(2, diaAtual, eventosDia, 2, 1);
        
        try {
            if (latch.await(2, TimeUnit.SECONDS)) {
                if (notificados.get() == 3) {
                    System.out.println("   ✓ PASSOU - Todos os 3 callbacks notificados\n");
                } else {
                    System.out.println("   ✗ FALHOU - Esperado 3, obteve " + notificados.get() + "\n");
                }
            } else {
                System.out.println("   ✗ Timeout aguardando notificações\n");
            }
        } catch (InterruptedException e) {
            System.out.println("   ✗ Teste interrompido\n");
        }
        
        manager.shutdown();
    }
    
    private static void testeVendasConsecutivas() {
        System.out.println("3. Teste Vendas Consecutivas - Detecção de streak");
        
        NotificationManager manager = new NotificationManager();
        AtomicInteger notificado = new AtomicInteger(0);
        int diaAtual = 1;
        
        // Registrar interesse em 3 vendas consecutivas do produto 1
        manager.registarVendasConsecutivas(1, 3, diaAtual, msg -> {
            notificado.set(1);
        });
        
        Map<Integer, List<?>> eventosDia = new HashMap<>();
        
        // Venda 1 (produto 1)
        eventosDia.put(1, Arrays.asList("evento1"));
        manager.notificarEvento(1, diaAtual, eventosDia, 1, 1);
        
        // Venda 2 (produto 1)
        manager.notificarEvento(1, diaAtual, eventosDia, 1, 2);
        
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        if (notificado.get() == 0) {
            // Venda 3 (produto 1) - deve notificar
            manager.notificarEvento(1, diaAtual, eventosDia, 1, 3);
            
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            if (notificado.get() == 1) {
                System.out.println("   ✓ PASSOU - Notificação após 3 vendas consecutivas\n");
            } else {
                System.out.println("   ✗ FALHOU - Notificação não disparada\n");
            }
        } else {
            System.out.println("   ✗ FALHOU - Notificação prematura\n");
        }
        
        manager.shutdown();
    }
    
    private static void testeLimpezaDia() {
        System.out.println("4. Teste Limpeza Dia - Notificações expiram");
        
        NotificationManager manager = new NotificationManager();
        AtomicInteger notificado = new AtomicInteger(0);
        
        // Registrar interesse no dia 1
        manager.registarVendaEspecifica(1, 2, 1, msg -> {
            notificado.set(1);
        });
        
        // Limpar dia 1 (simula avanço de dia)
        manager.limparNotificacoesDia(1);
        
        // Tentar notificar no dia 1 (não deve funcionar)
        Map<Integer, List<?>> eventosDia = new HashMap<>();
        eventosDia.put(1, Arrays.asList("evento1"));
        eventosDia.put(2, Arrays.asList("evento2"));
        
        manager.notificarEvento(2, 1, eventosDia, 2, 1);
        
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        if (notificado.get() == 0) {
            System.out.println("   ✓ PASSOU - Notificações limpas após avanço de dia\n");
        } else {
            System.out.println("   ✗ FALHOU - Notificação disparada após limpeza\n");
        }
        
        manager.shutdown();
    }
    
    private static void testeNotificacoesAssincronas() {
        System.out.println("5. Teste Assíncrono - Callbacks não bloqueiam");
        
        NotificationManager manager = new NotificationManager();
        CountDownLatch latch = new CountDownLatch(1);
        long[] tempoExecucao = new long[1];
        
        // Callback que demora 1s
        manager.registarVendaEspecifica(1, 2, 1, msg -> {
            try {
                Thread.sleep(1000);
                latch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        // Notificar e medir tempo
        Map<Integer, List<?>> eventosDia = new HashMap<>();
        eventosDia.put(1, Arrays.asList("evento1"));
        eventosDia.put(2, Arrays.asList("evento2"));
        
        long inicio = System.currentTimeMillis();
        manager.notificarEvento(2, 1, eventosDia, 2, 1);
        long fim = System.currentTimeMillis();
        
        tempoExecucao[0] = fim - inicio;
        
        try {
            // Callback deve ser assíncrono
            if (tempoExecucao[0] < 500) {
                System.out.println("   ✓ PASSOU - Notificação assíncrona (tempo: " + tempoExecucao[0] + "ms)\n");
            } else {
                System.out.println("   ✗ FALHOU - Notificação bloqueou (tempo: " + tempoExecucao[0] + "ms)\n");
            }
            
            // Aguardar callback completar
            latch.await(3, TimeUnit.SECONDS);
            
        } catch (InterruptedException e) {
            System.out.println("   ✗ Teste interrompido\n");
        }
        
        manager.shutdown();
    }
    
    private static void testeConcorrencia() {
        System.out.println("6. Teste Concorrência - Múltiplas notificações simultâneas");
        
        NotificationManager manager = new NotificationManager();
        AtomicInteger notificados = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(100);
        int diaAtual = 1;
        
        // 100 callbacks para diferentes produtos
        for (int i = 0; i < 100; i++) {
            final int produto1 = i / 10;
            final int produto2 = (i % 10) + 100;
            
            manager.registarVendaEspecifica(produto1, produto2, diaAtual, msg -> {
                notificados.incrementAndGet();
                latch.countDown();
            });
        }
        
        // Notificar todos os produtos concorrentemente
        for (int i = 0; i < 20; i++) {
            final int produtoID = i;
            new Thread(() -> {
                Map<Integer, List<?>> eventosDia = new HashMap<>();
                for (int j = 0; j < 110; j++) {
                    eventosDia.put(j, Arrays.asList("evento"));
                }
                
                manager.notificarEvento(produtoID, diaAtual, eventosDia, produtoID, 1);
            }).start();
        }
        
        try {
            if (latch.await(10, TimeUnit.SECONDS)) {
                if (notificados.get() == 100) {
                    System.out.println("   ✓ PASSOU - 100 notificações processadas concorrentemente\n");
                } else {
                    System.out.println("   ✗ FALHOU - Esperado 100, obteve " + notificados.get() + "\n");
                }
            } else {
                System.out.println("   ✗ Timeout - Obteve " + notificados.get() + "/100 notificações\n");
            }
        } catch (InterruptedException e) {
            System.out.println("   ✗ Teste interrompido\n");
        }
        
        manager.shutdown();
    }
}
