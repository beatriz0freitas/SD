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
 * Recebe respostas do servidor e acorda a thread que fez o pedido
 */
public class Demultiplexer implements Runnable {
    private final DataInputStream entrada;
    private final ProtocoloHandler protocolo;
    private final ReentrantLock lock;
    private final Map<Long, Object> respostas;
    private final Map<Long, Condition> threadsEspera;
    
    private volatile boolean ativo;
    private volatile Exception erro;
    
    public Demultiplexer(DataInputStream entrada) {
        this.entrada = entrada;
        this.protocolo = new ProtocoloHandler();
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
                TaggedResponse resp = (TaggedResponse) protocolo.receber(entrada);
                entregarResposta(resp.getTag(), resp.getResposta());
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