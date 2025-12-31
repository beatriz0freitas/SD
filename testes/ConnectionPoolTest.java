package SD.testes;

import client.connection.ConnectionPool;
import client.connection.PooledConnection;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Testes para ConnectionPool
 */
public class ConnectionPoolTest {
    private static final int TEST_PORT = 9999;
    private static ServerSocket mockServer;
    private static Thread serverThread;
    
    public static void main(String[] args) {
        System.out.println("=== TESTES DO CONNECTIONPOOL ===\n");
        
        try {
            iniciarMockServer();
            
            testeBasico();
            testeReutilizacao();
            testeMaxConnections();
            testeConcorrencia();
            testeConexaoInvalida();
            testeClose();
            
            System.out.println("\n=== TODOS OS TESTES CONCLUÍDOS ===");
            
        } catch (Exception e) {
            System.err.println("Erro ao executar testes: " + e.getMessage());
            e.printStackTrace();
        } finally {
            pararMockServer();
        }
    }
    
    private static void iniciarMockServer() throws IOException {
        mockServer = new ServerSocket(TEST_PORT);
        
        serverThread = new Thread(() -> {
            while (!mockServer.isClosed()) {
                try {
                    Socket client = mockServer.accept();
                    
                    // Thread para manter conexão aberta
                    new Thread(() -> {
                        try {
                            DataInputStream in = new DataInputStream(client.getInputStream());
                            DataOutputStream out = new DataOutputStream(client.getOutputStream());
                            
                            // Eco simples
                            while (!client.isClosed()) {
                                try {
                                    int data = in.readInt();
                                    out.writeInt(data);
                                    out.flush();
                                } catch (EOFException e) {
                                    break;
                                }
                            }
                        } catch (IOException e) {
                            // Conexão fechada
                        }
                    }).start();
                    
                } catch (IOException e) {
                    if (!mockServer.isClosed()) {
                        System.err.println("Erro ao aceitar conexão: " + e.getMessage());
                    }
                    break;
                }
            }
        }, "MockServer");
        
        serverThread.start();
        
        // Aguardar servidor ficar pronto
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("Mock server iniciado na porta " + TEST_PORT + "\n");
    }
    
    private static void pararMockServer() {
        try {
            if (mockServer != null && !mockServer.isClosed()) {
                mockServer.close();
            }
            if (serverThread != null) {
                serverThread.join(1000);
            }
        } catch (Exception e) {
            // Ignorar
        }
    }
    
    private static void testeBasico() {
        System.out.println("1. Teste Básico - Obter e devolver conexão");
        
        ConnectionPool pool = new ConnectionPool("localhost", TEST_PORT, 5);
        
        try {
            PooledConnection conn = pool.getConnection();
            
            if (conn == null) {
                System.out.println("   ✗ FALHOU - Conexão null\n");
                pool.close();
                return;
            }
            
            if (!conn.isValid()) {
                System.out.println("   ✗ FALHOU - Conexão inválida\n");
                pool.close();
                return;
            }
            
            conn.close(); // Devolver ao pool
            
            System.out.println("   ✓ PASSOU - Conexão obtida e devolvida\n");
            
        } catch (Exception e) {
            System.out.println("   ✗ FALHOU - Exceção: " + e.getMessage() + "\n");
        } finally {
            pool.close();
        }
    }
    
    private static void testeReutilizacao() {
        System.out.println("2. Teste Reutilização - Mesma conexão reutilizada");
        
        ConnectionPool pool = new ConnectionPool("localhost", TEST_PORT, 2);
        
        try {
            // Obter primeira vez
            PooledConnection conn1 = pool.getConnection();
            String addr1 = conn1.getRemoteAddress();
            conn1.close();
            
            // Obter segunda vez (deve reutilizar)
            PooledConnection conn2 = pool.getConnection();
            String addr2 = conn2.getRemoteAddress();
            conn2.close();
            
            // Endereços devem ser iguais (mesma conexão)
            if (addr1.equals(addr2)) {
                System.out.println("   ✓ PASSOU - Conexão reutilizada (" + addr1 + ")\n");
            } else {
                System.out.println("   ✗ FALHOU - Conexões diferentes\n");
            }
            
        } catch (Exception e) {
            System.out.println("   ✗ FALHOU - Exceção: " + e.getMessage() + "\n");
        } finally {
            pool.close();
        }
    }
    
