package SD.testes;

import client.ClienteMiddleware;
import client.stub.StubFactory;
import common.dto.*;
import common.interfaces.*;
import server.Server;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Testes de integração do sistema completo
 * Cliente -> Middleware -> Servidor
 */
public class IntegrationTest {
    private static final String HOST = "localhost";
    private static final int PORT = 5555;
    private static Server servidor;
    private static Thread serverThread;
    
    public static void main(String[] args) {
        System.out.println("=== TESTES DE INTEGRAÇÃO ===\n");
        
        try {
            iniciarServidor();
            
            testeAutenticacaoBasica();
            testeRegistroEventos();
            testeAgregacoes();
            testeClientesConcorrentes();
            testeConnectionPoolVsDedicado();
            testeNotificacoes();
            
            System.out.println("\n=== TODOS OS TESTES CONCLUÍDOS ===");
            
        } catch (Exception e) {
            System.err.println("Erro ao executar testes: " + e.getMessage());
            e.printStackTrace();
        } finally {
            pararServidor();
        }
    }
    
    private static void iniciarServidor() {
        servidor = new Server(PORT, 30, 5);
        
        serverThread = new Thread(() -> {
            servidor.iniciar();
        }, "TestServer");
        
        serverThread.start();
        
        // Aguardar servidor ficar pronto
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("Servidor de teste iniciado\n");
    }
    
    private static void pararServidor() {
        if (servidor != null) {
            servidor.parar();
        }
        
        if (serverThread != null) {
            try {
                serverThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        System.out.println("\nServidor de teste parado");
    }
    
    private static void testeAutenticacaoBasica() {
        System.out.println("1. Teste Autenticação - Registro e Login");
        
        try {
            ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT);
            middleware.conectar();
            
            StubFactory stubs = new StubFactory(middleware);
            IServicoAutenticacao servicoAuth = stubs.criarStubAutenticacao();
            
            // Registrar usuário
            UsuarioDTO usuario = new UsuarioDTO("testuser1", "password123");
            RespostaDTO resposta = servicoAuth.registrar(usuario);
            
            if (!resposta.isSucesso()) {
                System.out.println("   ✗ FALHOU - Registro: " + resposta.getMensagem() + "\n");
                middleware.desconectar();
                return;
            }
            
            // Autenticar
            resposta = servicoAuth.autenticar(usuario);
            
            if (resposta.isSucesso()) {
                System.out.println("   ✓ PASSOU - Registro e login funcionaram\n");
            } else {
                System.out.println("   ✗ FALHOU - Login: " + resposta.getMensagem() + "\n");
            }
            
            middleware.desconectar();
            
        } catch (Exception e) {
            System.out.println("   ✗ FALHOU - Exceção: " + e.getMessage() + "\n");
        }
    }
    
    private static void testeRegistroEventos() {
        System.out.println("2. Teste Registro Eventos - Criar eventos");
        
        try {
            ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT);
            middleware.conectar();
            
            StubFactory stubs = new StubFactory(middleware);
            IServicoAutenticacao servicoAuth = stubs.criarStubAutenticacao();
            IServicoEventos servicoEventos = stubs.criarStubEventos();
            
            // Autenticar
            UsuarioDTO usuario = new UsuarioDTO("testuser2", "password123");
            servicoAuth.registrar(usuario);
            servicoAuth.autenticar(usuario);
            
            // Registrar 5 eventos
            int sucesso = 0;
            for (int i = 0; i < 5; i++) {
                EventoDTO evento = new EventoDTO(1, 10, 50.0 + i);
                RespostaDTO resposta = servicoEventos.registrarEvento(evento);
                
                if (resposta.isSucesso()) {
                    sucesso++;
                }
            }
            
            if (sucesso == 5) {
                System.out.println("   ✓ PASSOU - 5 eventos registrados\n");
            } else {
                System.out.println("   ✗ FALHOU - Esperado 5, obteve " + sucesso + "\n");
            }
            
            middleware.desconectar();
            
        } catch (Exception e) {
            System.out.println("   ✗ FALHOU - Exceção: " + e.getMessage() + "\n");
        }
    }
    
