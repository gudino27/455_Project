package unit.p2p;

import api.ConnectionMode;
import network.p2p.P2PManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import util.NetworkConfig;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class P2PManagerTest {

    private P2PManager p2pManager;
    private NetworkConfig config;

    @BeforeEach
    void setUp() {
        config = new NetworkConfig();
        config.setLocalPort(0);
        config.setEnableUPnP(false);
        config.setHeartbeatIntervalMs(10000);
        config.setConnectionTimeoutMs(5000);
    }

    @AfterEach
    void tearDown() {
        if (p2pManager != null) {
            p2pManager.close();
        }
    }

    @Test
    void testP2PManagerCreation() {
        p2pManager = new P2PManager(config);
        assertNotNull(p2pManager);
    }

    @Test
    void testP2PManagerStart() throws Exception {
        p2pManager = new P2PManager(config);
        p2pManager.start();
    }

    @Test
    void testP2PManagerStartTwice() throws Exception {
        p2pManager = new P2PManager(config);
        p2pManager.start();
        p2pManager.start();
    }

    @Test
    void testP2PManagerStop() throws Exception {
        p2pManager = new P2PManager(config);
        p2pManager.start();
        p2pManager.stop();
    }

    @Test
    void testP2PManagerStopWithoutStart() {
        p2pManager = new P2PManager(config);
        p2pManager.stop();
    }

    @Test
    void testGetMode() {
        p2pManager = new P2PManager(config);
        assertEquals(ConnectionMode.P2P, p2pManager.getMode());
    }

    @Test
    void testGetPeersEmpty() {
        p2pManager = new P2PManager(config);
        assertTrue(p2pManager.getPeers().isEmpty());
    }

    @Test
    void testSetMessageListener() throws Exception {
        p2pManager = new P2PManager(config);
        CountDownLatch latch = new CountDownLatch(1);

        p2pManager.setMessageListener((peerId, message) -> {
            latch.countDown();
        });

        p2pManager.start();
    }

    @Test
    void testSetPeerConnectionListener() throws Exception {
        p2pManager = new P2PManager(config);
        CountDownLatch latch = new CountDownLatch(1);

        p2pManager.setPeerConnectionListener(new api.NetworkManager.PeerConnectionListener() {
            @Override
            public void onPeerConnected(String peerId, String address) {
                latch.countDown();
            }
        });

        p2pManager.start();
    }

    @Test
    void testSendMessageWithoutConnectedPeers() throws Exception {
        p2pManager = new P2PManager(config);
        p2pManager.start();

        p2pManager.sendMessage("non-existent-peer", "Hello");
    }

    @Test
    void testBroadcastWithNoPeers() throws Exception {
        p2pManager = new P2PManager(config);
        p2pManager.start();

        p2pManager.broadcast("Hello everyone");
    }

    @Test
    void testConnectToNonExistentPeer() throws Exception {
        p2pManager = new P2PManager(config);
        p2pManager.start();

        p2pManager.connect("192.168.1.100", 9999);

        Thread.sleep(1000);
    }

    @Test
    void testClose() throws Exception {
        p2pManager = new P2PManager(config);
        p2pManager.start();
        p2pManager.close();
    }

    @Test
    void testCloseWithoutStart() {
        p2pManager = new P2PManager(config);
        p2pManager.close();
    }

    @Test
    void testWithBootstrapPeers() {
        config.addBootstrapPeer("192.168.1.100:9000");
        config.addBootstrapPeer("192.168.1.101:9001");

        p2pManager = new P2PManager(config);
        assertNotNull(p2pManager);
    }

    @Test
    void testWithRelayServer() {
        config.setRelayServerAddress("localhost");
        config.setRelayServerPort(9090);

        p2pManager = new P2PManager(config);
        assertNotNull(p2pManager);
    }

    @Test
    void testMultipleMessageListeners() throws Exception {
        p2pManager = new P2PManager(config);

        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        p2pManager.setMessageListener((peerId, message) -> latch1.countDown());
        p2pManager.setMessageListener((peerId, message) -> latch2.countDown());

        p2pManager.start();
    }

    @Test
    void testMultiplePeerConnectionListeners() throws Exception {
        p2pManager = new P2PManager(config);

        AtomicReference<String> peer1 = new AtomicReference<>();
        AtomicReference<String> peer2 = new AtomicReference<>();

        p2pManager.setPeerConnectionListener(new api.NetworkManager.PeerConnectionListener() {
            @Override
            public void onPeerConnected(String peerId, String address) {
                peer1.set(peerId);
            }
        });

        p2pManager.setPeerConnectionListener(new api.NetworkManager.PeerConnectionListener() {
            @Override
            public void onPeerConnected(String peerId, String address) {
                peer2.set(peerId);
            }
        });

        p2pManager.start();
    }

    @Test
    void testStartStopMultipleTimes() throws Exception {
        config.setLocalPort(0);
        p2pManager = new P2PManager(config);

        p2pManager.start();
        Thread.sleep(500);
        p2pManager.stop();

        Thread.sleep(500);

        p2pManager = new P2PManager(config);
        p2pManager.start();
        Thread.sleep(500);
        p2pManager.stop();
    }

    @Test
    void testConfigWithUPnPDisabled() throws Exception {
        config.setEnableUPnP(false);
        p2pManager = new P2PManager(config);
        p2pManager.start();
        p2pManager.stop();
    }

    @Test
    void testConfigWithUPnPEnabled() throws Exception {
        config.setEnableUPnP(true);
        p2pManager = new P2PManager(config);
        p2pManager.start();
        Thread.sleep(1000);
        p2pManager.stop();
    }
}
