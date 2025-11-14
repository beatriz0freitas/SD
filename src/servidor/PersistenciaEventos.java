package src.servidor;

import java.io.File;

//Ler/escrever ficheiros de vendas do disco
public class PersistenciaEventos {
    
    private String pastaBase;

    public PersistenciaEventos(String pastaBase) {
        this.pastaBase = pastaBase;
        File dir = new File(pastaBase);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
}
