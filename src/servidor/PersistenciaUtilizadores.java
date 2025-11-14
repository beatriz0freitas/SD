package src.servidor;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Gere a persistência dos utilizadores em disco
 */
public class PersistenciaUtilizadores {
    private static final String FICHEIRO_USERS = "dados/utilizadores.dat";
    
    public PersistenciaUtilizadores() {
        // Criar diretório se não existir
        File dir = new File("dados");
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
    
    /**
     * Verifica se o ficheiro de utilizadores existe
     */
    public boolean existeFicheiro() {
        return new File(FICHEIRO_USERS).exists();
    }
    
    /**
     * Remove o ficheiro de utilizadores (útil para testes)
     */
    public void limpar() {
        if (existeFicheiro()) {
            new File(FICHEIRO_USERS).delete();
        }
    }
    
    /**
     * Carrega utilizadores do disco
     * @return Map com username -> password hash
     */
    public Map<String, String> carregarUtilizadores() throws IOException {
        Map<String, String> utilizadores = new HashMap<>();
        if (!existeFicheiro()) {
            return utilizadores; // Primeira execução, sem utilizadores
        }

        DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(new File(FICHEIRO_USERS))));

        try (in) {
            int numUsers = in.readInt();
            for (int i = 0; i < numUsers; i++) {
                String usernameCliente = in.readUTF();
                String passwordHash = in.readUTF();
                utilizadores.put(usernameCliente, passwordHash);
            }
            System.out.println("Carregados " + numUsers + " utilizadores do disco.");
        }
        return utilizadores;
    }
    
    /**
     * Guarda utilizadores em disco
     * @param utilizadores Map com username -> password hash
     */
    //todo: perceber se há necessidade de usar o temporario
    public void guardarUtilizadores(Map<String, String> utilizadores) throws IOException {
        File ficheiro = new File(FICHEIRO_USERS);
        File ficheiroTemp = new File(FICHEIRO_USERS + ".tmp");
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(ficheiroTemp)));

        // Guardar em ficheiro temporário primeiro
        try (out) {
            out.writeInt(utilizadores.size());
            for (Map.Entry<String, String> entry : utilizadores.entrySet()) {
                out.writeUTF(entry.getKey());
                out.writeUTF(entry.getValue());
            }
            out.flush();
        }
        
        // Substituir ficheiro original pelo temporário (operação atómica)
        if (ficheiro.exists()) {
            if (!ficheiro.delete()) {
                throw new IOException("Não foi possível remover ficheiro antigo");
            }
        }
        if (!ficheiroTemp.renameTo(ficheiro)) {
            throw new IOException("Não foi possível renomear ficheiro temporário");
        }
    }
    
}