package integration;

import network.lan.LANManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LANIntegrationTest {

    private final List<LANManager> managers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (LANManager manager : managers) {
            manager.close();
        }
        managers.clear();
    }

    private LANManager createManager(String peerId, int port) throws IOException {
        LANManager manager = new LANManager(port);
        managers.add(manager);
        return manager;
    }

    private void waitForConnections(LANManager manager, int expectedCount, int timeoutSeconds) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        Thread checker = new Thread(() -> {
            try {
                for (int i = 0; i < timeoutSeconds * 2; i++) {
                    if (manager.getPeers().size() >= expectedCount) {
                        latch.countDown();
                        break;
                    }
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        checker.start();

        assertTrue(latch.await(timeoutSeconds, TimeUnit.SECONDS),
            "Expected " + expectedCount + " connections but got " + manager.getPeers().size());
    }

    @Test
    void testFullLANWorkflow() throws IOException, InterruptedException {
        LANManager peer1 = createManager("peer-1", 10001);
        LANManager peer2 = createManager("peer-2", 10002);
        LANManager peer3 = createManager("peer-3", 10003);

        CopyOnWriteArrayList<String> peer2Messages = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<String> peer3Messages = new CopyOnWriteArrayList<>();

        peer2.setMessageListener((peerId, message) -> peer2Messages.add(message));
        peer3.setMessageListener((peerId, message) -> peer3Messages.add(message));

        peer1.start();
        peer2.start();
        peer3.start();

        waitForConnections(peer1, 2, 25);
        waitForConnections(peer2, 2, 25);
        waitForConnections(peer3, 2, 25);

        Thread.sleep(1000);

        peer1.broadcast("Hello from peer-1");

        Thread.sleep(3000);

        assertTrue(peer2Messages.size() > 0, "Peer 2 should receive message");
        assertTrue(peer3Messages.size() > 0, "Peer 3 should receive message");

        assertEquals("Hello from peer-1", peer2Messages.get(0));
    }

    @Test
    void testPeerJoinLeave() throws IOException, InterruptedException {
        LANManager peer1 = createManager("peer-1", 10004);
        LANManager peer2 = createManager("peer-2", 10005);

        peer1.start();
        peer2.start();

        waitForConnections(peer1, 1, 25);

        assertEquals(1, peer1.getPeers().size());
        assertEquals(1, peer2.getPeers().size());

        peer2.close();
        managers.remove(peer2);

        Thread.sleep(3000);

        assertTrue(peer1.getPeers().size() < 1 || peer1.getPeers().stream().noneMatch(p -> p.getPeerId().equals("peer-2")));
    }

    @Test
    void testMessageBroadcastToMultiplePeers() throws IOException, InterruptedException {
        LANManager peer1 = createManager("peer-1", 10006);
        LANManager peer2 = createManager("peer-2", 10007);
        LANManager peer3 = createManager("peer-3", 10008);
        LANManager peer4 = createManager("peer-4", 10009);

        CountDownLatch messageLatch = new CountDownLatch(3);
        CopyOnWriteArrayList<String> receivedMessages = new CopyOnWriteArrayList<>();

        api.NetworkManager.MessageListener listener = (peerId, message) -> {
            receivedMessages.add(message);
            messageLatch.countDown();
        };

        peer2.setMessageListener(listener);
        peer3.setMessageListener(listener);
        peer4.setMessageListener(listener);

        peer1.start();
        peer2.start();
        peer3.start();
        peer4.start();

        waitForConnections(peer1, 3, 30);

        Thread.sleep(1000);

        peer1.broadcast("Broadcast message");

        boolean allReceived = messageLatch.await(10, TimeUnit.SECONDS);
        assertTrue(allReceived, "Not all peers received the broadcast");
        assertEquals(3, receivedMessages.size());
    }

    @Test
    void testBidirectionalCommunication() throws IOException, InterruptedException {
        LANManager peer1 = createManager("peer-1", 10010);
        LANManager peer2 = createManager("peer-2", 10011);

        CountDownLatch latch = new CountDownLatch(2);
        CopyOnWriteArrayList<String> peer1Messages = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<String> peer2Messages = new CopyOnWriteArrayList<>();

        peer1.setMessageListener((peerId, message) -> {
            peer1Messages.add(message);
            latch.countDown();
        });

        peer2.setMessageListener((peerId, message) -> {
            peer2Messages.add(message);
            latch.countDown();
        });

        peer1.start();
        peer2.start();

        waitForConnections(peer1, 1, 25);
        waitForConnections(peer2, 1, 25);

        Thread.sleep(1000);

        peer1.broadcast("Message from peer-1");
        peer2.broadcast("Message from peer-2");

        boolean bothReceived = latch.await(10, TimeUnit.SECONDS);
        assertTrue(bothReceived, "Bidirectional communication failed");

        assertTrue(peer1Messages.stream().anyMatch(m -> m.equals("Message from peer-2")));
        assertTrue(peer2Messages.stream().anyMatch(m -> m.equals("Message from peer-1")));
    }

    @Test
    void testReconnectionAfterDisconnect() throws IOException, InterruptedException {
        LANManager peer1 = createManager("peer-1", 10012);
        LANManager peer2 = createManager("peer-2", 10013);

        peer1.start();
        peer2.start();

        waitForConnections(peer1, 1, 25);

        peer2.close();
        managers.remove(peer2);

        Thread.sleep(2000);

        peer2 = createManager("peer-2", 10013);
        peer2.start();

        Thread.sleep(8000);

        assertTrue(peer1.getPeers().stream().anyMatch(p -> p.getPeerId().equals("peer-2")));
    }

    @Test
    void testMultipleMessages() throws IOException, InterruptedException {
        LANManager peer1 = createManager("peer-1", 10014);
        LANManager peer2 = createManager("peer-2", 10015);

        int messageCount = 10;
        CountDownLatch latch = new CountDownLatch(messageCount);

        peer2.setMessageListener((peerId, message) -> latch.countDown());

        peer1.start();
        peer2.start();

        waitForConnections(peer1, 1, 25);

        Thread.sleep(1000);

        for (int i = 0; i < messageCount; i++) {
            peer1.broadcast("Message " + i);
            Thread.sleep(100);
        }

        boolean allReceived = latch.await(15, TimeUnit.SECONDS);
        assertTrue(allReceived, "Not all messages received");
    }
}
