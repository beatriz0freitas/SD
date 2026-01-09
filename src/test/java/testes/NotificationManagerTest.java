package testes;

import org.junit.jupiter.api.*;

import server.business.services.NotificationManager;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationManagerTest {

    @Test
    @Order(1)
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Notificação de venda específica - caso simples")
    void testeVendaEspecificaSimples() throws InterruptedException {
        NotificationManager manager = new NotificationManager();
        CountDownLatch latch = new CountDownLatch(1);
        int diaAtual = 1;

        final boolean[] notificado = {false};

        manager.registarVendaEspecifica(1, 2, diaAtual, msg -> {
            notificado[0] = true;
            latch.countDown();
        });

        Map<Integer, List<?>> eventosDia = new HashMap<>();
        eventosDia.put(1, List.of("evento1"));

        manager.notificarEvento(1, diaAtual, eventosDia, 1, 1);
        assertFalse(notificado[0], "Não deve notificar prematuramente");

        eventosDia.put(2, List.of("evento2"));
        manager.notificarEvento(2, diaAtual, eventosDia, 2, 1);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Notificação não recebida");
        assertTrue(notificado[0], "Callback deve ter sido chamado");

        manager.shutdown();
    }

    @Test
    @Order(2)
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Múltiplos interessados na mesma notificação")
    void testeVendaEspecificaMultiplosInteressados() throws InterruptedException {
        NotificationManager manager = new NotificationManager();
        int diaAtual = 1;

        int n = 3;
        CountDownLatch latch = new CountDownLatch(n);

        boolean[] chamados = new boolean[n];

        for (int i = 0; i < n; i++) {
            final int idx = i;
            manager.registarVendaEspecifica(1, 2, diaAtual, msg -> {
                chamados[idx] = true;
                latch.countDown();
            });
        }

        Map<Integer, List<?>> eventosDia = new HashMap<>();
        eventosDia.put(1, List.of("evento1"));
        eventosDia.put(2, List.of("evento2"));

        manager.notificarEvento(2, diaAtual, eventosDia, 2, 1);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Todos callbacks devem ser chamados");

        int totalChamados = 0;
        for (int i = 0; i < n; i++) if (chamados[i]) totalChamados++;

        assertEquals(n, totalChamados, "3 callbacks devem ter sido executados");

        manager.shutdown();
    }

    @Test
    @Order(3)
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Vendas consecutivas - caso simples")
    void testeVendasConsecutivas() throws InterruptedException {
        NotificationManager manager = new NotificationManager();
        CountDownLatch latch = new CountDownLatch(1);
        int diaAtual = 1;

        final boolean[] notificado = {false};

        manager.registarVendasConsecutivas(1, 3, diaAtual, msg -> {
            notificado[0] = true;
            latch.countDown();
        });

        Map<Integer, List<?>> eventosDia = new HashMap<>();
        eventosDia.put(1, List.of("evento"));

        manager.notificarEvento(1, diaAtual, eventosDia, 1, 1);
        manager.notificarEvento(1, diaAtual, eventosDia, 1, 2);

        assertFalse(notificado[0], "Ainda não deve notificar");

        manager.notificarEvento(1, diaAtual, eventosDia, 1, 3);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Notificação não recebida");
        assertTrue(notificado[0], "Callback deve ter sido chamado");

        manager.shutdown();
    }

    @Test
    @Order(4)
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Vendas consecutivas interrompidas - não deve notificar")
    void testeVendasConsecutivasInterrompidas() throws InterruptedException {
        NotificationManager manager = new NotificationManager();
        int diaAtual = 1;

        final boolean[] notificado = {false};

        manager.registarVendasConsecutivas(1, 3, diaAtual, msg -> notificado[0] = true);

        Map<Integer, List<?>> eventosDia = new HashMap<>();
        eventosDia.put(1, List.of("evento"));
        eventosDia.put(2, List.of("evento"));

        manager.notificarEvento(1, diaAtual, eventosDia, 1, 1);
        manager.notificarEvento(1, diaAtual, eventosDia, 1, 2);
        manager.notificarEvento(2, diaAtual, eventosDia, 2, 1);
        manager.notificarEvento(1, diaAtual, eventosDia, 1, 1);

        Thread.sleep(500);
        assertFalse(notificado[0], "Não deve notificar - sequência interrompida");

        manager.shutdown();
    }

    @Test
    @Order(5)
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Limpeza de notificações ao avançar dia")
    void testeLimpezaDia() {
        NotificationManager manager = new NotificationManager();

        final boolean[] notificado = {false};

        manager.registarVendaEspecifica(1, 2, 1, msg -> notificado[0] = true);

        manager.limparNotificacoesDia(1);

        Map<Integer, List<?>> eventosDia = new HashMap<>();
        eventosDia.put(1, List.of("evento1"));
        eventosDia.put(2, List.of("evento2"));

        manager.notificarEvento(2, 1, eventosDia, 2, 1);

        assertFalse(notificado[0], "Notificação não deve ocorrer após limpeza");

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
        assertTrue(latch.await(3, TimeUnit.SECONDS), "Callback deve ser executado");

        manager.shutdown();
    }

    @Test
    @Order(7)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Teste de concorrência - múltiplas notificações simultâneas")
    void testeConcorrencia() throws InterruptedException {
        NotificationManager manager = new NotificationManager();
        int diaAtual = 1;

        int n = 100;
        CountDownLatch latch = new CountDownLatch(n);

        boolean[] chamados = new boolean[n];

        for (int i = 0; i < n; i++) {
            final int idx = i;
            final int p1 = i / 10;
            final int p2 = (i % 10) + 100;

            manager.registarVendaEspecifica(p1, p2, diaAtual, msg -> {
                chamados[idx] = true;
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

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Todas notificações devem ser recebidas");

        int total = 0;
        for (int i = 0; i < n; i++) if (chamados[i]) total++;

        assertEquals(n, total, "Todos callbacks devem ser executados");

        manager.shutdown();
    }

    @Test
    @Order(8)
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Ordem de produtos não importa na venda específica")
    void testeOrdemProdutosNaoImporta() throws InterruptedException {
        NotificationManager manager = new NotificationManager();
        CountDownLatch latch = new CountDownLatch(1);
        int diaAtual = 1;

        final boolean[] notificado = {false};

        manager.registarVendaEspecifica(5, 3, diaAtual, msg -> {
            notificado[0] = true;
            latch.countDown();
        });

        Map<Integer, List<?>> eventosDia = new HashMap<>();

        eventosDia.put(3, List.of("evento"));
        manager.notificarEvento(3, diaAtual, eventosDia, 3, 1);

        eventosDia.put(5, List.of("evento"));
        manager.notificarEvento(5, diaAtual, eventosDia, 5, 1);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Ordem não deve importar");
        assertTrue(notificado[0], "Callback deve ser chamado");

        manager.shutdown();
    }

    @Test
    @Order(9)
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Callback com exceção não afeta outros callbacks")
    void testeCallbackComExcecao() throws InterruptedException {
        NotificationManager manager = new NotificationManager();
        CountDownLatch latch = new CountDownLatch(2);
        int diaAtual = 1;

        final boolean[] cb1Executou = {false};
        final boolean[] cb2Executou = {false};

        manager.registarVendaEspecifica(1, 2, diaAtual, msg -> {
            cb1Executou[0] = true;
            latch.countDown();
            throw new RuntimeException("Erro no callback 1");
        });

        manager.registarVendaEspecifica(1, 2, diaAtual, msg -> {
            cb2Executou[0] = true;
            latch.countDown();
        });

        Map<Integer, List<?>> eventosDia = new HashMap<>();
        eventosDia.put(1, List.of("evento"));
        eventosDia.put(2, List.of("evento"));

        manager.notificarEvento(2, diaAtual, eventosDia, 2, 1);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Ambos callbacks devem executar");
        assertTrue(cb1Executou[0], "Callback 1 deve executar");
        assertTrue(cb2Executou[0], "Callback 2 deve executar (não deve ser afetado)");

        manager.shutdown();
    }

    @Test
    @Order(10)
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Notificação não ocorre se dia mudou")
    void testeNotificacaoNaoOcorreSeDialMudou() throws InterruptedException {
        NotificationManager manager = new NotificationManager();

        final boolean[] notificado = {false};

        manager.registarVendaEspecifica(1, 2, 1, msg -> notificado[0] = true);

        manager.limparNotificacoesDia(1);

        Map<Integer, List<?>> eventosDia = new HashMap<>();
        eventosDia.put(1, List.of("evento"));
        eventosDia.put(2, List.of("evento"));

        manager.notificarEvento(2, 2, eventosDia, 2, 1);

        Thread.sleep(500);
        assertFalse(notificado[0], "Não deve notificar - dia mudou");

        manager.shutdown();
    }
}