package client;

/**
 * Shutdown é detectado por tag == -1 no Demultiplexer.
 */
public class ClientShutdownHandler {
    private volatile boolean serverShutdown = false;
    private final Runnable onShutdownCallback;

    public ClientShutdownHandler(Runnable onShutdownCallback) {
        this.onShutdownCallback = onShutdownCallback;
    }

    public void onShutdown() {
        serverShutdown = true;
        if (onShutdownCallback != null) onShutdownCallback.run();
    }

    public boolean isServerShutdown() {
        return serverShutdown;
    }
}