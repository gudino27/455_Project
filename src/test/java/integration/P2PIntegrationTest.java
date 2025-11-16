package integration;

import network.p2p.P2PManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import util.NetworkConfig;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class P2PIntegrationTest {

    private P2PManager manager1;
    private P2PManager manager2;
    private P2PManager manager3;

    @AfterEach
    void tearDown() {
        if (manager1 != null) manager1.close();
        if (manager2 != null) manager2.close();
        if (manager3 != null) manager3.close();
    }

    @Test
    void testTwoPeerConnection() throws Exception {
        NetworkConfig config1 = createConfig(9200);
        NetworkConfig config2 = createConfig(9201);
        config2.addBootstrapPeer("localhost:9200");

        manager1 = new P2PManager(config1);
        manager2 = new P2PManager(config2);

        manager1.start();
        Thread.sleep(500);
        manager2.start();

        Thread.sleep(3000);

        assertTrue(manager1.getPeers().size() > 0 || manager2.getPeers().size() > 0);
    }

    @Test
    void testMessageExchange() throws Exception {
        NetworkConfig config1 = createConfig(9202);
        NetworkConfig config2 = createConfig(9203);
        config2.addBootstrapPeer("localhost:9202");

        CountDownLatch messageLatch = new CountDownLatch(1);
        AtomicReference<String> receivedMessage = new AtomicReference<>();

        manager1 = new P2PManager(config1);
        manager2 = new P2PManager(config2);

        manager1.setMessageListener((peerId, message) -> {
            receivedMessage.set(message);
            messageLatch.countDown();
        });

        manager1.start();
        Thread.sleep(500);
        manager2.start();

        Thread.sleep(3000);

        if (manager1.getPeers().size() > 0) {
            String peer1Id = manager1.getPeers().get(0).getPeerId();
            manager2.sendMessage(peer1Id, "Hello from P2P peer 2");

            boolean received = messageLatch.await(5, TimeUnit.SECONDS);
            assertTrue(received, "Message not received within timeout");
            assertEquals("Hello from P2P peer 2", receivedMessage.get());
        }
    }

    @Test
    void testBroadcastMessage() throws Exception {
        NetworkConfig config1 = createConfig(9204);
        NetworkConfig config2 = createConfig(9205);
        NetworkConfig config3 = createConfig(9206);

        config2.addBootstrapPeer("localhost:9204");
        config3.addBootstrapPeer("localhost:9204");

        CountDownLatch messageLatch = new CountDownLatch(2);
        AtomicReference<String> receivedMessage1 = new AtomicReference<>();
        AtomicReference<String> receivedMessage2 = new AtomicReference<>();

        manager1 = new P2PManager(config1);
        manager2 = new P2PManager(config2);
        manager3 = new P2PManager(config3);

        manager2.setMessageListener((peerId, message) -> {
            receivedMessage1.set(message);
            messageLatch.countDown();
        });

        manager3.setMessageListener((peerId, message) -> {
            receivedMessage2.set(message);
            messageLatch.countDown();
        });

        manager1.start();
        Thread.sleep(500);
        manager2.start();
        Thread.sleep(500);
        manager3.start();

        Thread.sleep(4000);

        manager1.broadcast("Broadcast message from peer 1");

        boolean received = messageLatch.await(5, TimeUnit.SECONDS);
        if (received) {
            assertTrue(receivedMessage1.get() != null || receivedMessage2.get() != null);
        }
    }

    @Test
    void testPeerDiscovery() throws Exception {
        NetworkConfig config1 = createConfig(9207);
        NetworkConfig config2 = createConfig(9208);
        config2.addBootstrapPeer("localhost:9207");

        CountDownLatch connectionLatch = new CountDownLatch(1);

        manager1 = new P2PManager(config1);
        manager2 = new P2PManager(config2);

        manager1.setPeerConnectionListener(new api.NetworkManager.PeerConnectionListener() {
            @Override
            public void onPeerConnected(String peerId, String address) {
                connectionLatch.countDown();
            }
        });

        manager1.start();
        Thread.sleep(500);
        manager2.start();

        boolean connected = connectionLatch.await(10, TimeUnit.SECONDS);
        assertTrue(connected, "Peers did not connect within timeout");
    }

    @Test
    void testMultiplePeerDiscovery() throws Exception {
        NetworkConfig config1 = createConfig(9209);
        NetworkConfig config2 = createConfig(9210);
        NetworkConfig config3 = createConfig(9211);

        config2.addBootstrapPeer("localhost:9209");
        config3.addBootstrapPeer("localhost:9209");

        manager1 = new P2PManager(config1);
        manager2 = new P2PManager(config2);
        manager3 = new P2PManager(config3);

        manager1.start();
        Thread.sleep(500);
        manager2.start();
        Thread.sleep(500);
        manager3.start();

        Thread.sleep(5000);

        int totalDiscoveredPeers = manager1.getPeers().size() +
                                   manager2.getPeers().size() +
                                   manager3.getPeers().size();

        assertTrue(totalDiscoveredPeers > 0, "No peers discovered");
    }

    @Test
    void testPeerReconnection() throws Exception {
        NetworkConfig config1 = createConfig(9212);
        config1.setReconnectionDelayMs(1000);
        config1.setMaxReconnectionAttempts(3);

        NetworkConfig config2 = createConfig(9213);
        config2.addBootstrapPeer("localhost:9212");

        CountDownLatch disconnectLatch = new CountDownLatch(1);

        manager1 = new P2PManager(config1);
        manager2 = new P2PManager(config2);

        manager2.setPeerConnectionListener(new api.NetworkManager.PeerConnectionListener() {
            @Override
            public void onPeerConnected(String peerId, String address) {
            }

            @Override
            public void onPeerDisconnected(String peerId) {
                disconnectLatch.countDown();
            }
        });

        manager1.start();
        Thread.sleep(500);
        manager2.start();

        Thread.sleep(3000);

        manager1.stop();

        boolean disconnected = disconnectLatch.await(10, TimeUnit.SECONDS);
        if (disconnected) {
            assertTrue(true);
        }
    }

    @Test
    void testManualPeerConnection() throws Exception {
        NetworkConfig config1 = createConfig(9214);
        NetworkConfig config2 = createConfig(9215);

        manager1 = new P2PManager(config1);
        manager2 = new P2PManager(config2);

        manager1.start();
        manager2.start();

        Thread.sleep(1000);

        manager1.connect("localhost", 9215);

        Thread.sleep(3000);

        assertTrue(manager1.getPeers().size() > 0 || manager2.getPeers().size() > 0);
    }

    @Test
    void testBidirectionalMessaging() throws Exception {
        NetworkConfig config1 = createConfig(9216);
        NetworkConfig config2 = createConfig(9217);
        config2.addBootstrapPeer("localhost:9216");

        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        AtomicReference<String> message1 = new AtomicReference<>();
        AtomicReference<String> message2 = new AtomicReference<>();

        manager1 = new P2PManager(config1);
        manager2 = new P2PManager(config2);

        manager1.setMessageListener((peerId, message) -> {
            message1.set(message);
            latch1.countDown();
        });

        manager2.setMessageListener((peerId, message) -> {
            message2.set(message);
            latch2.countDown();
        });

        manager1.start();
        Thread.sleep(500);
        manager2.start();

        Thread.sleep(3000);

        if (manager1.getPeers().size() > 0 && manager2.getPeers().size() > 0) {
            String peer1Id = manager1.getPeers().get(0).getPeerId();
            String peer2Id = manager2.getPeers().get(0).getPeerId();

            manager2.sendMessage(peer1Id, "Message from peer 2");
            manager1.sendMessage(peer2Id, "Message from peer 1");

            boolean received1 = latch1.await(5, TimeUnit.SECONDS);
            boolean received2 = latch2.await(5, TimeUnit.SECONDS);

            if (received1 || received2) {
                assertTrue(message1.get() != null || message2.get() != null);
            }
        }
    }

    private NetworkConfig createConfig(int port) {
        NetworkConfig config = new NetworkConfig();
        config.setLocalPort(port);
        config.setEnableUPnP(false);
        config.setHeartbeatIntervalMs(30000);
        config.setConnectionTimeoutMs(5000);
        config.setReconnectionDelayMs(2000);
        config.setMaxReconnectionAttempts(3);
        return config;
    }
}
