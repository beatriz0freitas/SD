package src.servidor;

/**
 * Ponto de entrada do servidor. Inicializa o servidor na porta especificada (padrão 5000)
 */
public class Servidor {
    public static void main(String[] args) {
        int porta = 5000; // Porta padrão
        int D = 30;       // Dias anteriores (padrão)
        int S = 5;        // Séries em memória (padrão)
        
        // Uso: java Servidor [porta] [D] [S]
        if (args.length > 0) {
            try {
                porta = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Porta inválida, usando padrão: " + porta);
            }
        }
        
        if (args.length > 1) {
            try {
                D = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("D inválido, usando padrão: " + D);
            }
        }
        
        if (args.length > 2) {
            try {
                S = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                System.err.println("S inválido, usando padrão: " + S);
            }
        }
        
        // Validar S < D
        if (S >= D) {
            System.err.println("ERRO: S deve ser menor que D!");
            System.err.println("Ajustando S para " + (D / 2));
            S = Math.max(1, D / 2);
        }
        
        ServidorLogica servidor = new ServidorLogica(porta, D, S);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            servidor.shutdown();
        }));
        servidor.iniciar();
    }
}