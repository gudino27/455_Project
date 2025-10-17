package unit.lan;

import network.lan.LANDiscovery;
import network.lan.PeerInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.SocketException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LANDiscoveryTest {

    private LANDiscovery discovery1;
    private LANDiscovery discovery2;

    @AfterEach
    void tearDown() {
        if (discovery1 != null) discovery1.close();
        if (discovery2 != null) discovery2.close();
    }

    @Test
    void testDiscoveryCreation() throws SocketException {
        discovery1 = new LANDiscovery("peer-1", 9000);
        assertNotNull(discovery1);
    }

    @Test
    void testStartDiscovery() throws SocketException {
        discovery1 = new LANDiscovery("peer-1", 9000);
        discovery1.start();
    }

    @Test
    void testPeerDiscovery() throws SocketException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<PeerInfo> discoveredPeer = new AtomicReference<>();

        discovery1 = new LANDiscovery("peer-1", 9001);
        discovery2 = new LANDiscovery("peer-2", 9002);

        discovery1.addListener(peerInfo -> {
            if (peerInfo.getPeerId().equals("peer-2")) {
                discoveredPeer.set(peerInfo);
                latch.countDown();
            }
        });

        discovery1.start();
        discovery2.start();

        boolean discovered = latch.await(10, TimeUnit.SECONDS);
        assertTrue(discovered, "Peer discovery timed out");

        assertNotNull(discoveredPeer.get());
        assertEquals("peer-2", discoveredPeer.get().getPeerId());
        assertEquals(9002, discoveredPeer.get().getPort());
    }

    @Test
    void testMultiplePeerDiscovery() throws SocketException, InterruptedException {
        LANDiscovery discovery3 = null;

        try {
            CountDownLatch latch = new CountDownLatch(2);

            discovery1 = new LANDiscovery("peer-1", 9001);
            discovery2 = new LANDiscovery("peer-2", 9002);
            discovery3 = new LANDiscovery("peer-3", 9003);

            discovery1.addListener(peerInfo -> latch.countDown());

            discovery1.start();
            discovery2.start();
            discovery3.start();

            boolean discovered = latch.await(15, TimeUnit.SECONDS);
            assertTrue(discovered, "Failed to discover multiple peers");

        } finally {
            if (discovery3 != null) discovery3.close();
        }
    }

    @Test
    void testRemoveListener() throws SocketException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        discovery1 = new LANDiscovery("peer-1", 9001);
        discovery2 = new LANDiscovery("peer-2", 9002);

        LANDiscovery.DiscoveryListener listener = peerInfo -> latch.countDown();

        discovery1.addListener(listener);
        discovery1.removeListener(listener);

        discovery1.start();
        discovery2.start();

        boolean discovered = latch.await(6, TimeUnit.SECONDS);
        assertFalse(discovered, "Listener should have been removed");
    }

    @Test
    void testSelfDiscoveryIgnored() throws SocketException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        discovery1 = new LANDiscovery("peer-same", 9001);

        discovery1.addListener(peerInfo -> {
            if (peerInfo.getPeerId().equals("peer-same")) {
                latch.countDown();
            }
        });

        discovery1.start();

        boolean discovered = latch.await(6, TimeUnit.SECONDS);
        assertFalse(discovered, "Should not discover self");
    }

    @Test
    void testDiscoveryClose() throws SocketException {
        discovery1 = new LANDiscovery("peer-1", 9001);
        discovery1.start();
        discovery1.close();
    }
}
