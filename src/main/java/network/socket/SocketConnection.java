package network.socket;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class SocketConnection implements AutoCloseable {
    private final Socket socket;
    private final ObjectInputStream inputStream;
    private final ObjectOutputStream outputStream;
    private final BlockingQueue<Object> messageQueue;
    private final AtomicBoolean running;
    private final Thread receiverThread;
    private volatile MessageHandler messageHandler;

    public SocketConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.outputStream = new ObjectOutputStream(socket.getOutputStream());
        this.outputStream.flush();
        this.inputStream = new ObjectInputStream(socket.getInputStream());
        this.messageQueue = new LinkedBlockingQueue<>();
        this.running = new AtomicBoolean(true);
        this.receiverThread = new Thread(this::receiveMessages);
        this.receiverThread.setDaemon(true);
        this.receiverThread.start();
    }

    public void setMessageHandler(MessageHandler handler) {
        this.messageHandler = handler;
    }

    public void send(Object message) throws IOException {
        synchronized (outputStream) {
            outputStream.writeObject(message);
            outputStream.flush();
        }
    }

    private void receiveMessages() {
        try {
            while (running.get() && !socket.isClosed()) {
                Object message = inputStream.readObject();
                if (messageHandler != null) {
                    messageHandler.onMessage(message, this);
                } else {
                    messageQueue.offer(message);
                }
            }
        } catch (SocketException e) {
            if (running.get()) {
                handleError(e);
            }
        } catch (EOFException e) {
            handleDisconnect();
        } catch (IOException | ClassNotFoundException e) {
            handleError(e);
        } finally {
            closeQuietly();
        }
    }

    public Object receiveBlocking() throws InterruptedException {
        return messageQueue.take();
    }

    public String getRemoteAddress() {
        return socket.getInetAddress().getHostAddress();
    }

    public int getRemotePort() {
        return socket.getPort();
    }

    public boolean isConnected() {
        return socket.isConnected() && !socket.isClosed() && running.get();
    }

    private void handleError(Exception e) {
        if (messageHandler != null) {
            messageHandler.onError(e, this);
        }
    }

    private void handleDisconnect() {
        if (messageHandler != null) {
            messageHandler.onDisconnect(this);
        }
    }

    private void closeQuietly() {
        running.set(false);
        try {
            if (inputStream != null) inputStream.close();
        } catch (IOException ignored) {}
        try {
            if (outputStream != null) outputStream.close();
        } catch (IOException ignored) {}
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
    }

    @Override
    public void close() {
        closeQuietly();
        if (receiverThread != null && receiverThread.isAlive()) {
            receiverThread.interrupt();
        }
    }

    public interface MessageHandler {
        void onMessage(Object message, SocketConnection connection);
        void onError(Exception e, SocketConnection connection);
        void onDisconnect(SocketConnection connection);
    }
}
