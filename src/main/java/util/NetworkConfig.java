package util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class NetworkConfig {
    private int localPort = 8080;
    private List<String> bootstrapPeers = new ArrayList<>();
    private String relayServerAddress = null;
    private int relayServerPort = 9090;
    private boolean enableUPnP = true;
    private int heartbeatIntervalMs = 30000;
    private int connectionTimeoutMs = 10000;
    private int reconnectionDelayMs = 5000;
    private int maxReconnectionAttempts = 3;
    private String externalIp = null;
    private int externalPort = 0;

    public NetworkConfig() {}

    public static NetworkConfig fromFile(String filePath) throws IOException {
        NetworkConfig config = new NetworkConfig();
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(filePath)) {
            props.load(fis);
        }

        if (props.containsKey("local.port")) {
            config.localPort = Integer.parseInt(props.getProperty("local.port"));
        }
        if (props.containsKey("bootstrap.peers")) {
            String peersStr = props.getProperty("bootstrap.peers");
            String[] peers = peersStr.split(",");
            for (String peer : peers) {
                config.bootstrapPeers.add(peer.trim());
            }
        }
        if (props.containsKey("relay.server.address")) {
            config.relayServerAddress = props.getProperty("relay.server.address");
        }
        if (props.containsKey("relay.server.port")) {
            config.relayServerPort = Integer.parseInt(props.getProperty("relay.server.port"));
        }
        if (props.containsKey("upnp.enabled")) {
            config.enableUPnP = Boolean.parseBoolean(props.getProperty("upnp.enabled"));
        }
        if (props.containsKey("heartbeat.interval.ms")) {
            config.heartbeatIntervalMs = Integer.parseInt(props.getProperty("heartbeat.interval.ms"));
        }
        if (props.containsKey("connection.timeout.ms")) {
            config.connectionTimeoutMs = Integer.parseInt(props.getProperty("connection.timeout.ms"));
        }
        if (props.containsKey("reconnection.delay.ms")) {
            config.reconnectionDelayMs = Integer.parseInt(props.getProperty("reconnection.delay.ms"));
        }
        if (props.containsKey("max.reconnection.attempts")) {
            config.maxReconnectionAttempts = Integer.parseInt(props.getProperty("max.reconnection.attempts"));
        }
        if (props.containsKey("external.ip")) {
            config.externalIp = props.getProperty("external.ip");
        }
        if (props.containsKey("external.port")) {
            config.externalPort = Integer.parseInt(props.getProperty("external.port"));
        }

        return config;
    }

    public int getLocalPort() {
        return localPort;
    }

    public void setLocalPort(int localPort) {
        this.localPort = localPort;
    }

    public List<String> getBootstrapPeers() {
        return new ArrayList<>(bootstrapPeers);
    }

    public void setBootstrapPeers(List<String> bootstrapPeers) {
        this.bootstrapPeers = new ArrayList<>(bootstrapPeers);
    }

    public void addBootstrapPeer(String peer) {
        this.bootstrapPeers.add(peer);
    }

    public String getRelayServerAddress() {
        return relayServerAddress;
    }

    public void setRelayServerAddress(String relayServerAddress) {
        this.relayServerAddress = relayServerAddress;
    }

    public int getRelayServerPort() {
        return relayServerPort;
    }

    public void setRelayServerPort(int relayServerPort) {
        this.relayServerPort = relayServerPort;
    }

    public boolean isEnableUPnP() {
        return enableUPnP;
    }

    public void setEnableUPnP(boolean enableUPnP) {
        this.enableUPnP = enableUPnP;
    }

    public int getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(int heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public void setConnectionTimeoutMs(int connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    public int getReconnectionDelayMs() {
        return reconnectionDelayMs;
    }

    public void setReconnectionDelayMs(int reconnectionDelayMs) {
        this.reconnectionDelayMs = reconnectionDelayMs;
    }

    public int getMaxReconnectionAttempts() {
        return maxReconnectionAttempts;
    }

    public void setMaxReconnectionAttempts(int maxReconnectionAttempts) {
        this.maxReconnectionAttempts = maxReconnectionAttempts;
    }

    public String getExternalIp() {
        return externalIp;
    }

    public void setExternalIp(String externalIp) {
        this.externalIp = externalIp;
    }

    public int getExternalPort() {
        return externalPort;
    }

    public void setExternalPort(int externalPort) {
        this.externalPort = externalPort;
    }

    @Override
    public String toString() {
        return "NetworkConfig{" +
                "localPort=" + localPort +
                ", bootstrapPeers=" + bootstrapPeers +
                ", relayServerAddress='" + relayServerAddress + '\'' +
                ", relayServerPort=" + relayServerPort +
                ", enableUPnP=" + enableUPnP +
                ", heartbeatIntervalMs=" + heartbeatIntervalMs +
                ", connectionTimeoutMs=" + connectionTimeoutMs +
                ", reconnectionDelayMs=" + reconnectionDelayMs +
                ", maxReconnectionAttempts=" + maxReconnectionAttempts +
                ", externalIp='" + externalIp + '\'' +
                ", externalPort=" + externalPort +
                '}';
    }
}
