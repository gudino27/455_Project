package network.lan;

import network.protocol.Message;
import network.socket.SocketConnection;
import network.socket.SocketServer;

import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

public class LANManager implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(LANManager.class.getName());

    private final String peerId;
    private final int port;
    private final SocketServer server;
    private final LANDiscovery discovery;
    private final Map<String, SocketConnection> connections;
    private final Map<String, PeerInfo> discoveredPeers;
    private final CopyOnWriteArrayList<MessageListener> messageListeners;

    public LANManager(String peerId, int port) throws IOException {
        this.peerId = peerId;
        this.port = port;
        this.connections = new ConcurrentHashMap<>();
        this.discoveredPeers = new ConcurrentHashMap<>();
        this.messageListeners = new CopyOnWriteArrayList<>();

        this.server = new SocketServer(port);
        this.server.setConnectionHandler(this::handleIncomingConnection);

        this.discovery = new LANDiscovery(peerId, port);
        this.discovery.addListener(this::handlePeerDiscovered);
    }

    public void start() {
        server.start();
        discovery.start();
        logger.info("LAN Manager started for peer " + peerId + " on port " + port);
    }

    public void addMessageListener(MessageListener listener) {
        messageListeners.add(listener);
    }

    public void removeMessageListener(MessageListener listener) {
        messageListeners.remove(listener);
    }

    private void handleIncomingConnection(SocketConnection connection) {
        connection.setMessageHandler(new SocketConnection.MessageHandler() {
            @Override
            public void onMessage(Object message, SocketConnection conn) {
                if (message instanceof Message) {
                    Message msg = (Message) message;
                    if (msg.getType() == Message.MessageType.HANDSHAKE) {
                        String remotePeerId = msg.getSenderId();
                        connections.put(remotePeerId, conn);
                        logger.info("Handshake received from peer " + remotePeerId);

                        sendHandshakeResponse(conn);
                    } else {
                        notifyMessageReceived((Message) message);
                    }
                }
            }

            @Override
            public void onError(Exception e, SocketConnection conn) {
                logger.warning("Connection error: " + e.getMessage());
            }

            @Override
            public void onDisconnect(SocketConnection conn) {
                String disconnectedPeer = findPeerByConnection(conn);
                if (disconnectedPeer != null) {
                    connections.remove(disconnectedPeer);
                    logger.info("Peer disconnected: " + disconnectedPeer);
                }
            }
        });
    }

    private void handlePeerDiscovered(PeerInfo peerInfo) {
        discoveredPeers.put(peerInfo.getPeerId(), peerInfo);
        logger.info("Discovered peer: " + peerInfo);

        if (!connections.containsKey(peerInfo.getPeerId())) {
            connectToPeer(peerInfo);
        }
    }

    private void connectToPeer(PeerInfo peerInfo) {
        try {
            Socket socket = new Socket(peerInfo.getAddress(), peerInfo.getPort());
            SocketConnection connection = new SocketConnection(socket);

            connection.setMessageHandler(new SocketConnection.MessageHandler() {
                @Override
                public void onMessage(Object message, SocketConnection conn) {
                    if (message instanceof Message) {
                        notifyMessageReceived((Message) message);
                    }
                }

                @Override
                public void onError(Exception e, SocketConnection conn) {
                    logger.warning("Connection error with " + peerInfo.getPeerId() + ": " + e.getMessage());
                }

                @Override
                public void onDisconnect(SocketConnection conn) {
                    connections.remove(peerInfo.getPeerId());
                    logger.info("Disconnected from peer: " + peerInfo.getPeerId());
                }
            });

            sendHandshake(connection);
            connections.put(peerInfo.getPeerId(), connection);
            logger.info("Connected to peer: " + peerInfo.getPeerId());

        } catch (IOException e) {
            logger.warning("Failed to connect to peer " + peerInfo.getPeerId() + ": " + e.getMessage());
        }
    }

    private void sendHandshake(SocketConnection connection) {
        try {
            Message handshake = new Message(peerId, "HANDSHAKE", Message.MessageType.HANDSHAKE);
            connection.send(handshake);
        } catch (IOException e) {
            logger.warning("Failed to send handshake: " + e.getMessage());
        }
    }

    private void sendHandshakeResponse(SocketConnection connection) {
        try {
            Message response = new Message(peerId, "Hello", Message.MessageType.ACK);
            connection.send(response);
        } catch (IOException e) {
            logger.warning("Failed to send handshake response: " + e.getMessage());
        }
    }

    public void broadcast(String content) {
        Message message = new Message(peerId, content, Message.MessageType.TEXT);
        for (SocketConnection connection : connections.values()) {
            try {
                connection.send(message);
            } catch (IOException e) {
                logger.warning("Failed to broadcast message: " + e.getMessage());
            }
        }
    }

    public void sendTo(String targetPeerId, String content) throws IOException {
        SocketConnection connection = connections.get(targetPeerId);
        if (connection != null && connection.isConnected()) {
            Message message = new Message(peerId, content, Message.MessageType.TEXT);
            connection.send(message);
        } else {
            throw new IOException("Not connected to peer: " + targetPeerId);
        }
    }

    private void notifyMessageReceived(Message message) {
        for (MessageListener listener : messageListeners) {
            try {
                listener.onMessageReceived(message);
            } catch (Exception e) {
                logger.warning("Error notifying message listener: " + e.getMessage());
            }
        }
    }

    private String findPeerByConnection(SocketConnection connection) {
        for (Map.Entry<String, SocketConnection> entry : connections.entrySet()) {
            if (entry.getValue() == connection) {
                return entry.getKey();
            }
        }
        return null;
    }

    public int getConnectedPeerCount() {
        return connections.size();
    }

    public Map<String, PeerInfo> getDiscoveredPeers() {
        return Map.copyOf(discoveredPeers);
    }

    @Override
    public void close() {
        for (SocketConnection connection : connections.values()) {
            connection.close();
        }
        connections.clear();

        if (discovery != null) {
            discovery.close();
        }

        if (server != null) {
            server.close();
        }

        logger.info("LAN Manager stopped");
    }

    public interface MessageListener {
        void onMessageReceived(Message message);
    }
}
