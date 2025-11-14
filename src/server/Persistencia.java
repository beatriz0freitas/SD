package src.server;

import java.io.File;

//Ler/escrever ficheiros de vendas do disco
public class Persistencia {
    

    private String pastaBase;

    public Persistencia(String pastaBase) {
        this.pastaBase = pastaBase;
        File dir = new File(pastaBase);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
}
