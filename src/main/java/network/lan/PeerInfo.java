package network.lan;

import java.io.Serializable;
import java.util.Objects;

public class PeerInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String peerId;
    private final String address;
    private final int port;
    private final long timestamp;

    public PeerInfo(String peerId, String address, int port) {
        this.peerId = peerId;
        this.address = address;
        this.port = port;
        this.timestamp = System.currentTimeMillis();
    }

    public String getPeerId() {
        return peerId;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isExpired(long timeoutMs) {
        return System.currentTimeMillis() - timestamp > timeoutMs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerInfo peerInfo = (PeerInfo) o;
        return port == peerInfo.port &&
               Objects.equals(peerId, peerInfo.peerId) &&
               Objects.equals(address, peerInfo.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(peerId, address, port);
    }

    @Override
    public String toString() {
        return "PeerInfo{" +
               "peerId='" + peerId + '\'' +
               ", address='" + address + '\'' +
               ", port=" + port +
               '}';
    }
}
