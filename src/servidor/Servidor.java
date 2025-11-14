package src.servidor;

/**
 * Ponto de entrada do servidor. Inicializa o servidor na porta especificada (padrão 5000)
 */
public class Servidor {
    public static void main(String[] args) {
        int porta = 5000; // Porta padrão
        if (args.length > 0) {
            try {
                porta = Integer.parseInt(args[0]);          // Permitir especificar porta por argumento
            } catch (NumberFormatException e) {
                System.err.println("Porta inválida, usando padrão: " + porta);
            }
        }
        
        ServidorLogica servidor = new ServidorLogica(porta);
        // Adiciona um shutdown hook (quando o programa fechar corre automaticamente servidor.shutdown())
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            servidor.shutdown();
        }));
        servidor.iniciar();
    }
}