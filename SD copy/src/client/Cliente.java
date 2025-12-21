package client;

import client.Stub.*;
import client.ui.InterfaceUtilizador;

/**
 * Ponto de entrada do cliente
 */
public class Cliente {
    
    public static void main(String[] args) {
        String host = "localhost";
        int porta = 5001;
        
        // Processar argumentos
        if (args.length > 0) {
            host = args[0];
        }
        
        if (args.length > 1) {
            try {
                porta = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Porta inválida, usando padrão: " + porta);
            }
        }
        
        // 1. Criar middleware
        ClienteMiddleware middleware = new ClienteMiddleware(host, porta);
        
        // 2. Criar factory de proxies
        StubFactory stubFactory = new StubFactory(middleware);
        
        // 3. Criar UI e iniciar
        InterfaceUtilizador ui = new InterfaceUtilizador(middleware, stubFactory);
        
        try {
            middleware.conectar();
            ui.iniciar();
        } catch (Exception e) {
            System.err.println("Erro ao conectar: " + e.getMessage());
        } finally {
            middleware.desconectar();
        }
    }
}