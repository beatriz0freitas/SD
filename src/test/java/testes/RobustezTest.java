package testes;

import client.ClienteMiddleware;
import client.stub.StubFactory;
import common.dto.EventoDTO;
import common.dto.RespostaDTO;
import common.dto.UsuarioDTO;
import common.interfaces.IServicoAutenticacao;
import common.interfaces.IServicoEventos;
import middleware.Message;
import middleware.Protocolo;
import server.Server;

import org.junit.jupiter.api.*;

import java.io.DataOutputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de robustez (enunciado):
 * verificar o que acontece quando um cliente NÃO consome as respostas.
 *
 * Este teste é OBSERVACIONAL:
 * - não exige uma taxa mínima alta (pode degradar)
 * - exige que o sistema não bloqueie completamente e que haja algum progresso
 * - imprime métricas para o relatório
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RobustezTest {

    private static final String HOST = "localhost";
    private static final int PORT = 5564;

    // Estes IDs funcionaram no teu teste (houve registos/autenticações a acontecer),
    // por isso mantemos.
    private static final byte SVC_ID = 1;
    private static final byte MTD_ID = 1;

    private Server servidor;
    private Thread serverThread;

    @BeforeAll
    void iniciarServidor() throws InterruptedException {
        servidor = new Server(PORT, 60, 15);
        serverThread = new Thread(servidor::iniciar, "RobustezServer");
        serverThread.start();
        Thread.sleep(2000);
    }

    @AfterAll
    void pararServidor() throws InterruptedException {
        if (servidor != null) servidor.parar();
        if (serverThread != null) serverThread.join(5000);
    }

    @Test
    @DisplayName("Robustez: cliente não lê respostas (medir impacto, sem deadlock)")
    @Timeout(value = 35, unit = TimeUnit.SECONDS)
    void testeClienteNaoConsomeRespostas() throws Exception {

        CountDownLatch started = new CountDownLatch(1);

        Thread badClient = new Thread(() -> {
            try (Socket sock = new Socket(HOST, PORT)) {
                DataOutputStream out = new DataOutputStream(sock.getOutputStream());
                Protocolo protocolo = new Protocolo();

                started.countDown();

                long tag = 1;
                long end = System.currentTimeMillis() + 5000; // 5s flood

                while (System.currentTimeMillis() < end) {
                    // payload null para minimizar custo do cliente (o objetivo é encher respostas)
                    Message req = Message.request(tag++, SVC_ID, MTD_ID, null);
                    protocolo.enviar(req, out);
                }

                // manter socket aberto mais 5s sem ler nada
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}

            } catch (Exception ignored) {
                // servidor pode fechar ligação: ok
            }
        }, "BadClient-NoRead");

        badClient.start();
        assertTrue(started.await(2, TimeUnit.SECONDS), "Bad client não arrancou");

        // Clientes normais
        int normalClients = 10;
        CountDownLatch latch = new CountDownLatch(normalClients);
        boolean[] ok = new boolean[normalClients];

        long inicio = System.currentTimeMillis();

        for (int i = 0; i < normalClients; i++) {
            final int id = i;

            new Thread(() -> {
                ClienteMiddleware middleware = null;
                try {
                    middleware = new ClienteMiddleware(HOST, PORT, true, 1);
                    middleware.conectar();

                    StubFactory stubs = new StubFactory(middleware);
                    IServicoAutenticacao auth = stubs.criarStubAutenticacao();
                    IServicoEventos eventos = stubs.criarStubEventos();

                    UsuarioDTO u = new UsuarioDTO("robust" + id + System.nanoTime(), "pass" + id);

                    RespostaDTO r1 = auth.registrar(u);
                    if (r1 == null || !r1.isSucesso()) return;

                    RespostaDTO r2 = auth.autenticar(u);
                    if (r2 == null || !r2.isSucesso()) return;

                    for (int j = 0; j < 5; j++) {
                        RespostaDTO r = eventos.registrarEvento(new EventoDTO((id % 10) + 1, 1, 10.0));
                        if (r == null || !r.isSucesso()) return;
                    }

                    ok[id] = true;

                } catch (Exception ignored) {
                } finally {
                    try { if (middleware != null) middleware.desconectar(); } catch (Exception ignored) {}
                    latch.countDown();
                }
            }, "NormalClient-" + i).start();
        }

        assertTrue(latch.await(20, TimeUnit.SECONDS),
                "Clientes normais não terminaram (possível deadlock/bloqueio global)");

        long duracao = System.currentTimeMillis() - inicio;

        int sucessos = 0;
        for (int i = 0; i < normalClients; i++) if (ok[i]) sucessos++;
        int falhas = normalClients - sucessos;

        double taxa = (sucessos * 100.0) / normalClients;

        System.out.println("\n[ROBUSTEZ - Cliente não lê respostas]");
        System.out.println("  Sucessos: " + sucessos + "/" + normalClients);
        System.out.println("  Falhas:   " + falhas);
        System.out.println("  Taxa:     " + String.format("%.1f", taxa) + "%");
        System.out.println("  Duração:  " + duracao + " ms\n");

        // Critério de "robustez" para PASSAR:
        // - não bloqueou (já garantido pelo await)
        // - houve pelo menos algum progresso
        assertTrue(sucessos >= 1,
                "Robustez fraca: nenhum cliente normal conseguiu completar (0/" + normalClients + "). " +
                "Isto sugere bloqueio severo quando um cliente não consome respostas.");
    }
}