    private static void testeAgregacoes() {
        System.out.println("3. Teste Agregações - Consultar dados");
        
        try {
            ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT);
            middleware.conectar();
            
            StubFactory stubs = new StubFactory(middleware);
            IServicoAutenticacao servicoAuth = stubs.criarStubAutenticacao();
            IServicoEventos servicoEventos = stubs.criarStubEventos();
            IServicoAgregacoes servicoAgregacoes = stubs.criarStubAgregacoes();
            
            // Autenticar
            UsuarioDTO usuario = new UsuarioDTO("testuser3", "password123");
            servicoAuth.registrar(usuario);
            servicoAuth.autenticar(usuario);
            
            // Registrar eventos
            for (int i = 0; i < 3; i++) {
                EventoDTO evento = new EventoDTO(2, 5, 100.0);
                servicoEventos.registrarEvento(evento);
            }
            
            // Avançar dia
            servicoEventos.novoDia();
            
            // Consultar quantidade
            RespostaDTO resposta = servicoAgregacoes.obterQuantidadeVendas(2, 1);
            
            if (resposta.isSucesso()) {
                Object dados = resposta.getDados();
                if (dados instanceof Integer && (Integer)dados == 15) {
                    System.out.println("   ✓ PASSOU - Agregação correta (15 unidades)\n");
                } else {
                    System.out.println("   ✗ FALHOU - Valor esperado 15, obteve " + dados + "\n");
                }
            } else {
                System.out.println("   ✗ FALHOU - Erro na consulta: " + resposta.getMensagem() + "\n");
            }
            
            middleware.desconectar();
            
        } catch (Exception e) {
            System.out.println("   ✗ FALHOU - Exceção: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }
    
    private static void testeClientesConcorrentes() {
        System.out.println("4. Teste Clientes Concorrentes - 10 clientes simultâneos");
        
        AtomicInteger sucesso = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(10);
        
        for (int i = 0; i < 10; i++) {
            final int clienteId = i;
            
            new Thread(() -> {
                try {
                    ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT);
                    middleware.conectar();
                    
                    StubFactory stubs = new StubFactory(middleware);
                    IServicoAutenticacao servicoAuth = stubs.criarStubAutenticacao();
                    IServicoEventos servicoEventos = stubs.criarStubEventos();
                    
                    // Registrar e autenticar
                    UsuarioDTO usuario = new UsuarioDTO("concurrent" + clienteId, "pass" + clienteId);
                    servicoAuth.registrar(usuario);
                    RespostaDTO auth = servicoAuth.autenticar(usuario);
                    
                    if (!auth.isSucesso()) {
                        middleware.desconectar();
                        latch.countDown();
                        return;
                    }
                    
                    // Registrar eventos
                    for (int j = 0; j < 3; j++) {
                        EventoDTO evento = new EventoDTO(clienteId, 1, 10.0);
                        servicoEventos.registrarEvento(evento);
                    }
                    
                    sucesso.incrementAndGet();
                    middleware.desconectar();
                    
                } catch (Exception e) {
                    System.err.println("Cliente " + clienteId + " falhou: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            }, "Client-" + i).start();
        }
        
        try {
            if (latch.await(15, TimeUnit.SECONDS)) {
                if (sucesso.get() == 10) {
                    System.out.println("   ✓ PASSOU - 10 clientes concorrentes\n");
                } else {
                    System.out.println("   ✗ FALHOU - Esperado 10, obteve " + sucesso.get() + "\n");
                }
            } else {
                System.out.println("   ✗ Timeout - Obteve " + sucesso.get() + "/10\n");
            }
        } catch (InterruptedException e) {
            System.out.println("   ✗ Teste interrompido\n");
        }
    }
    
    private static void testeConnectionPoolVsDedicado() {
        System.out.println("5. Teste Performance - ConnectionPool vs Dedicado");
        
        try {
            // Teste com conexão dedicada
            long tempoDedicado = testarPerformance(false, 50);
            
            // Teste com pool
            long tempoPool = testarPerformance(true, 50);
            
            System.out.println("   Tempo conexão dedicada: " + tempoDedicado + "ms");
            System.out.println("   Tempo com pool: " + tempoPool + "ms");
            
            if (tempoPool <= tempoDedicado * 1.5) { // Pool deve ser comparável ou melhor
                System.out.println("   ✓ PASSOU - Pool tem performance aceitável\n");
            } else {
                System.out.println("   ⚠ AVISO - Pool mais lento que esperado\n");
            }
            
        } catch (Exception e) {
            System.out.println("   ✗ FALHOU - Exceção: " + e.getMessage() + "\n");
        }
    }
    
    private static long testarPerformance(boolean usePool, int numOperacoes) throws Exception {
        ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT, usePool, 10);
        middleware.conectar();
        
        StubFactory stubs = new StubFactory(middleware);
        IServicoAutenticacao servicoAuth = stubs.criarStubAutenticacao();
        IServicoEventos servicoEventos = stubs.criarStubEventos();
        
        // Autenticar
        String username = "perftest" + System.currentTimeMillis();
        UsuarioDTO usuario = new UsuarioDTO(username, "pass123");
        servicoAuth.registrar(usuario);
        servicoAuth.autenticar(usuario);
        
        // Medir tempo
        long inicio = System.currentTimeMillis();
        
        for (int i = 0; i < numOperacoes; i++) {
            EventoDTO evento = new EventoDTO(1, 1, 10.0);
            servicoEventos.registrarEvento(evento);
        }
        
        long fim = System.currentTimeMillis();
        
        middleware.desconectar();
        
        return fim - inicio;
    }
    
    private static void testeNotificacoes() {
        System.out.println("6. Teste Notificações - Venda específica");
        
        try {
            ClienteMiddleware middleware = new ClienteMiddleware(HOST, PORT);
            middleware.conectar();
            
            StubFactory stubs = new StubFactory(middleware);
            IServicoAutenticacao servicoAuth = stubs.criarStubAutenticacao();
            IServicoEventos servicoEventos = stubs.criarStubEventos();
            
            // Autenticar
            UsuarioDTO usuario = new UsuarioDTO("notiftest", "pass123");
            servicoAuth.registrar(usuario);
            servicoAuth.autenticar(usuario);
            
            // Thread que aguarda notificação
            AtomicInteger notificado = new AtomicInteger(0);
            Thread notificationThread = new Thread(() -> {
                try {
                    NotificacaoDTO notif = new NotificacaoDTO(10, 11);
                    RespostaDTO resp = servicoEventos.notificarVendaEspecifica(notif);
                    
                    if (resp.isSucesso()) {
                        notificado.set(1);
                    }
                } catch (Exception e) {
                    System.err.println("Erro na notificação: " + e.getMessage());
                }
            });
            
            notificationThread.start();
            Thread.sleep(500);
            
            // Registrar vendas
            servicoEventos.registrarEvento(new EventoDTO(10, 1, 100.0));
            Thread.sleep(200);
            servicoEventos.registrarEvento(new EventoDTO(11, 1, 100.0));
            
            // Aguardar notificação
            notificationThread.join(5000);
            
            if (notificado.get() == 1) {
                System.out.println("   ✓ PASSOU - Notificação funcionou\n");
            } else {
                System.out.println("   ✗ FALHOU - Notificação não recebida\n");
            }
            
            middleware.desconectar();
            
        } catch (Exception e) {
            System.out.println("   ✗ FALHOU - Exceção: " + e.getMessage() + "\n");
        }
    }
}