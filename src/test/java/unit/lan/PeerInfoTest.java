package unit.lan;

import network.lan.PeerInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PeerInfoTest {

    @Test
    void testPeerInfoCreation() {
        String peerId = "peer-123";
        String address = "192.168.1.100";
        int port = 9000;

        PeerInfo peerInfo = new PeerInfo(peerId, address, port);

        assertEquals(peerId, peerInfo.getPeerId());
        assertEquals(address, peerInfo.getAddress());
        assertEquals(port, peerInfo.getPort());
        assertTrue(peerInfo.getTimestamp() > 0);
    }

    @Test
    void testIsExpired() throws InterruptedException {
        PeerInfo peerInfo = new PeerInfo("peer-test", "localhost", 8000);

        assertFalse(peerInfo.isExpired(1000));

        Thread.sleep(100);
        assertFalse(peerInfo.isExpired(200));

        Thread.sleep(150);
        assertTrue(peerInfo.isExpired(200));
    }

    @Test
    void testEquals() {
        PeerInfo peer1 = new PeerInfo("peer-1", "192.168.1.1", 9000);
        PeerInfo peer2 = new PeerInfo("peer-1", "192.168.1.1", 9000);
        PeerInfo peer3 = new PeerInfo("peer-2", "192.168.1.1", 9000);
        PeerInfo peer4 = new PeerInfo("peer-1", "192.168.1.2", 9000);
        PeerInfo peer5 = new PeerInfo("peer-1", "192.168.1.1", 9001);

        assertEquals(peer1, peer2);
        assertNotEquals(peer1, peer3);
        assertNotEquals(peer1, peer4);
        assertNotEquals(peer1, peer5);
    }

    @Test
    void testHashCode() {
        PeerInfo peer1 = new PeerInfo("peer-1", "192.168.1.1", 9000);
        PeerInfo peer2 = new PeerInfo("peer-1", "192.168.1.1", 9000);

        assertEquals(peer1.hashCode(), peer2.hashCode());
    }

    @Test
    void testToString() {
        PeerInfo peerInfo = new PeerInfo("peer-abc", "10.0.0.1", 8080);
        String toString = peerInfo.toString();

        assertTrue(toString.contains("peer-abc"));
        assertTrue(toString.contains("10.0.0.1"));
        assertTrue(toString.contains("8080"));
    }
}
