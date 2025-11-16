package unit.p2p;

import network.lan.PeerInfo;
import network.p2p.P2PDiscovery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class P2PDiscoveryTest {

    private P2PDiscovery discovery;
    private List<String> bootstrapPeers;

    @BeforeEach
    void setUp() {
        bootstrapPeers = new ArrayList<>();
        bootstrapPeers.add("192.168.1.100:9000");
        bootstrapPeers.add("192.168.1.101:9001");
        discovery = new P2PDiscovery("local-peer", bootstrapPeers);
    }

    @AfterEach
    void tearDown() {
        if (discovery != null) {
            discovery.close();
        }
    }

    @Test
    void testDiscoveryCreation() {
        assertNotNull(discovery);
    }

    @Test
    void testStartWithBootstrapPeers() {
        discovery.start();
        List<PeerInfo> knownPeers = discovery.getKnownPeers();
        assertEquals(2, knownPeers.size());
    }

    @Test
    void testAddPeer() {
        discovery.addPeer("peer-1", "192.168.1.50", 8080);

        List<PeerInfo> knownPeers = discovery.getKnownPeers();
        assertEquals(1, knownPeers.size());

        PeerInfo peer = knownPeers.get(0);
        assertEquals("peer-1", peer.getPeerId());
        assertEquals("192.168.1.50", peer.getAddress());
        assertEquals(8080, peer.getPort());
    }

    @Test
    void testAddLocalPeer() {
        discovery.addPeer("local-peer", "192.168.1.50", 8080);
        List<PeerInfo> knownPeers = discovery.getKnownPeers();
        assertEquals(0, knownPeers.size());
    }

    @Test
    void testUpdateExistingPeer() {
        discovery.addPeer("peer-1", "192.168.1.50", 8080);
        assertEquals(1, discovery.getKnownPeers().size());

        discovery.addPeer("peer-1", "192.168.1.51", 8081);
        List<PeerInfo> knownPeers = discovery.getKnownPeers();
        assertEquals(1, knownPeers.size());

        PeerInfo peer = knownPeers.get(0);
        assertEquals("192.168.1.51", peer.getAddress());
        assertEquals(8081, peer.getPort());
    }

    @Test
    void testRemovePeer() {
        discovery.addPeer("peer-1", "192.168.1.50", 8080);
        assertEquals(1, discovery.getKnownPeers().size());

        discovery.removePeer("peer-1");
        assertEquals(0, discovery.getKnownPeers().size());
    }

    @Test
    void testRemoveNonExistentPeer() {
        discovery.removePeer("non-existent");
        assertEquals(0, discovery.getKnownPeers().size());
    }

    @Test
    void testExchangePeers() {
        List<PeerInfo> remotePeers = Arrays.asList(
            new PeerInfo("peer-1", "192.168.1.50", 8080),
            new PeerInfo("peer-2", "192.168.1.51", 8081),
            new PeerInfo("local-peer", "192.168.1.1", 9000)
        );

        discovery.exchangePeers(remotePeers);

        List<PeerInfo> knownPeers = discovery.getKnownPeers();
        assertEquals(2, knownPeers.size());

        boolean foundPeer1 = false;
        boolean foundPeer2 = false;
        for (PeerInfo peer : knownPeers) {
            if (peer.getPeerId().equals("peer-1")) foundPeer1 = true;
            if (peer.getPeerId().equals("peer-2")) foundPeer2 = true;
        }
        assertTrue(foundPeer1);
        assertTrue(foundPeer2);
    }

    @Test
    void testGetPeersForExchange() {
        for (int i = 0; i < 25; i++) {
            discovery.addPeer("peer-" + i, "192.168.1." + i, 9000);
        }

        List<PeerInfo> exchangePeers = discovery.getPeersForExchange();
        assertTrue(exchangePeers.size() <= 20);
    }

    @Test
    void testGetPeersForExchangeWithFewPeers() {
        discovery.addPeer("peer-1", "192.168.1.50", 8080);
        discovery.addPeer("peer-2", "192.168.1.51", 8081);

        List<PeerInfo> exchangePeers = discovery.getPeersForExchange();
        assertEquals(2, exchangePeers.size());
    }

    @Test
    void testDiscoveryListener() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<PeerInfo> discoveredPeer = new AtomicReference<>();

        discovery.addDiscoveryListener(peerInfo -> {
            discoveredPeer.set(peerInfo);
            latch.countDown();
        });

        discovery.addPeer("peer-1", "192.168.1.50", 8080);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(discoveredPeer.get());
        assertEquals("peer-1", discoveredPeer.get().getPeerId());
    }

    @Test
    void testRemoveDiscoveryListener() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        P2PDiscovery.DiscoveryListener listener = peerInfo -> latch.countDown();

        discovery.addDiscoveryListener(listener);
        discovery.removeDiscoveryListener(listener);

        discovery.addPeer("peer-1", "192.168.1.50", 8080);

        assertFalse(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    void testStartWithInvalidBootstrapPeer() {
        List<String> invalidBootstrap = Arrays.asList(
            "invalid-format",
            "192.168.1.100",
            "192.168.1.100:abc"
        );

        P2PDiscovery testDiscovery = new P2PDiscovery("local-peer", invalidBootstrap);
        testDiscovery.start();

        assertEquals(0, testDiscovery.getKnownPeers().size());
        testDiscovery.close();
    }

    @Test
    void testStartWithEmptyBootstrapPeers() {
        P2PDiscovery testDiscovery = new P2PDiscovery("local-peer", new ArrayList<>());
        testDiscovery.start();

        assertEquals(0, testDiscovery.getKnownPeers().size());
        testDiscovery.close();
    }

    @Test
    void testClose() {
        discovery.addPeer("peer-1", "192.168.1.50", 8080);
        discovery.addPeer("peer-2", "192.168.1.51", 8081);

        assertEquals(2, discovery.getKnownPeers().size());

        discovery.close();

        assertEquals(0, discovery.getKnownPeers().size());
    }

    @Test
    void testMultipleListeners() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);

        discovery.addDiscoveryListener(peerInfo -> latch.countDown());
        discovery.addDiscoveryListener(peerInfo -> latch.countDown());

        discovery.addPeer("peer-1", "192.168.1.50", 8080);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }
}
