package testes;

import org.junit.jupiter.api.*;

import server.business.services.NotificationManager;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite completa de testes para NotificationManager
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationManagerTest {

    @Test
    @Order(1)
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Notificação de venda específica - caso simples")
    void testeVendaEspecificaSimples() throws InterruptedException {
        NotificationManager manager = new NotificationManager();
        AtomicInteger notificado = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        int diaAtual = 1;

        manager.registarVendaEspecifica(1, 2, diaAtual, msg -> {
            notificado.set(1);
            latch.countDown();
        });

        Map<Integer, List<?>> eventosDia = new HashMap<>();
        eventosDia.put(1, List.of("evento1"));

        // Primeira venda — não deve notificar
        manager.notificarEvento(1, diaAtual, eventosDia, 1, 1);
        assertEquals(0, notificado.get(), "Não deve notificar prematuramente");

        // Segunda venda — agora deve notificar
        eventosDia.put(2, List.of("evento2"));
        manager.notificarEvento(2, diaAtual, eventosDia, 2, 1);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Notificação não recebida");
        assertEquals(1, notificado.get(), "Callback deve ter sido chamado");

        manager.shutdown();
    }

    @Test
    @Order(2)
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Múltiplos interessados na mesma notificação")
    void testeVendaEspecificaMultiplosInteressados() throws InterruptedException {
        NotificationManager manager = new NotificationManager();
        AtomicInteger notificados = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3);
        int diaAtual = 1;

        for (int i = 0; i < 3; i++) {
            manager.registarVendaEspecifica(1, 2, diaAtual, msg -> {
                notificados.incrementAndGet();
                latch.countDown();
            });
        }

        Map<Integer, List<?>> eventosDia = new HashMap<>();
        eventosDia.put(1, List.of("evento1"));
        eventosDia.put(2, List.of("evento2"));

        manager.notificarEvento(2, diaAtual, eventosDia, 2, 1);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Todos callbacks devem ser chamados");
        assertEquals(3, notificados.get(), "3 callbacks devem ter sido executados");

        manager.shutdown();
    }

    @Test
    @Order(3)
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Vendas consecutivas - caso simples")
    void testeVendasConsecutivas() throws InterruptedException {
        NotificationManager manager = new NotificationManager();
        AtomicInteger notificado = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        int diaAtual = 1;

        manager.registarVendasConsecutivas(1, 3, diaAtual, msg -> {
            notificado.set(1);
            latch.countDown();
        });

        Map<Integer, List<?>> eventosDia = new HashMap<>();
        eventosDia.put(1, List.of("evento"));

        // Vendas 1 e 2 - não notifica
        manager.notificarEvento(1, diaAtual, eventosDia, 1, 1);
        manager.notificarEvento(1, diaAtual, eventosDia, 1, 2);

        assertEquals(0, notificado.get(), "Ainda não deve notificar");

        // Venda 3 - agora notifica
        manager.notificarEvento(1, diaAtual, eventosDia, 1, 3);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Notificação não recebida");
        assertEquals(1, notificado.get(), "Callback deve ter sido chamado");

        manager.shutdown();
    }

    @Test
    @Order(4)
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Vendas consecutivas interrompidas - não deve notificar")
    void testeVendasConsecutivasInterrompidas() throws InterruptedException {
        NotificationManager manager = new NotificationManager();
        AtomicInteger notificado = new AtomicInteger(0);
        int diaAtual = 1;

        manager.registarVendasConsecutivas(1, 3, diaAtual, msg -> {
            notificado.set(1);
        });

        Map<Integer, List<?>> eventosDia = new HashMap<>();
        eventosDia.put(1, List.of("evento"));
        eventosDia.put(2, List.of("evento"));

        // Vendas: produto 1, produto 1, produto 2 (interrompeu), produto 1
        manager.notificarEvento(1, diaAtual, eventosDia, 1, 1);
        manager.notificarEvento(1, diaAtual, eventosDia, 1, 2);
        manager.notificarEvento(2, diaAtual, eventosDia, 2, 1); // Interrupção
        manager.notificarEvento(1, diaAtual, eventosDia, 1, 1);

        Thread.sleep(500);
        assertEquals(0, notificado.get(), "Não deve notificar - sequência interrompida");

        manager.shutdown();
    }

    @Test
    @Order(5)
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Limpeza de notificações ao avançar dia")
    void testeLimpezaDia() {
        NotificationManager manager = new NotificationManager();
        AtomicInteger notificado = new AtomicInteger(0);

        manager.registarVendaEspecifica(1, 2, 1, msg -> {
            notificado.set(1);
        });

        manager.limparNotificacoesDia(1);

        Map<Integer, List<?>> eventosDia = new HashMap<>();
        eventosDia.put(1, List.of("evento1"));
        eventosDia.put(2, List.of("evento2"));

        manager.notificarEvento(2, 1, eventosDia, 2, 1);

        assertEquals(0, notificado.get(), "Notificação não deve ocorrer após limpeza");

        manager.shutdown();
    }

    @Test
    @Order(6)
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Notificações são assíncronas")
    void testeNotificacoesAssincronas() throws InterruptedException {
        NotificationManager manager = new NotificationManager();
        CountDownLatch latch = new CountDownLatch(1);

        manager.registarVendaEspecifica(1, 2, 1, msg -> {
            try {
                Thread.sleep(1000); // Callback demora 1 segundo
            } catch (InterruptedException ignored) {
            }
            latch.countDown();
        });

        Map<Integer, List<?>> eventosDia = new HashMap<>();
        eventosDia.put(1, List.of("evento1"));
        eventosDia.put(2, List.of("evento2"));

        long inicio = System.currentTimeMillis();
        manager.notificarEvento(2, 1, eventosDia, 2, 1);
        long duracao = System.currentTimeMillis() - inicio;

        assertTrue(duracao < 500, "Callback não deve bloquear thread chamadora");
        assertTrue(latch.await(3, TimeUnit.SECONDS), "Callback deve ser executado");

        manager.shutdown();
    }

    @Test
    @Order(7)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Teste de concorrência - múltiplas notificações simultâneas")
    void testeConcorrencia() throws InterruptedException {
        NotificationManager manager = new NotificationManager();
        AtomicInteger notificados = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(100);
        int diaAtual = 1;

        // Registar 100 notificações diferentes
        for (int i = 0; i < 100; i++) {
            final int p1 = i / 10;
            final int p2 = (i % 10) + 100;

            manager.registarVendaEspecifica(p1, p2, diaAtual, msg -> {
                notificados.incrementAndGet();
                latch.countDown();
            });
        }

        // Disparar eventos concorrentemente
        for (int i = 0; i < 20; i++) {
            final int produto = i;
            new Thread(() -> {
                Map<Integer, List<?>> eventosDia = new HashMap<>();
                for (int j = 0; j < 110; j++) {
                    eventosDia.put(j, List.of("evento"));
                }
                manager.notificarEvento(produto, diaAtual, eventosDia, produto, 1);
            }).start();
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Todas notificações devem ser recebidas");
        assertEquals(100, notificados.get(), "Todos callbacks devem ser executados");

        manager.shutdown();
    }

    @Test
    @Order(8)
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Ordem de produtos não importa na venda específica")
    void testeOrdemProdutosNaoImporta() throws InterruptedException {
        NotificationManager manager = new NotificationManager();
        AtomicInteger notificado = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        int diaAtual = 1;

        // Registar interesse em produtos 5 e 3 (nesta ordem)
        manager.registarVendaEspecifica(5, 3, diaAtual, msg -> {
            notificado.set(1);
            latch.countDown();
        });

        Map<Integer, List<?>> eventosDia = new HashMap<>();
        
        // Vender primeiro o 3, depois o 5
        eventosDia.put(3, List.of("evento"));
        manager.notificarEvento(3, diaAtual, eventosDia, 3, 1);
        
        eventosDia.put(5, List.of("evento"));
        manager.notificarEvento(5, diaAtual, eventosDia, 5, 1);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Ordem não deve importar");
        assertEquals(1, notificado.get(), "Callback deve ser chamado");

        manager.shutdown();
    }

    @Test
    @Order(9)
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Callback com exceção não afeta outros callbacks")
    void testeCallbackComExcecao() throws InterruptedException {
        NotificationManager manager = new NotificationManager();
        AtomicInteger sucesso1 = new AtomicInteger(0);
        AtomicInteger sucesso2 = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(2);
        int diaAtual = 1;

        // Callback 1 - lança exceção
        manager.registarVendaEspecifica(1, 2, diaAtual, msg -> {
            latch.countDown();
            throw new RuntimeException("Erro no callback 1");
        });

        // Callback 2 - deve executar normalmente
        manager.registarVendaEspecifica(1, 2, diaAtual, msg -> {
            sucesso2.set(1);
            latch.countDown();
        });

        Map<Integer, List<?>> eventosDia = new HashMap<>();
        eventosDia.put(1, List.of("evento"));
        eventosDia.put(2, List.of("evento"));

        manager.notificarEvento(2, diaAtual, eventosDia, 2, 1);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Ambos callbacks devem executar");
        assertEquals(1, sucesso2.get(), "Callback 2 deve ter sucesso");

        manager.shutdown();
    }

    @Test
    @Order(10)
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Notificação não ocorre se dia mudou")
    void testeNotificacaoNaoOcorreSeDialMudou() throws InterruptedException {
        NotificationManager manager = new NotificationManager();
        AtomicInteger notificado = new AtomicInteger(0);
        int diaAtual = 1;

        manager.registarVendaEspecifica(1, 2, diaAtual, msg -> {
            notificado.set(1);
        });

        // Limpar dia (simula avanço de dia)
        manager.limparNotificacoesDia(diaAtual);

        // Tentar notificar no dia seguinte
        Map<Integer, List<?>> eventosDia = new HashMap<>();
        eventosDia.put(1, List.of("evento"));
        eventosDia.put(2, List.of("evento"));

        manager.notificarEvento(2, diaAtual + 1, eventosDia, 2, 1);

        Thread.sleep(500);
        assertEquals(0, notificado.get(), "Não deve notificar - dia mudou");

        manager.shutdown();
    }
}