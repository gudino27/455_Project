package network.p2p;

import network.lan.PeerInfo;
import network.protocol.Message;
import network.socket.SocketConnection;
import util.NetworkConfig;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class PeerConnectionManager implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(PeerConnectionManager.class.getName());

    private final NetworkConfig config;
    private final ConcurrentHashMap<String, PeerConnection> peerConnections;
    private final ScheduledExecutorService heartbeatExecutor;
    private final ScheduledExecutorService reconnectionExecutor;
    private ConnectionStateListener stateListener;

    public PeerConnectionManager(NetworkConfig config) {
        this.config = config;
        this.peerConnections = new ConcurrentHashMap<>();
        this.heartbeatExecutor = Executors.newScheduledThreadPool(1);
        this.reconnectionExecutor = Executors.newScheduledThreadPool(2);

        startHeartbeatMonitor();
    }

    public boolean connect(PeerInfo peerInfo, SocketConnection connection, ConnectionType type) {
        String peerId = peerInfo.getPeerId();

        PeerConnection existing = peerConnections.get(peerId);
        if (existing != null && existing.state == ConnectionState.CONNECTED) {
            logger.fine("Already connected to peer: " + peerId);
            return true;
        }

        PeerConnection peerConnection = new PeerConnection(peerInfo, connection, type);
        peerConnection.state = ConnectionState.CONNECTED;
        peerConnection.lastHeartbeat = System.currentTimeMillis();
        peerConnections.put(peerId, peerConnection);

        logger.info("Connected to peer: " + peerId + " (type: " + type + ")");
        notifyStateChange(peerId, ConnectionState.CONNECTED);
        return true;
    }

    public SocketConnection getConnection(String peerId) {
        PeerConnection peerConnection = peerConnections.get(peerId);
        if (peerConnection != null && peerConnection.state == ConnectionState.CONNECTED) {
            return peerConnection.connection;
        }
        return null;
    }

    public ConnectionState getConnectionState(String peerId) {
        PeerConnection peerConnection = peerConnections.get(peerId);
        return peerConnection != null ? peerConnection.state : ConnectionState.DISCONNECTED;
    }

    public void disconnect(String peerId) {
        PeerConnection peerConnection = peerConnections.remove(peerId);
        if (peerConnection != null) {
            peerConnection.state = ConnectionState.DISCONNECTED;
            try {
                if (peerConnection.connection != null) {
                    peerConnection.connection.close();
                }
            } catch (Exception e) {
                logger.fine("Error closing connection: " + e.getMessage());
            }
            logger.info("Disconnected from peer: " + peerId);
            notifyStateChange(peerId, ConnectionState.DISCONNECTED);
        }
    }

    public void recordHeartbeat(String peerId) {
        PeerConnection peerConnection = peerConnections.get(peerId);
        if (peerConnection != null) {
            peerConnection.lastHeartbeat = System.currentTimeMillis();
        }
    }

    public boolean sendHeartbeat(String peerId) {
        PeerConnection peerConnection = peerConnections.get(peerId);
        if (peerConnection == null || peerConnection.connection == null) {
            return false;
        }

        try {
            Message heartbeat = new Message("HEARTBEAT", "ping", Message.MessageType.ACK);
            peerConnection.connection.send(heartbeat);
            return true;
        } catch (IOException e) {
            logger.warning("Failed to send heartbeat to " + peerId + ": " + e.getMessage());
            handleConnectionFailure(peerId);
            return false;
        }
    }

    public void scheduleReconnection(PeerInfo peerInfo) {
        String peerId = peerInfo.getPeerId();
        PeerConnection peerConnection = peerConnections.get(peerId);

        if (peerConnection == null) {
            peerConnection = new PeerConnection(peerInfo, null, ConnectionType.DIRECT);
            peerConnections.put(peerId, peerConnection);
        }

        if (peerConnection.reconnectionAttempts >= config.getMaxReconnectionAttempts()) {
            logger.warning("Max reconnection attempts reached for peer: " + peerId);
            disconnect(peerId);
            return;
        }

        peerConnection.state = ConnectionState.RECONNECTING;
        peerConnection.reconnectionAttempts++;

        long delay = calculateBackoffDelay(peerConnection.reconnectionAttempts);

        logger.info("Scheduling reconnection to " + peerId + " (attempt " +
                   peerConnection.reconnectionAttempts + ") in " + delay + "ms");

        final PeerConnection finalPeerConnection = peerConnection;
        reconnectionExecutor.schedule(() -> {
            attemptReconnection(peerInfo, finalPeerConnection);
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void attemptReconnection(PeerInfo peerInfo, PeerConnection peerConnection) {
        SocketConnection connection = null;
        try {
            Socket socket = new Socket(peerInfo.getAddress(), peerInfo.getPort());
            socket.setSoTimeout(config.getConnectionTimeoutMs());

            connection = new SocketConnection(socket);

            peerConnection.connection = connection;
            peerConnection.state = ConnectionState.CONNECTED;
            peerConnection.reconnectionAttempts = 0;
            peerConnection.lastHeartbeat = System.currentTimeMillis();

            logger.info("Successfully reconnected to peer: " + peerInfo.getPeerId());
            notifyStateChange(peerInfo.getPeerId(), ConnectionState.CONNECTED);

        } catch (Exception e) {
            logger.warning("Reconnection failed for " + peerInfo.getPeerId() + ": " + e.getMessage());
            if (connection != null && peerConnection.connection != connection) {
                try {
                    connection.close();
                } catch (Exception ex) {
                    logger.fine("Error closing failed connection: " + ex.getMessage());
                }
            }
            scheduleReconnection(peerInfo);
        }
    }

    private void handleConnectionFailure(String peerId) {
        PeerConnection peerConnection = peerConnections.get(peerId);
        if (peerConnection != null) {
            peerConnection.state = ConnectionState.DISCONNECTED;
            notifyStateChange(peerId, ConnectionState.DISCONNECTED);

            if (peerConnection.reconnectionAttempts < config.getMaxReconnectionAttempts()) {
                scheduleReconnection(peerConnection.peerInfo);
            }
        }
    }

    private void startHeartbeatMonitor() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            long timeout = config.getHeartbeatIntervalMs() * 2;

            for (ConcurrentHashMap.Entry<String, PeerConnection> entry : peerConnections.entrySet()) {
                PeerConnection pc = entry.getValue();

                if (pc.state == ConnectionState.CONNECTED) {
                    long timeSinceLastHeartbeat = now - pc.lastHeartbeat;

                    if (timeSinceLastHeartbeat > timeout) {
                        logger.warning("Heartbeat timeout for peer: " + entry.getKey());
                        handleConnectionFailure(entry.getKey());
                    } else if (timeSinceLastHeartbeat > config.getHeartbeatIntervalMs()) {
                        sendHeartbeat(entry.getKey());
                    }
                }
            }
        }, config.getHeartbeatIntervalMs(), config.getHeartbeatIntervalMs(), TimeUnit.MILLISECONDS);
    }

    private long calculateBackoffDelay(int attempts) {
        long baseDelay = config.getReconnectionDelayMs();
        long exponentialDelay = baseDelay * (1L << Math.min(attempts - 1, 5));
        return Math.min(exponentialDelay, 60000);
    }

    public void setConnectionStateListener(ConnectionStateListener listener) {
        this.stateListener = listener;
    }

    private void notifyStateChange(String peerId, ConnectionState newState) {
        if (stateListener != null) {
            try {
                stateListener.onConnectionStateChanged(peerId, newState);
            } catch (Exception e) {
                logger.warning("Error in state listener: " + e.getMessage());
            }
        }
    }

    public ConcurrentHashMap<String, PeerConnection> getAllConnections() {
        return new ConcurrentHashMap<>(peerConnections);
    }

    @Override
    public void close() {
        heartbeatExecutor.shutdown();
        reconnectionExecutor.shutdown();

        try {
            if (!heartbeatExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                heartbeatExecutor.shutdownNow();
            }
            if (!reconnectionExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                reconnectionExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            heartbeatExecutor.shutdownNow();
            reconnectionExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        for (String peerId : peerConnections.keySet()) {
            disconnect(peerId);
        }

        logger.info("PeerConnectionManager closed");
    }

    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RELAYED,
        RECONNECTING
    }

    public enum ConnectionType {
        DIRECT,
        RELAYED
    }

    public static class PeerConnection {
        public final PeerInfo peerInfo;
        public SocketConnection connection;
        public ConnectionState state;
        public ConnectionType type;
        public long lastHeartbeat;
        public int reconnectionAttempts;

        public PeerConnection(PeerInfo peerInfo, SocketConnection connection, ConnectionType type) {
            this.peerInfo = peerInfo;
            this.connection = connection;
            this.type = type;
            this.state = ConnectionState.DISCONNECTED;
            this.lastHeartbeat = System.currentTimeMillis();
            this.reconnectionAttempts = 0;
        }
    }

    @FunctionalInterface
    public interface ConnectionStateListener {
        void onConnectionStateChanged(String peerId, ConnectionState newState);
    }
}
