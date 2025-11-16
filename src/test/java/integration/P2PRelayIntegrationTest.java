package integration;

import network.p2p.RelayClient;
import network.p2p.RelayServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class P2PRelayIntegrationTest {

    private RelayServer relayServer;
    private RelayClient client1;
    private RelayClient client2;

    @BeforeEach
    void setUp() throws Exception {
        relayServer = new RelayServer(0);
        relayServer.start();

        Thread.sleep(500);
    }

    @AfterEach
    void tearDown() {
        if (client1 != null) client1.close();
        if (client2 != null) client2.close();
        if (relayServer != null) relayServer.close();
    }

    @Test
    void testRelayServerStart() {
        assertTrue(relayServer.isRunning());
    }

    @Test
    void testSingleClientConnection() throws Exception {
        int port = relayServer.getPort();
        client1 = new RelayClient("localhost", port, "peer-1");

        boolean connected = client1.connect();
        assertTrue(connected);
        assertTrue(client1.isConnected());

        Thread.sleep(500);
        assertEquals(1, relayServer.getRegisteredPeerCount());
    }

    @Test
    void testMultipleClientConnections() throws Exception {
        int port = relayServer.getPort();
        client1 = new RelayClient("localhost", port, "peer-1");
        client2 = new RelayClient("localhost", port, "peer-2");

        assertTrue(client1.connect());
        assertTrue(client2.connect());

        Thread.sleep(500);
        assertEquals(2, relayServer.getRegisteredPeerCount());
    }

    @Test
    void testMessageRelay() throws Exception {
        int port = relayServer.getPort();
        client1 = new RelayClient("localhost", port, "peer-1");
        client2 = new RelayClient("localhost", port, "peer-2");

        CountDownLatch messageLatch = new CountDownLatch(1);
        AtomicReference<String> receivedMessage = new AtomicReference<>();
        AtomicReference<String> senderId = new AtomicReference<>();

        client2.setMessageListener((sender, content) -> {
            senderId.set(sender);
            receivedMessage.set(content);
            messageLatch.countDown();
        });

        assertTrue(client1.connect());
        assertTrue(client2.connect());

        Thread.sleep(1000);

        client1.sendMessageViaRelay("peer-2", "Hello via relay");

        boolean received = messageLatch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Message not received within timeout");
        assertEquals("Hello via relay", receivedMessage.get());
        assertEquals("peer-1", senderId.get());
    }

    @Test
    void testBidirectionalRelay() throws Exception {
        int port = relayServer.getPort();
        client1 = new RelayClient("localhost", port, "peer-1");
        client2 = new RelayClient("localhost", port, "peer-2");

        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        AtomicReference<String> message1 = new AtomicReference<>();
        AtomicReference<String> message2 = new AtomicReference<>();

        client1.setMessageListener((sender, content) -> {
            message1.set(content);
            latch1.countDown();
        });

        client2.setMessageListener((sender, content) -> {
            message2.set(content);
            latch2.countDown();
        });

        assertTrue(client1.connect());
        assertTrue(client2.connect());

        Thread.sleep(1000);

        client1.sendMessageViaRelay("peer-2", "Message from peer 1");
        client2.sendMessageViaRelay("peer-1", "Message from peer 2");

        boolean received1 = latch1.await(5, TimeUnit.SECONDS);
        boolean received2 = latch2.await(5, TimeUnit.SECONDS);

        assertTrue(received1, "Peer 1 did not receive message");
        assertTrue(received2, "Peer 2 did not receive message");

        assertEquals("Message from peer 2", message1.get());
        assertEquals("Message from peer 1", message2.get());
    }

    @Test
    void testSendToNonExistentPeer() throws Exception {
        int port = relayServer.getPort();
        client1 = new RelayClient("localhost", port, "peer-1");

        assertTrue(client1.connect());
        Thread.sleep(1000);

        client1.sendMessageViaRelay("non-existent-peer", "Hello");

        Thread.sleep(1000);
    }

    @Test
    void testClientDisconnection() throws Exception {
        int port = relayServer.getPort();
        client1 = new RelayClient("localhost", port, "peer-1");

        assertTrue(client1.connect());
        Thread.sleep(500);

        assertEquals(1, relayServer.getRegisteredPeerCount());

        client1.close();
        Thread.sleep(1000);

        assertEquals(0, relayServer.getRegisteredPeerCount());
    }

    @Test
    void testRelayServerShutdown() throws Exception {
        int port = relayServer.getPort();
        client1 = new RelayClient("localhost", port, "peer-1");

        assertTrue(client1.connect());
        assertTrue(client1.isConnected());

        relayServer.stop();
        Thread.sleep(1000);

        assertFalse(relayServer.isRunning());
    }

    @Test
    void testMultipleMessages() throws Exception {
        int port = relayServer.getPort();
        client1 = new RelayClient("localhost", port, "peer-1");
        client2 = new RelayClient("localhost", port, "peer-2");

        CountDownLatch messageLatch = new CountDownLatch(3);
        AtomicReference<Integer> messageCount = new AtomicReference<>(0);

        client2.setMessageListener((sender, content) -> {
            messageCount.set(messageCount.get() + 1);
            messageLatch.countDown();
        });

        assertTrue(client1.connect());
        assertTrue(client2.connect());

        Thread.sleep(1000);

        client1.sendMessageViaRelay("peer-2", "Message 1");
        client1.sendMessageViaRelay("peer-2", "Message 2");
        client1.sendMessageViaRelay("peer-2", "Message 3");

        boolean received = messageLatch.await(10, TimeUnit.SECONDS);
        assertTrue(received, "Not all messages received");
        assertEquals(3, messageCount.get().intValue());
    }
}
