package testes;

import org.junit.jupiter.api.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import client.connection.ConnectionPool;
import client.connection.PooledConnection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para ConnectionPool
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConnectionPoolTest {

    private static final int TEST_PORT = 9999;
    private ServerSocket mockServer;
    private Thread serverThread;

    @BeforeAll
    void iniciarMockServer() throws IOException {
        mockServer = new ServerSocket(TEST_PORT);

        serverThread = new Thread(() -> {
            while (!mockServer.isClosed()) {
                try {
                    Socket client = mockServer.accept();

                    new Thread(() -> {
                        try (DataInputStream in =
                                     new DataInputStream(client.getInputStream());
                             DataOutputStream out =
                                     new DataOutputStream(client.getOutputStream())) {

                            while (!client.isClosed()) {
                                int data = in.readInt();
                                out.writeInt(data);
                                out.flush();
                            }
                        } catch (IOException ignored) {
                        }
                    }).start();

                } catch (IOException e) {
                    break;
                }
            }
        }, "MockServer");

        serverThread.start();

        try {
            Thread.sleep(100); // garantir servidor ativo
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterAll
    void pararMockServer() throws Exception {
        if (mockServer != null && !mockServer.isClosed()) {
            mockServer.close();
        }
        if (serverThread != null) {
            serverThread.join(1000);
        }
    }

    @Test
    void testeBasico() throws Exception {
        ConnectionPool pool = new ConnectionPool("localhost", TEST_PORT, 5);

        PooledConnection conn = pool.getConnection();
        assertNotNull(conn);
        assertTrue(conn.isValid());

        conn.close();
        pool.close();
    }

    @Test
    void testeReutilizacao() throws Exception {
        ConnectionPool pool = new ConnectionPool("localhost", TEST_PORT, 2);

        PooledConnection conn1 = pool.getConnection();
        String addr1 = conn1.getRemoteAddress();
        conn1.close();

        PooledConnection conn2 = pool.getConnection();
        String addr2 = conn2.getRemoteAddress();
        conn2.close();

        pool.close();

        assertEquals(addr1, addr2, "A conexão deveria ser reutilizada");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testeMaxConnections() throws Exception {
        ConnectionPool pool = new ConnectionPool("localhost", TEST_PORT, 3);
        List<PooledConnection> connections = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            connections.add(pool.getConnection());
        }

        CountDownLatch bloqueio = new CountDownLatch(1);
        CountDownLatch desbloqueio = new CountDownLatch(1);
        AtomicInteger estado = new AtomicInteger(0);

        Thread t = new Thread(() -> {
            try {
                estado.set(1); // vai bloquear
                bloqueio.countDown();
                PooledConnection conn = pool.getConnection();
                estado.set(2); // desbloqueou
                conn.close();
                desbloqueio.countDown();
            } catch (Exception ignored) {
            }
        });

        t.start();
        assertTrue(bloqueio.await(1, TimeUnit.SECONDS));

        Thread.sleep(300);
        assertEquals(1, estado.get(), "Thread deveria estar bloqueada");

        connections.get(0).close(); // libertar uma ligação

        assertTrue(desbloqueio.await(2, TimeUnit.SECONDS));
        assertEquals(2, estado.get(), "Thread deveria desbloquear");

        pool.close();
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testeConcorrencia() throws Exception {
        ConnectionPool pool = new ConnectionPool("localhost", TEST_PORT, 10);
        AtomicInteger sucesso = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(50);

        for (int i = 0; i < 50; i++) {
            new Thread(() -> {
                try {
                    PooledConnection conn = pool.getConnection();

                    DataOutputStream out = conn.getOutputStream();
                    DataInputStream in = conn.getInputStream();

                    out.writeInt(42);
                    out.flush();

                    int response = in.readInt();
                    if (response == 42) {
                        sucesso.incrementAndGet();
                    }

                    Thread.sleep(10);
                    conn.close();

                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue(latch.await(12, TimeUnit.SECONDS));
        assertEquals(50, sucesso.get());

        pool.close();
    }

    @Test
    void testeConexaoInvalida() throws Exception {
        ConnectionPool pool = new ConnectionPool("localhost", TEST_PORT, 2);

        PooledConnection conn1 = pool.getConnection();
        conn1.closePhysical();
        conn1.invalidate();
        conn1.close();

        PooledConnection conn2 = pool.getConnection();
        assertTrue(conn2.isValid(), "Pool deveria criar nova conexão válida");

        conn2.close();
        pool.close();
    }

    @Test
    void testeClose() throws Exception {
        ConnectionPool pool = new ConnectionPool("localhost", TEST_PORT, 5);
        List<PooledConnection> connections = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            connections.add(pool.getConnection());
        }

        connections.get(0).close();
        connections.get(1).close();

        pool.close();

        assertThrows(IOException.class, pool::getConnection,
                "Pool fechado não deve aceitar novas conexões");
    }
}
