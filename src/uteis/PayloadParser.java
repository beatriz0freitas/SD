package src.uteis;

import java.io.IOException;

/**
 * Métodos auxiliares para extrair dados de payloads.
 * Mantidos separados para não poluir a classe Mensagem.
 */
public class PayloadParser {
    
    /**
     * Extrai credenciais de payload de autenticação
     */
    public static String[] lerAutenticacao(byte[] payload) throws IOException {
        return Protocolo.deserializar(payload, in -> {
            String username = Protocolo.lerString(in);
            String password = Protocolo.lerString(in);
            if (username == null || password == null) {
                throw new IOException("Credenciais nulas");
            }
            return new String[]{username, password};
        });
    }
    
    /**
     * Extrai mensagem de texto de payload de resposta
     */
    public static String lerResposta(byte[] payload) throws IOException {
        return Protocolo.deserializar(payload, Protocolo::lerString);
    }
    
    /**
     * Extrai evento de payload
     */
    public static Evento lerEvento(byte[] payload) throws IOException {
        return Protocolo.deserializar(payload, in -> 
            new Evento(in.readInt(), in.readInt(), in.readDouble())
        );
    }

    // --- Admin ---
    public static String lerPasswordAdmin(byte[] payload) throws IOException {
        return Protocolo.deserializar(payload, Protocolo::lerString);
    }
    
    public static String lerListaClientes(byte[] payload) throws IOException {
        return Protocolo.deserializar(payload, Protocolo::lerString);
    }
    
    public static String lerListaEventos(byte[] payload) throws IOException {
        return Protocolo.deserializar(payload, Protocolo::lerString);
    }
}