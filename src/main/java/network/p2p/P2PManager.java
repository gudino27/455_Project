package network.p2p;

import api.ConnectionMode;
import api.NetworkManager;
import network.lan.PeerInfo;
import network.protocol.Message;
import network.socket.SocketConnection;
import network.socket.SocketServer;
import util.NetworkConfig;
import util.NATDetector;
import util.UPnPManager;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

public class P2PManager implements NetworkManager {
    private static final Logger logger = Logger.getLogger(P2PManager.class.getName());

    private final NetworkConfig config;
    private final String localPeerId;
    private final SocketServer socketServer;
    private final P2PDiscovery discovery;
    private final PeerConnectionManager connectionManager;
    private final List<MessageListener> messageListeners;
    private final List<PeerConnectionListener> peerConnectionListeners;

    private UPnPManager upnpManager;
    private RelayClient relayClient;
    private volatile boolean running;

    public P2PManager(NetworkConfig config) {
        this.config = config;
        this.localPeerId = generatePeerId();
        try {
            this.socketServer = new SocketServer(config.getLocalPort());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create socket server: " + e.getMessage(), e);
        }
        this.discovery = new P2PDiscovery(localPeerId, config.getBootstrapPeers());
        this.connectionManager = new PeerConnectionManager(config);
        this.messageListeners = new CopyOnWriteArrayList<>();
        this.peerConnectionListeners = new CopyOnWriteArrayList<>();
        this.running = false;
    }

    @Override
    public void start() throws Exception {
        if (running) {
            logger.warning("P2PManager already running");
            return;
        }

        logger.info("Starting P2PManager with peer ID: " + localPeerId);

        if (config.isEnableUPnP()) {
            setupUPnP();
        }

        if (config.getRelayServerAddress() != null) {
            setupRelayClient();
        }

        setupSocketServer();

        discovery.addDiscoveryListener(this::handlePeerDiscovery);
        discovery.start();

        connectionManager.setConnectionStateListener(this::handleConnectionStateChange);

        connectToBootstrapPeers();

        running = true;
        logger.info("P2PManager started on port " + config.getLocalPort());
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        running = false;

        try {
            discovery.close();
        } catch (Exception e) {
            logger.warning("Error closing discovery: " + e.getMessage());
        }

        try {
            connectionManager.close();
        } catch (Exception e) {
            logger.warning("Error closing connection manager: " + e.getMessage());
        }

        try {
            socketServer.close();
        } catch (Exception e) {
            logger.warning("Error closing socket server: " + e.getMessage());
        }

        if (relayClient != null) {
            try {
                relayClient.close();
            } catch (Exception e) {
                logger.warning("Error closing relay client: " + e.getMessage());
            }
        }

        if (upnpManager != null) {
            try {
                upnpManager.deletePortMapping(config.getLocalPort(), "TCP");
                upnpManager.close();
            } catch (Exception e) {
                logger.warning("Error cleaning up UPnP: " + e.getMessage());
            }
        }

        logger.info("P2PManager stopped");
    }

    @Override
    public void connect(String address, int port) throws Exception {
        // Check if we already know this peer by address:port
        for (PeerInfo peer : discovery.getKnownPeers()) {
            if (peer.getAddress().equals(address) && peer.getPort() == port) {
                if (connectionManager.getConnectionState(peer.getPeerId()) == PeerConnectionManager.ConnectionState.CONNECTED) {
                    logger.fine("Already connected to " + peer.getPeerId());
                    return;
                }
                connectToPeer(peer);
                return;
            }
        }

        // First time connecting, use placeholder until handshake
        PeerInfo peerInfo = new PeerInfo("connecting-" + address + ":" + port, address, port);
        connectToPeer(peerInfo);
    }

