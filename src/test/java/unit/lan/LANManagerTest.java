package unit.lan;

import network.lan.LANManager;
import network.protocol.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LANManagerTest {

    private LANManager manager1;
    private LANManager manager2;
    private LANManager manager3;

    @AfterEach
    void tearDown() {
        if (manager1 != null) manager1.close();
        if (manager2 != null) manager2.close();
        if (manager3 != null) manager3.close();
    }

    @Test
    void testManagerCreation() throws IOException {
        manager1 = new LANManager("peer-1", 9100);
        assertNotNull(manager1);
    }

    @Test
    void testManagerStart() throws IOException {
        manager1 = new LANManager("peer-1", 9100);
        manager1.start();
    }

    @Test
    void testPeerConnection() throws IOException, InterruptedException {
        manager1 = new LANManager("peer-1", 9101);
        manager2 = new LANManager("peer-2", 9102);

        CountDownLatch latch = new CountDownLatch(1);

        manager1.start();
        manager2.start();

        Thread connectionChecker = new Thread(() -> {
            try {
                for (int i = 0; i < 30; i++) {
                    if (manager1.getConnectedPeerCount() > 0 && manager2.getConnectedPeerCount() > 0) {
                        latch.countDown();
                        break;
                    }
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        connectionChecker.start();

        boolean connected = latch.await(20, TimeUnit.SECONDS);
        assertTrue(connected, "Peers failed to connect");

        assertTrue(manager1.getConnectedPeerCount() > 0);
        assertTrue(manager2.getConnectedPeerCount() > 0);
    }

    @Test
    void testBroadcastMessage() throws IOException, InterruptedException {
        manager1 = new LANManager("peer-1", 9103);
        manager2 = new LANManager("peer-2", 9104);

        CountDownLatch connectionLatch = new CountDownLatch(1);
        CountDownLatch messageLatch = new CountDownLatch(1);
        AtomicReference<Message> receivedMessage = new AtomicReference<>();

        manager2.addMessageListener(message -> {
            receivedMessage.set(message);
            messageLatch.countDown();
        });

        manager1.start();
        manager2.start();

        Thread connectionChecker = new Thread(() -> {
            try {
                for (int i = 0; i < 30; i++) {
                    if (manager1.getConnectedPeerCount() > 0) {
                        connectionLatch.countDown();
                        break;
                    }
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        connectionChecker.start();

        assertTrue(connectionLatch.await(20, TimeUnit.SECONDS), "Connection timeout");

        Thread.sleep(1000);

        manager1.broadcast("Hello from peer-1");

        boolean messageReceived = messageLatch.await(10, TimeUnit.SECONDS);
        assertTrue(messageReceived, "Message not received");

        assertNotNull(receivedMessage.get());
        assertEquals("Hello from peer-1", receivedMessage.get().getContent());
        assertEquals("peer-1", receivedMessage.get().getSenderId());
        assertEquals(Message.MessageType.TEXT, receivedMessage.get().getType());
    }

    @Test
    void testMultiplePeerConnection() throws IOException, InterruptedException {
        manager1 = new LANManager("peer-1", 9105);
        manager2 = new LANManager("peer-2", 9106);
        manager3 = new LANManager("peer-3", 9107);

        CountDownLatch latch = new CountDownLatch(1);

        manager1.start();
        manager2.start();
        manager3.start();

        Thread connectionChecker = new Thread(() -> {
            try {
                for (int i = 0; i < 40; i++) {
                    int count1 = manager1.getConnectedPeerCount();
                    int count2 = manager2.getConnectedPeerCount();
                    int count3 = manager3.getConnectedPeerCount();

                    if (count1 >= 2 && count2 >= 2 && count3 >= 2) {
                        latch.countDown();
                        break;
                    }
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        connectionChecker.start();

        boolean allConnected = latch.await(25, TimeUnit.SECONDS);
        assertTrue(allConnected, "Not all peers connected");

        assertTrue(manager1.getConnectedPeerCount() >= 2);
        assertTrue(manager2.getConnectedPeerCount() >= 2);
        assertTrue(manager3.getConnectedPeerCount() >= 2);
    }

    @Test
    void testDiscoveredPeers() throws IOException, InterruptedException {
        manager1 = new LANManager("peer-1", 9108);
        manager2 = new LANManager("peer-2", 9109);

        manager1.start();
        manager2.start();

        Thread.sleep(8000);

        assertFalse(manager1.getDiscoveredPeers().isEmpty());
        assertTrue(manager1.getDiscoveredPeers().containsKey("peer-2"));
    }

    @Test
    void testManagerClose() throws IOException {
        manager1 = new LANManager("peer-1", 9110);
        manager1.start();
        manager1.close();
        assertEquals(0, manager1.getConnectedPeerCount());
    }
}
