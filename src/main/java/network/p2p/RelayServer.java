package network.p2p;

import network.protocol.Message;
import network.socket.SocketConnection;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class RelayServer implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(RelayServer.class.getName());

    private int port;
    private final ExecutorService executor;
    private final ConcurrentHashMap<String, SocketConnection> registeredPeers;
    private ServerSocket serverSocket;
    private volatile boolean running;
    private Thread acceptThread;

    public RelayServer(int port) {
        this.port = port;
        this.executor = Executors.newCachedThreadPool();
        this.registeredPeers = new ConcurrentHashMap<>();
        this.running = false;
    }

    public void start() throws IOException {
        if (running) {
            logger.warning("RelayServer already running");
            return;
        }

        serverSocket = new ServerSocket(port);
        this.port = serverSocket.getLocalPort();
        running = true;

        acceptThread = new Thread(this::acceptConnections, "RelayServer-Accept");
        acceptThread.start();

        logger.info("RelayServer started on port " + this.port);
    }

    public void stop() {
        if (!running) {
            return;
        }

        running = false;

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.warning("Error closing server socket: " + e.getMessage());
        }

        for (SocketConnection connection : registeredPeers.values()) {
            try {
                connection.close();
            } catch (Exception e) {
                logger.fine("Error closing peer connection: " + e.getMessage());
            }
        }
        registeredPeers.clear();

        if (acceptThread != null) {
            acceptThread.interrupt();
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("RelayServer stopped");
    }

    private void acceptConnections() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                executor.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                if (running) {
                    logger.warning("Error accepting connection: " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        SocketConnection connection = null;
        String peerId = null;

        try {
            connection = new SocketConnection(socket);

            connection.setMessageHandler(new SocketConnection.MessageHandler() {
                @Override
                public void onMessage(Object msg, SocketConnection conn) {
                    if (msg instanceof Message) {
                        handleRelayMessage((Message) msg, conn);
                    }
                }

                @Override
                public void onError(Exception e, SocketConnection conn) {
                    logger.warning("Connection error: " + e.getMessage());
                }

                @Override
                public void onDisconnect(SocketConnection conn) {
                    String disconnectedPeerId = findPeerIdByConnection(conn);
                    if (disconnectedPeerId != null) {
                        registeredPeers.remove(disconnectedPeerId);
                        logger.info("Peer unregistered: " + disconnectedPeerId);
                    }
                }
            });

            Object registerObj = connection.receiveBlocking();
            if (registerObj instanceof Message) {
                Message registerMessage = (Message) registerObj;
                if (registerMessage.getType() == Message.MessageType.HANDSHAKE) {
                    peerId = registerMessage.getSenderId();
                    registeredPeers.put(peerId, connection);
                    logger.info("Peer registered: " + peerId + " from " + socket.getInetAddress());

                    Message ack = new Message("relay-server", "Registration successful", Message.MessageType.ACK);
                    connection.send(ack);

                    while (running && connection.isConnected()) {
                        Thread.sleep(1000);
                    }
                } else {
                    logger.warning("Invalid registration message type");
                    connection.close();
                }
            } else {
                logger.warning("Invalid registration message");
                connection.close();
            }

        } catch (InterruptedException e) {
            logger.fine("Client handler interrupted for peer: " + peerId);
        } catch (Exception e) {
            logger.warning("Error handling client: " + e.getMessage());
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ex) {
                    logger.fine("Error closing connection: " + ex.getMessage());
                }
            }
            if (peerId != null) {
                registeredPeers.remove(peerId);
                logger.info("Peer unregistered: " + peerId);
            }
        }
    }

    private void handleRelayMessage(Message message, SocketConnection senderConnection) {
        try {
            String recipientId = extractRecipientId(message);
            if (recipientId == null) {
                logger.warning("No recipient specified in relay message");
                return;
            }

            SocketConnection recipientConnection = registeredPeers.get(recipientId);
            if (recipientConnection != null) {
                recipientConnection.send(message);
                logger.fine("Relayed message from " + message.getSenderId() + " to " + recipientId);
            } else {
                logger.warning("Recipient not found: " + recipientId);
                Message errorMsg = new Message("relay-server",
                    "Error: Recipient " + recipientId + " not connected", Message.MessageType.TEXT);
                senderConnection.send(errorMsg);
            }
        } catch (Exception e) {
            logger.warning("Error relaying message: " + e.getMessage());
        }
    }

    private String extractRecipientId(Message message) {
        String content = message.getContent();
        if (content != null && content.startsWith("TO:")) {
            int endIndex = content.indexOf(':', 3);
            if (endIndex > 3) {
                return content.substring(3, endIndex);
            }
        }
        return null;
    }

    private String findPeerIdByConnection(SocketConnection connection) {
        for (ConcurrentHashMap.Entry<String, SocketConnection> entry : registeredPeers.entrySet()) {
            if (entry.getValue() == connection) {
                return entry.getKey();
            }
        }
        return null;
    }

    public int getRegisteredPeerCount() {
        return registeredPeers.size();
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    @Override
    public void close() {
        stop();
    }

    public static void main(String[] args) {
        int port = 9090;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default: " + port);
            }
        }

        RelayServer server = new RelayServer(port);
        try {
            server.start();
            System.out.println("RelayServer running on port " + port);
            System.out.println("Press Ctrl+C to stop");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down RelayServer...");
                server.stop();
            }));

            while (server.isRunning()) {
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            logger.severe("RelayServer error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            server.close();
        }
    }
}
