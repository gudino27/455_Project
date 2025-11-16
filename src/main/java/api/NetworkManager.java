package api;

import network.lan.PeerInfo;

import java.util.List;

public interface NetworkManager extends AutoCloseable {

    void start() throws Exception;

    void stop();

    void connect(String address, int port) throws Exception;

    void sendMessage(String peerId, String message);

    void broadcast(String message);

    List<PeerInfo> getPeers();

    void setMessageListener(MessageListener listener);

    void setPeerConnectionListener(PeerConnectionListener listener);

    ConnectionMode getMode();

    @FunctionalInterface
    interface MessageListener {
        void onMessageReceived(String peerId, String message);
    }

    @FunctionalInterface
    interface PeerConnectionListener {
        void onPeerConnected(String peerId, String address);

        default void onPeerDisconnected(String peerId) {}
    }
}
