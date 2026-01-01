package client;

import client.stub.*;
import client.ui.InterfaceUtilizador;

public class Cliente {
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int porta = args.length > 1 ? parsePorta(args[1]) : 5001;
        
        ClienteMiddleware middleware = new ClienteMiddleware(host, porta);
        StubFactory stubFactory = new StubFactory(middleware);
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
    
    private static int parsePorta(String valor) {
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            System.err.println("Porta inválida, usando padrão: 5001");
            return 5001;
        }
    }
}