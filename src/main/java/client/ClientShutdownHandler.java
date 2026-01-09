package client;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Shutdown é detectado por tag == -1 no Demultiplexer.
 */
public class ClientShutdownHandler {
    private final ReentrantLock lock = new ReentrantLock();
    private boolean serverShutdown = false;
    private final Runnable onShutdownCallback;

    public ClientShutdownHandler(Runnable onShutdownCallback) {
        this.onShutdownCallback = onShutdownCallback;
    }

    public void onShutdown() {
        lock.lock();
        try {
            serverShutdown = true;
        } finally {
            lock.unlock();
        }
        if (onShutdownCallback != null) onShutdownCallback.run();
    }

    public boolean isServerShutdown() {
        lock.lock();
        try {
            return serverShutdown;
        } finally {
            lock.unlock();
        }
    }
}