    private static void testeMaxConnections() {
        System.out.println("3. Teste Max Connections - Limite respeitado");
        
        ConnectionPool pool = new ConnectionPool("localhost", TEST_PORT, 3);
        List<PooledConnection> connections = new ArrayList<>();
        
        try {
            // Obter 3 conexões (máximo)
            for (int i = 0; i < 3; i++) {
                connections.add(pool.getConnection());
            }
            
            // Tentar obter 4ª conexão (deve bloquear)
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger bloqueou = new AtomicInteger(0);
            
            Thread t = new Thread(() -> {
                try {
                    bloqueou.set(1);
                    PooledConnection conn = pool.getConnection();
                    bloqueou.set(2);
                    conn.close();
                    latch.countDown();
                } catch (Exception e) {
                    // Ignorar
                }
            });
            
            t.start();
            Thread.sleep(500); // Aguardar thread bloquear
            
            if (bloqueou.get() == 1) {
                // Thread bloqueou, liberar uma conexão
                connections.get(0).close();
                
                // Aguardar thread desbloquear
                if (latch.await(2, TimeUnit.SECONDS) && bloqueou.get() == 2) {
                    System.out.println("   ✓ PASSOU - Limite de conexões respeitado\n");
                } else {
                    System.out.println("   ✗ FALHOU - Thread não desbloqueou\n");
                }
            } else {
                System.out.println("   ✗ FALHOU - Thread não bloqueou\n");
            }
            
        } catch (Exception e) {
            System.out.println("   ✗ FALHOU - Exceção: " + e.getMessage() + "\n");
        } finally {
            for (PooledConnection conn : connections) {
                try {
                    conn.close();
                } catch (Exception e) {
                    // Ignorar
                }
            }
            pool.close();
        }
    }
    
    private static void testeConcorrencia() {
        System.out.println("4. Teste Concorrência - Múltiplas threads");
        
        ConnectionPool pool = new ConnectionPool("localhost", TEST_PORT, 10);
        AtomicInteger sucesso = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(50);
        
        // 50 threads obtendo conexões
        for (int i = 0; i < 50; i++) {
            new Thread(() -> {
                try {
                    PooledConnection conn = pool.getConnection();
                    
                    // Usar conexão
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
                    
                } catch (Exception e) {
                    System.err.println("Erro na thread: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        try {
            if (latch.await(15, TimeUnit.SECONDS)) {
                if (sucesso.get() == 50) {
                    System.out.println("   ✓ PASSOU - 50 threads usaram pool corretamente\n");
                } else {
                    System.out.println("   ✗ FALHOU - Esperado 50, obteve " + sucesso.get() + "\n");
                }
            } else {
                System.out.println("   ✗ Timeout aguardando threads\n");
            }
        } catch (InterruptedException e) {
            System.out.println("   ✗ Teste interrompido\n");
        } finally {
            pool.close();
        }
    }
    
    private static void testeConexaoInvalida() {
        System.out.println("5. Teste Conexão Inválida - Renovar conexão fechada");
        
        ConnectionPool pool = new ConnectionPool("localhost", TEST_PORT, 2);
        
        try {
            PooledConnection conn1 = pool.getConnection();
            
            // Fechar conexão fisicamente (simular falha)
            conn1.closePhysical();
            conn1.invalidate();
            conn1.close(); // Devolver ao pool
            
            // Obter nova conexão (pool deve criar nova)
            PooledConnection conn2 = pool.getConnection();
            
            if (conn2.isValid()) {
                System.out.println("   ✓ PASSOU - Conexão inválida renovada\n");
            } else {
                System.out.println("   ✗ FALHOU - Nova conexão também inválida\n");
            }
            
            conn2.close();
            
        } catch (Exception e) {
            System.out.println("   ✗ FALHOU - Exceção: " + e.getMessage() + "\n");
        } finally {
            pool.close();
        }
    }
    
    private static void testeClose() {
        System.out.println("6. Teste Close - Pool fecha todas as conexões");
        
        ConnectionPool pool = new ConnectionPool("localhost", TEST_PORT, 5);
        List<PooledConnection> connections = new ArrayList<>();
        
        try {
            // Obter várias conexões
            for (int i = 0; i < 3; i++) {
                connections.add(pool.getConnection());
            }
            
            // Devolver algumas
            connections.get(0).close();
            connections.get(1).close();
            
            // Fechar pool
            pool.close();
            
            // Tentar obter nova conexão (deve falhar)
            boolean falhou = false;
            try {
                pool.getConnection();
            } catch (IOException e) {
                falhou = true;
            }
            
            if (falhou) {
                System.out.println("   ✓ PASSOU - Pool fechado corretamente\n");
            } else {
                System.out.println("   ✗ FALHOU - Pool aceitou conexão após close\n");
            }
            
        } catch (Exception e) {
            System.out.println("   ✗ FALHOU - Exceção: " + e.getMessage() + "\n");
        }
    }
}