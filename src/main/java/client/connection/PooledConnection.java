package client.connection;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class PooledConnection implements AutoCloseable {
    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final ConnectionPool pool;

    private volatile boolean valid;
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

    public boolean isValid() {
        if (!valid) return false;
        try {
            return socket.isConnected() && !socket.isClosed() && !socket.isInputShutdown() && !socket.isOutputShutdown();
        } catch (Exception e) {
            valid = false;
            return false;
        }
    }

    public void invalidate() { valid = false; }

    @Override
    public void close() {
        if (pool != null) pool.releaseConnection(this);
        else closePhysical(); // dedicado
    }

    public void closePhysical() {
        valid = false;
        try { input.close(); } catch (IOException ignored) {}
        try { output.close(); } catch (IOException ignored) {}
        try { if (!socket.isClosed()) socket.close(); } catch (IOException ignored) {}
    }

    private void updateLastUsed() { lastUsed = System.currentTimeMillis(); }
}