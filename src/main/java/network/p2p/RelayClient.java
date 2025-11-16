package network.p2p;

import network.protocol.Message;
import network.socket.SocketConnection;

import java.io.IOException;
import java.net.Socket;
import java.util.logging.Logger;

public class RelayClient implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(RelayClient.class.getName());

    private final String relayServerAddress;
    private final int relayServerPort;
    private final String localPeerId;
    private SocketConnection relayConnection;
    private volatile boolean connected;
    private MessageListener messageListener;

    public RelayClient(String relayServerAddress, int relayServerPort, String localPeerId) {
        this.relayServerAddress = relayServerAddress;
        this.relayServerPort = relayServerPort;
        this.localPeerId = localPeerId;
        this.connected = false;
    }

    public boolean connect() {
        try {
            Socket socket = new Socket(relayServerAddress, relayServerPort);
            relayConnection = new SocketConnection(socket);

            relayConnection.setMessageHandler(new SocketConnection.MessageHandler() {
                @Override
                public void onMessage(Object msg, SocketConnection conn) {
                    if (msg instanceof Message) {
                        handleRelayedMessage((Message) msg);
                    }
                }

                @Override
                public void onError(Exception e, SocketConnection conn) {
                    logger.warning("Relay connection error: " + e.getMessage());
                    connected = false;
                }

                @Override
                public void onDisconnect(SocketConnection conn) {
                    logger.info("Disconnected from relay server");
                    connected = false;
                }
            });

            Message handshake = new Message(localPeerId, "RELAY_REGISTER", Message.MessageType.HANDSHAKE);
            relayConnection.send(handshake);

            Object response = relayConnection.receiveBlocking();
            if (response instanceof Message) {
                Message ackMsg = (Message) response;
                if (ackMsg.getType() == Message.MessageType.ACK) {
                    connected = true;
                    logger.info("Connected to relay server at " + relayServerAddress + ":" + relayServerPort);
                    return true;
                }
            }

            logger.warning("Failed to register with relay server");
            relayConnection.close();
            return false;

        } catch (Exception e) {
            logger.warning("Failed to connect to relay server: " + e.getMessage());
            return false;
        }
    }

    public void sendMessageViaRelay(String recipientId, String content) throws IOException {
        if (!connected || relayConnection == null) {
            throw new IOException("Not connected to relay server");
        }

        String relayContent = "TO:" + recipientId + ":" + content;
        Message message = new Message(localPeerId, relayContent, Message.MessageType.TEXT);
        relayConnection.send(message);
        logger.fine("Sent message to " + recipientId + " via relay");
    }

    private void handleRelayedMessage(Message message) {
        if (messageListener != null) {
            String content = message.getContent();
            if (content != null && content.startsWith("TO:")) {
                int firstColon = content.indexOf(':', 3);
                if (firstColon > 3) {
                    String actualContent = content.substring(firstColon + 1);
                    messageListener.onRelayedMessage(message.getSenderId(), actualContent);
                    return;
                }
            }
            messageListener.onRelayedMessage(message.getSenderId(), message.getContent());
        }
    }

    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    public boolean isConnected() {
        return connected && relayConnection != null && relayConnection.isConnected();
    }

    @Override
    public void close() {
        connected = false;
        if (relayConnection != null) {
            try {
                Message disconnect = new Message(localPeerId, "DISCONNECT", Message.MessageType.DISCONNECT);
                relayConnection.send(disconnect);
            } catch (Exception e) {
                logger.fine("Error sending disconnect message: " + e.getMessage());
            }
            try {
                relayConnection.close();
            } catch (Exception e) {
                logger.fine("Error closing relay connection: " + e.getMessage());
            }
        }
        logger.info("RelayClient closed");
    }

    @FunctionalInterface
    public interface MessageListener {
        void onRelayedMessage(String senderId, String content);
    }
}
