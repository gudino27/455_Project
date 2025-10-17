package unit.socket;

import network.socket.SocketConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SocketConnectionTest {

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private Socket acceptedSocket;
    private SocketConnection clientConnection;
    private SocketConnection serverConnection;

    @BeforeEach
    void setUp() throws IOException {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        Thread acceptThread = new Thread(() -> {
            try {
                acceptedSocket = serverSocket.accept();
                serverConnection = new SocketConnection(acceptedSocket);
            } catch (IOException e) {
                fail("Failed to accept connection: " + e.getMessage());
            }
        });
        acceptThread.start();

        clientSocket = new Socket("localhost", port);
        clientConnection = new SocketConnection(clientSocket);

        try {
            acceptThread.join(5000);
        } catch (InterruptedException e) {
            fail("Accept thread interrupted");
        }
    }

    @AfterEach
    void tearDown() {
        if (clientConnection != null) clientConnection.close();
        if (serverConnection != null) serverConnection.close();
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
    }

    @Test
    void testConnectionEstablished() {
        assertNotNull(clientConnection);
        assertNotNull(serverConnection);
        assertTrue(clientConnection.isConnected());
        assertTrue(serverConnection.isConnected());
    }

    @Test
    void testSendAndReceiveMessage() throws IOException, InterruptedException {
        String testMessage = "Hello, World!";

        clientConnection.send(testMessage);

        Object received = serverConnection.receiveBlocking();

        assertEquals(testMessage, received);
    }

    @Test
    void testMessageHandler() throws IOException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Object> receivedMessage = new AtomicReference<>();

        serverConnection.setMessageHandler(new SocketConnection.MessageHandler() {
            @Override
            public void onMessage(Object message, SocketConnection connection) {
                receivedMessage.set(message);
                latch.countDown();
            }

            @Override
            public void onError(Exception e, SocketConnection connection) {
                fail("Unexpected error: " + e.getMessage());
            }

            @Override
            public void onDisconnect(SocketConnection connection) {
            }
        });

        String testMessage = "Test Message";
        clientConnection.send(testMessage);

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(testMessage, receivedMessage.get());
    }

    @Test
    void testDisconnectCallback() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        serverConnection.setMessageHandler(new SocketConnection.MessageHandler() {
            @Override
            public void onMessage(Object message, SocketConnection connection) {
            }

            @Override
            public void onError(Exception e, SocketConnection connection) {
            }

            @Override
            public void onDisconnect(SocketConnection connection) {
                latch.countDown();
            }
        });

        clientConnection.close();

        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testGetRemoteAddress() {
        assertNotNull(clientConnection.getRemoteAddress());
        assertTrue(clientConnection.getRemotePort() > 0);
    }

    @Test
    void testClosedConnectionIsNotConnected() {
        assertTrue(clientConnection.isConnected());
        clientConnection.close();
        assertFalse(clientConnection.isConnected());
    }

    @Test
    void testMultipleMessages() throws IOException, InterruptedException {
        int messageCount = 10;
        CountDownLatch latch = new CountDownLatch(messageCount);

        serverConnection.setMessageHandler(new SocketConnection.MessageHandler() {
            @Override
            public void onMessage(Object message, SocketConnection connection) {
                latch.countDown();
            }

            @Override
            public void onError(Exception e, SocketConnection connection) {
            }

            @Override
            public void onDisconnect(SocketConnection connection) {
            }
        });

        for (int i = 0; i < messageCount; i++) {
            clientConnection.send("Message " + i);
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }
}
