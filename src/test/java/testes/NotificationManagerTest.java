package testes;

import org.junit.jupiter.api.*;

import server.business.services.NotificationManager;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para NotificationManager
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationManagerTest {

    @Test
    @Order(1)
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
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

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, notificado.get());

        manager.shutdown();
    }

    /* =========================
       2. Múltiplos interessados
       ========================= */

    @Test
    @Order(2)
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
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

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(3, notificados.get());

        manager.shutdown();
    }

    @Test
    @Order(3)
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
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

        manager.notificarEvento(1, diaAtual, eventosDia, 1, 1);
        manager.notificarEvento(1, diaAtual, eventosDia, 1, 2);

        assertEquals(0, notificado.get(), "Ainda não deve notificar");

        manager.notificarEvento(1, diaAtual, eventosDia, 1, 3);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, notificado.get());

        manager.shutdown();
    }

    @Test
    @Order(4)
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
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
    @Order(5)
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testeNotificacoesAssincronas() throws InterruptedException {
        NotificationManager manager = new NotificationManager();
        CountDownLatch latch = new CountDownLatch(1);

        manager.registarVendaEspecifica(1, 2, 1, msg -> {
            try {
                Thread.sleep(1000);
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
        assertTrue(latch.await(3, TimeUnit.SECONDS));

        manager.shutdown();
    }

    @Test
    @Order(6)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testeConcorrencia() throws InterruptedException {
        NotificationManager manager = new NotificationManager();
        AtomicInteger notificados = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(100);
        int diaAtual = 1;

        for (int i = 0; i < 100; i++) {
            final int p1 = i / 10;
            final int p2 = (i % 10) + 100;

            manager.registarVendaEspecifica(p1, p2, diaAtual, msg -> {
                notificados.incrementAndGet();
                latch.countDown();
            });
        }

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

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(100, notificados.get());

        manager.shutdown();
    }
}
