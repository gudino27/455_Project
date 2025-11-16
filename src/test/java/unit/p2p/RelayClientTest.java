package unit.p2p;

import network.p2p.RelayClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RelayClientTest {

    private RelayClient relayClient;

    @AfterEach
    void tearDown() {
        if (relayClient != null) {
            relayClient.close();
        }
    }

    @Test
    void testClientCreation() {
        relayClient = new RelayClient("localhost", 9999, "test-peer");
        assertNotNull(relayClient);
        assertFalse(relayClient.isConnected());
    }

    @Test
    void testConnectToNonExistentServer() {
        relayClient = new RelayClient("localhost", 9999, "test-peer");
        boolean connected = relayClient.connect();
        assertFalse(connected);
        assertFalse(relayClient.isConnected());
    }

    @Test
    void testSendMessageWithoutConnection() {
        relayClient = new RelayClient("localhost", 9999, "test-peer");
        assertThrows(IOException.class, () -> {
            relayClient.sendMessageViaRelay("recipient", "Hello");
        });
    }

    @Test
    void testSetMessageListener() {
        relayClient = new RelayClient("localhost", 9999, "test-peer");
        AtomicReference<String> receivedMessage = new AtomicReference<>();

        relayClient.setMessageListener((senderId, content) -> {
            receivedMessage.set(content);
        });

        assertNotNull(receivedMessage);
    }

    @Test
    void testCloseWithoutConnection() {
        relayClient = new RelayClient("localhost", 9999, "test-peer");
        relayClient.close();
        assertFalse(relayClient.isConnected());
    }

    @Test
    void testMultipleCloseCalls() {
        relayClient = new RelayClient("localhost", 9999, "test-peer");
        relayClient.close();
        relayClient.close();
        assertFalse(relayClient.isConnected());
    }

    @Test
    void testIsConnectedBeforeConnect() {
        relayClient = new RelayClient("localhost", 9999, "test-peer");
        assertFalse(relayClient.isConnected());
    }
}
