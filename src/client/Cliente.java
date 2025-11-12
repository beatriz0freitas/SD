package src.client;

/**
 * Classe principal do cliente
 */
public class Cliente {
    
    public static void main(String[] args) {
        String host = "localhost";
        int porta = 5000;
        
        // Permitir especificar host e porta por argumentos
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
        
        // Criar e iniciar interface do utilizador
        InterfaceUtilizador ui = new InterfaceUtilizador(host, porta);
        ui.iniciar();
    }
}