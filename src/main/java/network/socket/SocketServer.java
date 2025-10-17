package network.socket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class SocketServer implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(SocketServer.class.getName());

    private final int port;
    private final ServerSocket serverSocket;
    private final ExecutorService executorService;
    private final AtomicBoolean running;
    private final Thread acceptThread;
    private volatile ConnectionHandler connectionHandler;

    public SocketServer(int port) throws IOException {
        this.port = port;
        this.serverSocket = new ServerSocket(port);
        this.executorService = Executors.newCachedThreadPool();
        this.running = new AtomicBoolean(false);
        this.acceptThread = new Thread(this::acceptConnections);
        this.acceptThread.setDaemon(true);
        logger.info("Socket server created on port " + port);
    }

    public void setConnectionHandler(ConnectionHandler handler) {
        this.connectionHandler = handler;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            acceptThread.start();
            logger.info("Socket server started on port " + port);
        }
    }

    private void acceptConnections() {
        while (running.get() && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("Accepted connection from " + clientSocket.getInetAddress());

                executorService.submit(() -> handleConnection(clientSocket));
            } catch (SocketException e) {
                if (running.get()) {
                    logger.warning("Socket exception: " + e.getMessage());
                }
            } catch (IOException e) {
                logger.severe("Error accepting connection: " + e.getMessage());
            }
        }
    }

    private void handleConnection(Socket clientSocket) {
        try {
            SocketConnection connection = new SocketConnection(clientSocket);
            if (connectionHandler != null) {
                connectionHandler.onConnection(connection);
            } else {
                logger.warning("No connection handler set, closing connection");
                connection.close();
            }
        } catch (IOException e) {
            logger.severe("Error creating socket connection: " + e.getMessage());
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
        }
    }

    public int getPort() {
        return port;
    }

    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void close() {
        running.set(false);
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.warning("Error closing server socket: " + e.getMessage());
        }

        executorService.shutdown();

        if (acceptThread != null && acceptThread.isAlive()) {
            acceptThread.interrupt();
        }

        logger.info("Socket server stopped");
    }

    public interface ConnectionHandler {
        void onConnection(SocketConnection connection);
    }
}
