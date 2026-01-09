package client.connection;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.locks.ReentrantLock;

public class PooledConnection implements AutoCloseable {
    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final ConnectionPool pool;

    private final ReentrantLock stateLock = new ReentrantLock();
    private boolean valid;
    private long lastUsed;

    public PooledConnection(String host, int porta, ConnectionPool pool) throws IOException {
        this.socket = new Socket(host, porta);
        this.input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        this.pool = pool;
        this.valid = true;
        this.lastUsed = System.currentTimeMillis();
    }

    public DataInputStream getInputStream() { updateLastUsed(); return input; }
    public DataOutputStream getOutputStream() { updateLastUsed(); return output; }

    
    public String getConnectionId() {
        String local = socket.getLocalSocketAddress() != null
                ? socket.getLocalSocketAddress().toString()
                : "unknown-local";
        String remote = socket.getRemoteSocketAddress() != null
                ? socket.getRemoteSocketAddress().toString()
                : "unknown-remote";
        return local + "->" + remote;
    }

    public boolean isValid() {
        stateLock.lock();
        try {
            if (!valid) return false;
            try {
                boolean ok = socket.isConnected()
                        && !socket.isClosed()
                        && !socket.isInputShutdown()
                        && !socket.isOutputShutdown();
                if (!ok) valid = false;
                return ok;
            } catch (Exception e) {
                valid = false;
                return false;
            }
        } finally {
            stateLock.unlock();
        }
    }

    public void invalidate() {
        stateLock.lock();
        try {
            valid = false;
        } finally {
            stateLock.unlock();
        }
    }

    @Override
    public void close() {
        if (pool != null) pool.releaseConnection(this);
        else closePhysical();
    }

    public void closePhysical() {
        stateLock.lock();
        try {
            valid = false;
        } finally {
            stateLock.unlock();
        }
        try { input.close(); } catch (IOException ignored) {}
        try { output.close(); } catch (IOException ignored) {}
        try { if (!socket.isClosed()) socket.close(); } catch (IOException ignored) {}
    }

    private void updateLastUsed() {
        stateLock.lock();
        try {
            lastUsed = System.currentTimeMillis();
        } finally {
            stateLock.unlock();
        }
    }
}
