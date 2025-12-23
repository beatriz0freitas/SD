package client;

import client.stub.*;
import common.dto.*;
import common.interfaces.*;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ClienteTeste {
    private static final Random rand = new Random();
    private static final AtomicInteger eventos = new AtomicInteger(0);
    private static final AtomicInteger consultas = new AtomicInteger(0);
    
    public static void main(String[] args) {
        int numClientes = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        String host = args.length > 1 ? args[1] : "localhost";
        int porta = args.length > 2 ? Integer.parseInt(args[2]) : 5001;
        
        System.out.println("=== TESTE CONCORRENTE ===");
        System.out.println("Clientes: " + numClientes);
        System.out.println("Servidor: " + host + ":" + porta);
        System.out.println("========================\n");
        
        ExecutorService pool = Executors.newFixedThreadPool(numClientes);
        
        for (int i = 1; i <= numClientes; i++) {
            final int id = i;
            pool.execute(() -> executarCliente(id, host, porta));
        }
        
        pool.shutdown();
        try {
            pool.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            pool.shutdownNow();
        }
        
        System.out.println("\n=== RESULTADO ===");
        System.out.println("Eventos: " + eventos.get());
        System.out.println("Consultas: " + consultas.get());
    }
    
    private static void executarCliente(int id, String host, int porta) {
        ClienteMiddleware mid = null;
        try {
            System.out.println("[" + id + "] Iniciando...");
            
            mid = new ClienteMiddleware(host, porta);
            mid.conectar();
            
            StubFactory stubs = new StubFactory(mid);
            IServicoAutenticacao auth = stubs.criarStubAutenticacao();
            IServicoEventos evts = stubs.criarStubEventos();
            IServicoAgregacoes agreg = stubs.criarStubAgregacoes();
            
            // Autenticar
            String user = "user" + id;
            UsuarioDTO usuario = new UsuarioDTO(user, "pass" + id);
            
            try {
                auth.registrar(usuario);
            } catch (Exception e) {}
            
            RespostaDTO login = auth.autenticar(usuario);
            if (!login.isSucesso()) {
                System.err.println("[" + id + "] Login falhou");
                return;
            }
            
            System.out.println("[" + id + "] Autenticado");
            
            // Simular operações
            int tipo = id % 3;
            if (tipo == 0) {
                vendedor(id, evts);
            } else if (tipo == 1) {
                consultor(id, evts, agreg);
            } else {
                misto(id, evts, agreg);
            }
            
            System.out.println("[" + id + "] Concluído");
            
        } catch (Exception e) {
            System.err.println("[" + id + "] ERRO: " + e.getMessage());
        } finally {
            if (mid != null) mid.desconectar();
        }
    }
    
    private static void vendedor(int id, IServicoEventos evts) throws Exception {
        for (int i = 0; i < 5; i++) {
            EventoDTO evt = new EventoDTO(
                rand.nextInt(10) + 1,
                rand.nextInt(50) + 1,
                10 + rand.nextDouble() * 90
            );
            
            if (evts.registrarEvento(evt).isSucesso()) {
                eventos.incrementAndGet();
            }
            
            Thread.sleep(rand.nextInt(300));
        }
    }
    
    private static void consultor(int id, IServicoEventos evts, IServicoAgregacoes agreg) throws Exception {
        for (int i = 0; i < 5; i++) {
            int op = rand.nextInt(3);
            int prod = rand.nextInt(10) + 1;
            int dias = rand.nextInt(7) + 1;
            
            RespostaDTO resp = null;
            if (op == 0) {
                resp = evts.listarEventosDiaAtual();
            } else if (op == 1) {
                resp = agreg.obterQuantidadeVendas(prod, dias);
            } else {
                resp = agreg.obterVolumeVendas(prod, dias);
            }
            
            if (resp != null && resp.isSucesso()) {
                consultas.incrementAndGet();
            }
            
            Thread.sleep(rand.nextInt(500));
        }
    }
    
    private static void misto(int id, IServicoEventos evts, IServicoAgregacoes agreg) throws Exception {
        // 3 eventos
        for (int i = 0; i < 3; i++) {
            EventoDTO evt = new EventoDTO(
                rand.nextInt(10) + 1,
                rand.nextInt(30) + 1,
                15 + rand.nextDouble() * 85
            );
            if (evts.registrarEvento(evt).isSucesso()) {
                eventos.incrementAndGet();
            }
            Thread.sleep(rand.nextInt(200));
        }
        
        // 3 consultas
        for (int i = 0; i < 3; i++) {
            RespostaDTO resp = agreg.obterQuantidadeVendas(
                rand.nextInt(10) + 1,
                rand.nextInt(5) + 1
            );
            if (resp.isSucesso()) {
                consultas.incrementAndGet();
            }
            Thread.sleep(rand.nextInt(300));
        }
    }
}