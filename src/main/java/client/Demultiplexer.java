package client;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import middleware.Message;
import middleware.Protocolo;

public class Demultiplexer implements Runnable {
    private final DataInputStream entrada;
    private final Protocolo protocolo;
    private final ReentrantLock lock;
    private final Map<Long, byte[]> respostas;
    private final Map<Long, Condition> threadsEspera;

    private boolean ativo;
    private Exception erro;
    private boolean serverShutdown;

    private final ClientShutdownHandler shutdownHandler;

    public Demultiplexer(DataInputStream entrada, ClientShutdownHandler shutdownHandler) {
        this.entrada = entrada;
        this.protocolo = new Protocolo();
        this.lock = new ReentrantLock();
        this.respostas = new HashMap<>();
        this.threadsEspera = new HashMap<>();
        this.ativo = true;
        this.serverShutdown = false;
        this.shutdownHandler = shutdownHandler;
    }

    @Override
    public void run() {
        try {
            while (shouldRun()) {
                Message msg = protocolo.receber(entrada);

                if (!msg.isResponse()) {
                    System.err.println("Demux recebeu request (inesperado): " + msg);
                    continue;
                }

                if (msg.getTag() == -1) {
                    setServerShutdown(true);
                    if (shutdownHandler != null) shutdownHandler.onShutdown();
                    acordarTodasThreads();
                    break;
                }

                entregarResposta(msg.getTag(), msg.getPayload());
            }
        } catch (IOException e) {
            if (shouldRun()) tratarErroConexao(e);
        }
    }

    public void parar() {
        lock.lock();
        try {
            ativo = false;
            acordarTodasThreads();
        } finally {
            lock.unlock();
        }
    }

    public byte[] aguardar(long tag) throws Exception {
        lock.lock();
        try {
            verificarErro();
            if (serverShutdown) throw new IOException("Servidor encerrado");
            if (!ativo) throw new IOException("Demultiplexer parado");

            Condition condicao = lock.newCondition();
            threadsEspera.put(tag, condicao);

            while (!respostas.containsKey(tag) && erro == null && !serverShutdown && ativo) {
                condicao.await();
            }

            if (!ativo) throw new IOException("Demultiplexer parado durante espera");
            if (serverShutdown) throw new IOException("Servidor encerrado durante espera");
            verificarErro();

            threadsEspera.remove(tag);
            return respostas.remove(tag);
        } finally {
            lock.unlock();
        }
    }

    private void entregarResposta(long tag, byte[] respostaBytes) {
        lock.lock();
        try {
            respostas.put(tag, respostaBytes);
            Condition condicao = threadsEspera.get(tag);
            if (condicao != null) condicao.signal();
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
            for (Condition condicao : threadsEspera.values()) condicao.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void verificarErro() throws Exception {
        if (erro != null) throw erro;
    }

    private boolean shouldRun() {
        lock.lock();
        try {
            return ativo && !serverShutdown;
        } finally {
            lock.unlock();
        }
    }

    private void setServerShutdown(boolean value) {
        lock.lock();
        try {
            serverShutdown = value;
        } finally {
            lock.unlock();
        }
    }
}
