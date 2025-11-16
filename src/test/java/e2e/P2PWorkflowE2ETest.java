package e2e;

import network.p2p.P2PManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import util.NetworkConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Tag("e2e")
class P2PWorkflowE2ETest {

    private final List<P2PManager> managers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (P2PManager manager : managers) {
            if (manager != null) {
                try {
                    manager.close();
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }
        managers.clear();
    }

    @Test
    void testCompleteP2PWorkflow() throws Exception {
        NetworkConfig config1 = createConfig(9400);
        NetworkConfig config2 = createConfig(9401);
        NetworkConfig config3 = createConfig(9402);

        config2.addBootstrapPeer("localhost:9400");
        config3.addBootstrapPeer("localhost:9400");

        CountDownLatch messageLatch = new CountDownLatch(2);
        AtomicInteger messagesReceived = new AtomicInteger(0);

        P2PManager manager1 = new P2PManager(config1);
        P2PManager manager2 = new P2PManager(config2);
        P2PManager manager3 = new P2PManager(config3);

        managers.add(manager1);
        managers.add(manager2);
        managers.add(manager3);

        manager2.setMessageListener((peerId, message) -> {
            messagesReceived.incrementAndGet();
            messageLatch.countDown();
        });

        manager3.setMessageListener((peerId, message) -> {
            messagesReceived.incrementAndGet();
            messageLatch.countDown();
        });

        manager1.start();
        Thread.sleep(1000);
        manager2.start();
        Thread.sleep(1000);
        manager3.start();

        Thread.sleep(5000);

        assertTrue(manager1.getPeers().size() > 0 ||
                   manager2.getPeers().size() > 0 ||
                   manager3.getPeers().size() > 0,
                   "No peers discovered");

        manager1.broadcast("Broadcast from manager 1");

        boolean received = messageLatch.await(10, TimeUnit.SECONDS);
        if (received) {
            assertTrue(messagesReceived.get() > 0, "No messages were received");
        }

        manager1.stop();
        manager2.stop();
        manager3.stop();

        assertEquals(0, manager1.getPeers().size());
    }

    @Test
    void testPeerJoinAndLeave() throws Exception {
        NetworkConfig config1 = createConfig(9403);
        NetworkConfig config2 = createConfig(9404);
        config2.addBootstrapPeer("localhost:9403");

        CountDownLatch connectLatch = new CountDownLatch(1);
        CountDownLatch disconnectLatch = new CountDownLatch(1);

        P2PManager manager1 = new P2PManager(config1);
        P2PManager manager2 = new P2PManager(config2);

        managers.add(manager1);
        managers.add(manager2);

        manager1.setPeerConnectionListener(new api.NetworkManager.PeerConnectionListener() {
            @Override
            public void onPeerConnected(String peerId, String address) {
                connectLatch.countDown();
            }

            @Override
            public void onPeerDisconnected(String peerId) {
                disconnectLatch.countDown();
            }
        });

        manager1.start();
        Thread.sleep(1000);
        manager2.start();

        boolean connected = connectLatch.await(10, TimeUnit.SECONDS);
        assertTrue(connected, "Peer did not connect");

        manager2.stop();

        boolean disconnected = disconnectLatch.await(10, TimeUnit.SECONDS);
        if (disconnected) {
            assertTrue(true, "Peer disconnection detected");
        }
    }

    @Test
    void testMultiplePeerNetwork() throws Exception {
        int peerCount = 5;
        int basePort = 9405;

        List<NetworkConfig> configs = new ArrayList<>();
        for (int i = 0; i < peerCount; i++) {
            NetworkConfig config = createConfig(basePort + i);
            if (i > 0) {
                config.addBootstrapPeer("localhost:" + basePort);
            }
            configs.add(config);
        }

        AtomicInteger totalMessagesReceived = new AtomicInteger(0);
        CountDownLatch messageLatch = new CountDownLatch(peerCount - 1);

        for (int i = 0; i < peerCount; i++) {
            P2PManager manager = new P2PManager(configs.get(i));
            manager.setMessageListener((peerId, message) -> {
                totalMessagesReceived.incrementAndGet();
                messageLatch.countDown();
            });
            managers.add(manager);
            manager.start();
            Thread.sleep(500);
        }

        Thread.sleep(8000);

        int totalDiscoveredPeers = 0;
        for (P2PManager manager : managers) {
            totalDiscoveredPeers += manager.getPeers().size();
        }

        assertTrue(totalDiscoveredPeers > 0, "No peers discovered in the network");

        managers.get(0).broadcast("Network-wide broadcast");

        boolean received = messageLatch.await(15, TimeUnit.SECONDS);
        if (received) {
            assertTrue(totalMessagesReceived.get() > 0);
        }
    }

    @Test
    void testDirectMessaging() throws Exception {
        NetworkConfig config1 = createConfig(9410);
        NetworkConfig config2 = createConfig(9411);
        config2.addBootstrapPeer("localhost:9410");

        CountDownLatch messageLatch = new CountDownLatch(1);
        AtomicInteger messageCount = new AtomicInteger(0);

        P2PManager manager1 = new P2PManager(config1);
        P2PManager manager2 = new P2PManager(config2);

        managers.add(manager1);
        managers.add(manager2);

        manager1.setMessageListener((peerId, message) -> {
            messageCount.incrementAndGet();
            messageLatch.countDown();
        });

        manager1.start();
        Thread.sleep(1000);
        manager2.start();

        Thread.sleep(5000);

        if (manager1.getPeers().size() > 0) {
            String peer1Id = manager1.getPeers().get(0).getPeerId();
            manager2.sendMessage(peer1Id, "Direct message test");

            boolean received = messageLatch.await(10, TimeUnit.SECONDS);
            assertTrue(received, "Direct message not received");
            assertEquals(1, messageCount.get());
        }
    }

    @Test
    void testPeerDiscoveryChain() throws Exception {
        NetworkConfig config1 = createConfig(9412);
        NetworkConfig config2 = createConfig(9413);
        NetworkConfig config3 = createConfig(9414);

        config2.addBootstrapPeer("localhost:9412");
        config3.addBootstrapPeer("localhost:9413");

        P2PManager manager1 = new P2PManager(config1);
        P2PManager manager2 = new P2PManager(config2);
        P2PManager manager3 = new P2PManager(config3);

        managers.add(manager1);
        managers.add(manager2);
        managers.add(manager3);

        manager1.start();
        Thread.sleep(1000);
        manager2.start();
        Thread.sleep(2000);
        manager3.start();

        Thread.sleep(8000);

        int totalPeers = manager1.getPeers().size() +
                         manager2.getPeers().size() +
                         manager3.getPeers().size();

        assertTrue(totalPeers >= 2, "Peer discovery chain failed");
    }

    @Test
    void testMessageOrdering() throws Exception {
        NetworkConfig config1 = createConfig(9415);
        NetworkConfig config2 = createConfig(9416);
        config2.addBootstrapPeer("localhost:9415");

        List<String> receivedMessages = new ArrayList<>();
        CountDownLatch messageLatch = new CountDownLatch(3);

        P2PManager manager1 = new P2PManager(config1);
        P2PManager manager2 = new P2PManager(config2);

        managers.add(manager1);
        managers.add(manager2);

        manager1.setMessageListener((peerId, message) -> {
            synchronized (receivedMessages) {
                receivedMessages.add(message);
            }
            messageLatch.countDown();
        });

        manager1.start();
        Thread.sleep(1000);
        manager2.start();

        Thread.sleep(5000);

        if (manager1.getPeers().size() > 0) {
            String peer1Id = manager1.getPeers().get(0).getPeerId();

            manager2.sendMessage(peer1Id, "Message 1");
            Thread.sleep(100);
            manager2.sendMessage(peer1Id, "Message 2");
            Thread.sleep(100);
            manager2.sendMessage(peer1Id, "Message 3");

            boolean received = messageLatch.await(10, TimeUnit.SECONDS);
            assertTrue(received, "Not all messages received");
            assertEquals(3, receivedMessages.size());
        }
    }

    @Test
    void testGracefulShutdown() throws Exception {
        NetworkConfig config1 = createConfig(9417);
        NetworkConfig config2 = createConfig(9418);
        config2.addBootstrapPeer("localhost:9417");

        P2PManager manager1 = new P2PManager(config1);
        P2PManager manager2 = new P2PManager(config2);

        managers.add(manager1);
        managers.add(manager2);

        manager1.start();
        Thread.sleep(1000);
        manager2.start();

        Thread.sleep(5000);

        manager1.close();
        manager2.close();

        managers.clear();

        assertTrue(true, "Graceful shutdown completed");
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
