package unit.socket;

import network.socket.SocketConnection;
import network.socket.SocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SocketServerTest {

    private SocketServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void testServerCreation() throws IOException {
        server = new SocketServer(0);
        assertNotNull(server);
        assertTrue(server.getPort() >= 0);
    }

    @Test
    void testServerStart() throws IOException {
        server = new SocketServer(0);
        assertFalse(server.isRunning());
        server.start();
        assertTrue(server.isRunning());
    }

    @Test
    void testAcceptConnection() throws IOException, InterruptedException {
        server = new SocketServer(0);
        int port = server.getPort();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<SocketConnection> acceptedConnection = new AtomicReference<>();

        server.setConnectionHandler(connection -> {
            acceptedConnection.set(connection);
            latch.countDown();
        });

        server.start();

        Socket client = new Socket("localhost", port);

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(acceptedConnection.get());
        assertTrue(acceptedConnection.get().isConnected());

        client.close();
        acceptedConnection.get().close();
    }

    @Test
    void testMultipleConnections() throws IOException, InterruptedException {
        server = new SocketServer(0);
        int port = server.getPort();

        int connectionCount = 5;
        CountDownLatch latch = new CountDownLatch(connectionCount);

        server.setConnectionHandler(connection -> latch.countDown());
        server.start();

        Socket[] clients = new Socket[connectionCount];
        for (int i = 0; i < connectionCount; i++) {
            clients[i] = new Socket("localhost", port);
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));

        for (Socket client : clients) {
            client.close();
        }
    }

    @Test
    void testServerClose() throws IOException, InterruptedException {
        server = new SocketServer(0);
        int port = server.getPort();

        server.start();
        assertTrue(server.isRunning());

        server.close();
        assertFalse(server.isRunning());

        Thread.sleep(500);

        assertThrows(IOException.class, () -> {
            try (Socket testSocket = new Socket("localhost", port)) {
                // Socket automatically closed by try-with-resources
            }
        });
    }

    @Test
    void testNoConnectionHandlerSet() throws IOException, InterruptedException {
        server = new SocketServer(0);
        int port = server.getPort();

        server.start();

        Socket client = new Socket("localhost", port);

        Thread.sleep(1000);

        client.close();
    }
}
