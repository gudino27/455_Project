package api;

import network.lan.LANManager;
import network.p2p.P2PManager;
import util.NetworkConfig;

public class NetworkManagerFactory {

    public static NetworkManager createNetworkManager(ConnectionMode mode, NetworkConfig config) {
        try {
            switch (mode) {
                case LAN:
                    return new LANManager(config);
                case P2P:
                    return new P2PManager(config);
                default:
                    throw new IllegalArgumentException("Unknown connection mode: " + mode);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create network manager: " + e.getMessage(), e);
        }
    }

    public static NetworkManager createNetworkManager(ConnectionMode mode, int port) {
        NetworkConfig config = new NetworkConfig();
        config.setLocalPort(port);
        return createNetworkManager(mode, config);
    }
}
