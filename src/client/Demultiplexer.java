package client;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import middleware.proto.ProtocoloHandler;
import middleware.proto.TaggedResponse;

/**
 * Demultiplexer 
 * Recebe respostas e acorda a thread certa
 */
public class Demultiplexer implements Runnable {
    private final DataInputStream input;
    private final ProtocoloHandler proto = new ProtocoloHandler();
    
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<Long, Object> respostas = new HashMap<>();
    private final Map<Long, Condition> threads = new HashMap<>();
    
    private volatile boolean ativo = true;
    private volatile Exception erro = null;  
    
    public Demultiplexer(DataInputStream input) {
        this.input = input;
    }
    
    @Override
    public void run() {
        try {
            while (ativo) {
                // Recebe resposta
                Object obj = proto.receber(input);
                TaggedResponse resp = (TaggedResponse) obj;
                long tag = resp.getTag();
                
                // Guarda resposta e acorda thread
                lock.lock();
                try {
                    respostas.put(tag, resp.getResposta());
                    Condition c = threads.get(tag);
                    if (c != null) c.signal(); // Acorda a thread certa
                } finally {
                    lock.unlock();
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            lock.lock();
            try {
                erro = new IOException("Conexão perdida: " + e.getMessage(), e);
                // Acordar todas as threads que estão à espera
                for (Condition c : threads.values()) {
                    c.signalAll();
                }
            } finally {
                lock.unlock();
            }
        }
    }
    
    /**
     * Thread regista-se e aguarda resposta
     */
    public Object aguardar(long tag) throws Exception {
        lock.lock();
        try {
            if (erro != null) {
                throw erro;
            }
            
            // Criar condition para esta thread
            Condition c = lock.newCondition();
            threads.put(tag, c);
            
            // Aguardar até resposta chegar
            while (!respostas.containsKey(tag) && erro == null) {
                c.await(); 
            }
            
            if (erro != null) {
                threads.remove(tag);
                throw erro;
            }
            
            threads.remove(tag);
            return respostas.remove(tag);
            
        } finally {
            lock.unlock();
        }
    }
    
    public void parar() {
        ativo = false;
        lock.lock();
        try {
            for (Condition c : threads.values()) {
                c.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }
}