    @Override
    public void sendMessage(String peerId, String message) {
        SocketConnection connection = connectionManager.getConnection(peerId);

        if (connection != null && connection.isConnected()) {
            try {
                Message msg = new Message(localPeerId, message, Message.MessageType.TEXT);
                connection.send(msg);
                logger.fine("Sent message to " + peerId);
            } catch (IOException e) {
                logger.warning("Failed to send message to " + peerId + ": " + e.getMessage());

                if (relayClient != null && relayClient.isConnected()) {
                    try {
                        relayClient.sendMessageViaRelay(peerId, message);
                        logger.info("Message sent via relay to " + peerId);
                    } catch (IOException relayError) {
                        logger.severe("Failed to send via relay: " + relayError.getMessage());
                    }
                }
            }
        } else {
            logger.warning("No connection to peer: " + peerId);

            if (relayClient != null && relayClient.isConnected()) {
                try {
                    relayClient.sendMessageViaRelay(peerId, message);
                    logger.info("Message sent via relay to " + peerId);
                } catch (IOException e) {
                    logger.severe("Failed to send via relay: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void broadcast(String message) {
        List<PeerInfo> peers = discovery.getKnownPeers();
        logger.info("Broadcasting message to " + peers.size() + " peers");

        for (PeerInfo peer : peers) {
            sendMessage(peer.getPeerId(), message);
        }
    }

    @Override
    public List<PeerInfo> getPeers() {
        return discovery.getKnownPeers();
    }

    @Override
    public void setMessageListener(MessageListener listener) {
        messageListeners.add(listener);
    }

    @Override
    public void setPeerConnectionListener(PeerConnectionListener listener) {
        peerConnectionListeners.add(listener);
    }

    @Override
    public ConnectionMode getMode() {
        return ConnectionMode.P2P;
    }

    private void setupUPnP() {
        try {
            upnpManager = new UPnPManager();
            if (upnpManager.initialize()) {
                boolean success = upnpManager.addPortMapping(
                    config.getLocalPort(),
                    config.getLocalPort(),
                    "TCP",
                    "P2P Network Application"
                );

                if (success) {
                    String externalIp = NATDetector.getExternalIP();
                    if (externalIp != null) {
                        config.setExternalIp(externalIp);
                        config.setExternalPort(config.getLocalPort());
                        logger.info("UPnP port mapping successful: " + externalIp + ":" + config.getLocalPort());
                    }
                } else {
                    logger.warning("UPnP port mapping failed");
                }
            } else {
                logger.warning("UPnP initialization failed");
            }
        } catch (Exception e) {
            logger.warning("UPnP setup error: " + e.getMessage());
        }
    }

    private void setupRelayClient() {
        try {
            relayClient = new RelayClient(
                config.getRelayServerAddress(),
                config.getRelayServerPort(),
                localPeerId
            );

            if (relayClient.connect()) {
                relayClient.setMessageListener(this::handleRelayedMessage);
                logger.info("Connected to relay server");
            } else {
                logger.warning("Failed to connect to relay server");
                relayClient = null;
            }
        } catch (Exception e) {
            logger.warning("Relay client setup error: " + e.getMessage());
            relayClient = null;
        }
    }

    private void setupSocketServer() {
        socketServer.setConnectionHandler(connection -> {
            try {
                handleIncomingConnection(connection);
            } catch (Exception e) {
                logger.warning("Error handling incoming connection: " + e.getMessage());
            }
        });

        socketServer.start();
    }

    private void handleIncomingConnection(SocketConnection connection) throws Exception {

        // Add timeout before receiving - must be done before setting message handler
        connection.setTimeout(config.getConnectionTimeoutMs());

        Object firstMessage;
        try {
            firstMessage = connection.receiveBlocking(config.getConnectionTimeoutMs());
        } catch (InterruptedException e) {
            logger.warning("Interrupted while waiting for handshake");
            connection.close();
            return;
        }

        if (firstMessage == null) {
            logger.warning("Timeout waiting for handshake from " + connection.getRemoteAddress());
            connection.close();
            return;
        }

        if (firstMessage instanceof Message) {
            Message handshake = (Message) firstMessage;

            if (handshake.getType() == Message.MessageType.HANDSHAKE) {
                String remotePeerId = handshake.getSenderId();

                int remotePort = connection.getRemotePort();
                String content = handshake.getContent();
                if (content != null && content.startsWith("HANDSHAKE:")) {
                    try {
                        remotePort = Integer.parseInt(content.substring(10));
                    } catch (NumberFormatException e) {
                        logger.warning("Invalid port in handshake: " + content);
                    }
                }

                PeerInfo peerInfo = new PeerInfo(remotePeerId, connection.getRemoteAddress(), remotePort);

                // CHECK FOR DUPLICATE BEFORE ADDING
                PeerConnectionManager.ConnectionState existingState = connectionManager.getConnectionState(remotePeerId);
                if (existingState == PeerConnectionManager.ConnectionState.CONNECTED) {
                    // Use peer ID comparison to decide which connection to keep
                    if (shouldCloseIncomingConnection(localPeerId, remotePeerId)) {
                        logger.info("Duplicate connection with " + remotePeerId + ", closing incoming (keeping outgoing)");
                        connection.close();
                        return;
                    } else {
                        logger.info("Duplicate connection with " + remotePeerId + ", keeping incoming (closing outgoing)");
                        connectionManager.disconnect(remotePeerId);
                    }
                }

                // Clear socket timeout after handshake (0 = infinite)
                connection.setTimeout(0);

                // Set message handler AFTER handshake is complete
                connection.setMessageHandler(new SocketConnection.MessageHandler() {
                    @Override
                    public void onMessage(Object msg, SocketConnection conn) {
                        if (msg instanceof Message) {
                            handleMessage((Message) msg, conn);
                        }
                    }

                    @Override
                    public void onError(Exception e, SocketConnection conn) {
                        logger.warning("Connection error: " + e.getMessage());
                    }

                    @Override
                    public void onDisconnect(SocketConnection conn) {
                        logger.fine("Peer disconnected: " + conn.getRemoteAddress());
                    }
                });

                connectionManager.connect(peerInfo, connection, PeerConnectionManager.ConnectionType.DIRECT);
                discovery.addPeer(remotePeerId, peerInfo.getAddress(), peerInfo.getPort());

                Message ack = new Message(localPeerId, "HANDSHAKE_ACK", Message.MessageType.ACK);
                connection.send(ack);

                notifyPeerConnected(remotePeerId, peerInfo.getAddress());

                sendPeerExchange(remotePeerId);
            }
        }
    }

    private void connectToBootstrapPeers() {
        List<PeerInfo> bootstrapPeers = discovery.getKnownPeers();

        for (PeerInfo peer : bootstrapPeers) {
            try {
                connectToPeer(peer);
            } catch (Exception e) {
                logger.warning("Failed to connect to bootstrap peer " + peer.getPeerId() + ": " + e.getMessage());
            }
        }
    }

    private void connectToPeer(PeerInfo peerInfo) {
        try {
            Socket socket = new Socket(peerInfo.getAddress(), peerInfo.getPort());
            socket.setSoTimeout(config.getConnectionTimeoutMs());

            SocketConnection connection = new SocketConnection(socket);

            Message handshake = new Message(localPeerId, "HANDSHAKE:" + config.getLocalPort(), Message.MessageType.HANDSHAKE);
            connection.send(handshake);

            // Wait for handshake ACK to get real peer ID (with timeout)
            Object response;
            try {
                response = connection.receiveBlocking(config.getConnectionTimeoutMs());
            } catch (InterruptedException e) {
                logger.warning("Interrupted waiting for handshake ACK from " + peerInfo.getPeerId());
                connection.close();
                return;
            }

            if (response == null) {
                logger.warning("Timeout waiting for handshake ACK from " + peerInfo.getPeerId());
                connection.close();
                return;
            }

            if (response instanceof Message) {
                Message ackMsg = (Message) response;
                if (ackMsg.getType() == Message.MessageType.ACK && "HANDSHAKE_ACK".equals(ackMsg.getContent())) {
                    String realPeerId = ackMsg.getSenderId();

                    // Check for duplicate with real peer ID
                    PeerConnectionManager.ConnectionState existingState = connectionManager.getConnectionState(realPeerId);
                    if (existingState == PeerConnectionManager.ConnectionState.CONNECTED) {
                        if (!shouldInitiateConnection(localPeerId, realPeerId)) {
                            logger.info("Duplicate connection with " + realPeerId + ", closing outgoing");
                            connection.close();
                            return;
                        }
                    }

                    // Update peer info with real ID
                    PeerInfo updatedPeerInfo = new PeerInfo(realPeerId, peerInfo.getAddress(), peerInfo.getPort());

                    // Clear socket timeout after handshake (0 = infinite)
                    connection.setTimeout(0);

                    // Set message handler after handshake with real peer ID
                    connection.setMessageHandler(new SocketConnection.MessageHandler() {
                        @Override
                        public void onMessage(Object msg, SocketConnection conn) {
                            if (msg instanceof Message) {
                                handleMessage((Message) msg, conn);
                            }
                        }

                        @Override
                        public void onError(Exception e, SocketConnection conn) {
                            logger.warning("Connection error with " + realPeerId + ": " + e.getMessage());
                        }

                        @Override
                        public void onDisconnect(SocketConnection conn) {
                            logger.info("Disconnected from " + realPeerId);
                            connectionManager.disconnect(realPeerId);
                        }
                    });

                    connectionManager.connect(updatedPeerInfo, connection, PeerConnectionManager.ConnectionType.DIRECT);
                    discovery.addPeer(realPeerId, updatedPeerInfo.getAddress(), updatedPeerInfo.getPort());

                    logger.info("Connected to peer: " + realPeerId);
                    notifyPeerConnected(realPeerId, updatedPeerInfo.getAddress());
                    sendPeerExchange(realPeerId);
                } else {
                    logger.warning("Unexpected handshake response: " + ackMsg);
                    connection.close();
                }
            } else {
                logger.warning("Invalid handshake response");
                connection.close();
            }

        } catch (IOException e) {
            logger.warning("Direct connection to " + peerInfo.getPeerId() + " failed: " + e.getMessage());

            if (relayClient != null && relayClient.isConnected()) {
                logger.info("Using relay for " + peerInfo.getPeerId());
            } else {
                connectionManager.scheduleReconnection(peerInfo);
            }
        }
    }

    private void handlePeerDiscovery(PeerInfo peerInfo) {
        PeerConnectionManager.ConnectionState state = connectionManager.getConnectionState(peerInfo.getPeerId());

        if (state == PeerConnectionManager.ConnectionState.DISCONNECTED) {
            // Only initiate if our peer ID is smaller (prevents simultaneous connections)
            if (shouldInitiateConnection(localPeerId, peerInfo.getPeerId())) {
                logger.info("Discovered new peer, attempting connection: " + peerInfo.getPeerId());
                connectToPeer(peerInfo);
            } else {
                logger.info("Discovered new peer, waiting for incoming connection: " + peerInfo.getPeerId());
            }
        }
    }

    private void handleMessage(Message message, SocketConnection connection) {
        connectionManager.recordHeartbeat(message.getSenderId());

        switch (message.getType()) {
            case TEXT:
                notifyMessageReceived(message.getSenderId(), message.getContent());
                break;

            case ACK:
                if ("PEER_EXCHANGE".equals(message.getContent())) {
                    handlePeerExchangeResponse(message);
                }
                break;

            case HANDSHAKE:
                logger.fine("Received handshake from " + message.getSenderId());
                break;

            case DISCONNECT:
                logger.info("Peer disconnecting: " + message.getSenderId());
                connectionManager.disconnect(message.getSenderId());
                break;
        }
    }

    private void handleRelayedMessage(String senderId, String content) {
        notifyMessageReceived(senderId, content);
    }

    private void sendPeerExchange(String peerId) {
        try {
            List<PeerInfo> peersToShare = discovery.getPeersForExchange();
            String peerListJson = serializePeerList(peersToShare);

            Message exchangeMsg = new Message(localPeerId, "PEER_EXCHANGE:" + peerListJson, Message.MessageType.ACK);
            sendMessage(peerId, exchangeMsg.getContent());
        } catch (Exception e) {
            logger.warning("Failed to send peer exchange: " + e.getMessage());
        }
    }

    private void handlePeerExchangeResponse(Message message) {
        try {
            String content = message.getContent();
            if (content.startsWith("PEER_EXCHANGE:")) {
                String peerListJson = content.substring(14);
                List<PeerInfo> remotePeers = deserializePeerList(peerListJson);
                discovery.exchangePeers(remotePeers);
                logger.fine("Processed peer exchange from " + message.getSenderId());
            }
        } catch (Exception e) {
            logger.warning("Failed to process peer exchange: " + e.getMessage());
        }
    }

    private String serializePeerList(List<PeerInfo> peers) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < peers.size(); i++) {
            PeerInfo peer = peers.get(i);
            sb.append(peer.getPeerId()).append("|")
              .append(peer.getAddress()).append("|")
              .append(peer.getPort());
            if (i < peers.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private List<PeerInfo> deserializePeerList(String json) {
        List<PeerInfo> peers = new ArrayList<>();
        if (json.startsWith("[") && json.endsWith("]")) {
            String content = json.substring(1, json.length() - 1);
            if (!content.isEmpty()) {
                String[] peerStrings = content.split(",");
                for (String peerStr : peerStrings) {
                    String[] parts = peerStr.split("\\|");
                    if (parts.length == 3) {
                        String peerId = parts[0];
                        String address = parts[1];
                        int port = Integer.parseInt(parts[2]);
                        peers.add(new PeerInfo(peerId, address, port));
                    }
                }
            }
        }
        return peers;
    }

    private void handleConnectionStateChange(String peerId, PeerConnectionManager.ConnectionState newState) {
        logger.fine("Peer " + peerId + " state changed to " + newState);

        if (newState == PeerConnectionManager.ConnectionState.DISCONNECTED) {
            notifyPeerDisconnected(peerId);
        }
    }

    private void notifyMessageReceived(String peerId, String message) {
        for (MessageListener listener : messageListeners) {
            try {
                listener.onMessageReceived(peerId, message);
            } catch (Exception e) {
                logger.warning("Error in message listener: " + e.getMessage());
            }
        }
    }

    private void notifyPeerConnected(String peerId, String address) {
        for (PeerConnectionListener listener : peerConnectionListeners) {
            try {
                listener.onPeerConnected(peerId, address);
            } catch (Exception e) {
                logger.warning("Error in peer connection listener: " + e.getMessage());
            }
        }
    }

    private void notifyPeerDisconnected(String peerId) {
        for (PeerConnectionListener listener : peerConnectionListeners) {
            try {
                listener.onPeerDisconnected(peerId);
            } catch (Exception e) {
                logger.warning("Error in peer disconnection listener: " + e.getMessage());
            }
        }
    }

    private String generatePeerId() {
        return "peer-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private boolean shouldCloseIncomingConnection(String localId, String remoteId) {
        // Peer with smaller ID keeps outgoing, larger ID keeps incoming
        return localId.compareTo(remoteId) < 0;
    }

    private boolean shouldInitiateConnection(String localId, String remoteId) {
        return localId.compareTo(remoteId) < 0;
    }

    @Override
    public void close() {
        stop();
    }
}
