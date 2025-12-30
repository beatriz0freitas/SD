package client;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import middleware.Message;
import middleware.Protocolo;

/**
 * Recebe respostas do servidor e acorda a thread que fez o pedido
 */
public class Demultiplexer implements Runnable {
    private final DataInputStream entrada;
    private final Protocolo protocolo;
    private final ReentrantLock lock;
    private final Map<Long, Object> respostas;
    private final Map<Long, Condition> threadsEspera;
    
    private volatile boolean ativo;
    private volatile Exception erro;
    
    public Demultiplexer(DataInputStream entrada) {
        this.entrada = entrada;
        this.protocolo = new Protocolo();
        this.lock = new ReentrantLock();
        this.respostas = new HashMap<>();
        this.threadsEspera = new HashMap<>();
        this.ativo = true;
        this.erro = null;
    }
    
    @Override
    public void run() {
        try {
            while (ativo) {
                Message msg = (Message) protocolo.receber(entrada);
                
                if (msg.isResponse()) {
                    entregarResposta(msg.getTag(), msg.getPayload());
                } else {
                    System.err.println("Demux recebeu request (inesperado): " + msg);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            tratarErroConexao(e);
        }
    }
    
    public Object aguardar(long tag) throws Exception {
        lock.lock();
        try {
            verificarErro();
            
            Condition condicao = lock.newCondition();
            threadsEspera.put(tag, condicao);
            
            while (!respostas.containsKey(tag) && erro == null) {
                condicao.await();
            }
            
            verificarErro();
            
            threadsEspera.remove(tag);
            return respostas.remove(tag);
            
        } finally {
            lock.unlock();
        }
    }
    
    public void parar() {
        ativo = false;
        acordarTodasThreads();
    }
    
    private void entregarResposta(long tag, Object resposta) {
        lock.lock();
        try {
            respostas.put(tag, resposta);
            Condition condicao = threadsEspera.get(tag);
            if (condicao != null) {
                condicao.signal();
            }
        } finally {
            lock.unlock();
        }
    }
    
    private void tratarErroConexao(Exception e) {
        lock.lock();
        try {
            erro = new IOException("Conexão perdida: " + e.getMessage(), e);
            acordarTodasThreads();
        } finally {
            lock.unlock();
        }
    }
    
    private void acordarTodasThreads() {
        lock.lock();
        try {
            for (Condition condicao : threadsEspera.values()) {
                condicao.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }
    
    private void verificarErro() throws Exception {
        if (erro != null) {
            throw erro;
        }
    }
}
