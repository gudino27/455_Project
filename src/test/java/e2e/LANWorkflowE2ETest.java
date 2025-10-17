package e2e;

import network.lan.LANManager;
import network.protocol.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("e2e")
class LANWorkflowE2ETest {

    private final List<LANManager> managers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (LANManager manager : managers) {
            try {
                manager.close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
        managers.clear();
    }

    private LANManager createAndStartManager(String peerId, int port) throws IOException {
        LANManager manager = new LANManager(peerId, port);
        manager.start();
        managers.add(manager);
        return manager;
    }

    private void waitForConnections(LANManager manager, int expectedCount, int timeoutSeconds) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (manager.getConnectedPeerCount() >= expectedCount) {
                return;
            }
            Thread.sleep(500);
        }

        fail("Timeout waiting for " + expectedCount + " connections. Got: " + manager.getConnectedPeerCount());
    }

    @Test
    void testCompleteLANNetworkSetup() throws IOException, InterruptedException {
        System.out.println("=== E2E Test: Complete LAN Network Setup ===");

        LANManager peer1 = createAndStartManager("e2e-peer-1", 11001);
        LANManager peer2 = createAndStartManager("e2e-peer-2", 11002);
        LANManager peer3 = createAndStartManager("e2e-peer-3", 11003);
        LANManager peer4 = createAndStartManager("e2e-peer-4", 11004);

        System.out.println("All peers started, waiting for discovery and connections...");

        waitForConnections(peer1, 3, 30);
        waitForConnections(peer2, 3, 30);
        waitForConnections(peer3, 3, 30);
        waitForConnections(peer4, 3, 30);

        System.out.println("All peers connected successfully");

        assertEquals(3, peer1.getConnectedPeerCount());
        assertEquals(3, peer2.getConnectedPeerCount());
        assertEquals(3, peer3.getConnectedPeerCount());
        assertEquals(3, peer4.getConnectedPeerCount());

        System.out.println("=== Test Passed ===");
    }

    @Test
    void testCompleteMessageWorkflow() throws IOException, InterruptedException {
        System.out.println("=== E2E Test: Complete Message Workflow ===");

        LANManager sender = createAndStartManager("e2e-sender", 11005);
        LANManager receiver1 = createAndStartManager("e2e-receiver-1", 11006);
        LANManager receiver2 = createAndStartManager("e2e-receiver-2", 11007);

        CountDownLatch messageLatch = new CountDownLatch(2);
        CopyOnWriteArrayList<Message> receivedMessages = new CopyOnWriteArrayList<>();

        LANManager.MessageListener listener = message -> {
            System.out.println("Message received: " + message);
            receivedMessages.add(message);
            messageLatch.countDown();
        };

        receiver1.addMessageListener(listener);
        receiver2.addMessageListener(listener);

        System.out.println("Waiting for peer discovery and connections...");
        waitForConnections(sender, 2, 30);

        Thread.sleep(2000);

        System.out.println("Broadcasting message...");
        sender.broadcast("E2E Test Message");

        boolean received = messageLatch.await(15, TimeUnit.SECONDS);
        assertTrue(received, "Message not received by all peers");

        assertEquals(2, receivedMessages.size());
        receivedMessages.forEach(msg -> {
            assertEquals("E2E Test Message", msg.getContent());
            assertEquals("e2e-sender", msg.getSenderId());
            assertEquals(Message.MessageType.TEXT, msg.getType());
        });

        System.out.println("=== Test Passed ===");
    }

    @Test
    void testNetworkResilience() throws IOException, InterruptedException {
        System.out.println("=== E2E Test: Network Resilience ===");

        LANManager peer1 = createAndStartManager("resilience-1", 11008);
        LANManager peer2 = createAndStartManager("resilience-2", 11009);
        LANManager peer3 = createAndStartManager("resilience-3", 11010);

        System.out.println("Waiting for initial connections...");
        waitForConnections(peer1, 2, 30);

        System.out.println("Initial network established. Disconnecting one peer...");
        peer2.close();
        managers.remove(peer2);

        Thread.sleep(3000);

        System.out.println("Testing if remaining peers can still communicate...");
        CountDownLatch latch = new CountDownLatch(1);
        peer3.addMessageListener(msg -> latch.countDown());

        Thread.sleep(1000);
        peer1.broadcast("Test after disconnect");

        boolean received = latch.await(10, TimeUnit.SECONDS);
        assertTrue(received, "Communication failed after peer disconnect");

        System.out.println("Reconnecting peer...");
        peer2 = createAndStartManager("resilience-2", 11009);

        Thread.sleep(10000);

        assertTrue(peer1.getDiscoveredPeers().containsKey("resilience-2"),
            "Peer should be rediscovered");

        System.out.println("=== Test Passed ===");
    }

    @Test
    void testHighLoadMessaging() throws IOException, InterruptedException {
        System.out.println("=== E2E Test: High Load Messaging ===");

        LANManager sender = createAndStartManager("load-sender", 11011);
        LANManager receiver = createAndStartManager("load-receiver", 11012);

        int messageCount = 50;
        CountDownLatch latch = new CountDownLatch(messageCount);

        receiver.addMessageListener(msg -> latch.countDown());

        System.out.println("Waiting for connection...");
        waitForConnections(sender, 1, 30);

        Thread.sleep(1000);

        System.out.println("Sending " + messageCount + " messages...");
        for (int i = 0; i < messageCount; i++) {
            sender.broadcast("Message " + i);
            Thread.sleep(50);
        }

        boolean allReceived = latch.await(30, TimeUnit.SECONDS);
        assertTrue(allReceived, "Not all messages received under load");

        System.out.println("All " + messageCount + " messages received successfully");
        System.out.println("=== Test Passed ===");
    }

    @Test
    void testGracefulShutdown() throws IOException, InterruptedException {
        System.out.println("=== E2E Test: Graceful Shutdown ===");

        LANManager peer1 = createAndStartManager("shutdown-1", 11013);
        LANManager peer2 = createAndStartManager("shutdown-2", 11014);

        System.out.println("Establishing connections...");
        waitForConnections(peer1, 1, 30);

        System.out.println("Connections established. Testing graceful shutdown...");

        assertDoesNotThrow(() -> {
            peer1.close();
            peer2.close();
        });

        managers.clear();

        System.out.println("=== Test Passed ===");
    }
}
