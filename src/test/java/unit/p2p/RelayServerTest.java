package unit.p2p;

import network.p2p.RelayServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class RelayServerTest {

    private RelayServer relayServer;

    @AfterEach
    void tearDown() {
        if (relayServer != null) {
            relayServer.close();
        }
    }

    @Test
    void testServerCreation() {
        relayServer = new RelayServer(0);
        assertNotNull(relayServer);
        assertFalse(relayServer.isRunning());
    }

    @Test
    void testServerStart() throws IOException {
        relayServer = new RelayServer(0);
        relayServer.start();
        assertTrue(relayServer.isRunning());
    }

    @Test
    void testServerStop() throws IOException {
        relayServer = new RelayServer(0);
        relayServer.start();
        assertTrue(relayServer.isRunning());

        relayServer.stop();
        assertFalse(relayServer.isRunning());
    }

    @Test
    void testInitialPeerCount() {
        relayServer = new RelayServer(0);
        assertEquals(0, relayServer.getRegisteredPeerCount());
    }

    @Test
    void testStartAlreadyRunningServer() throws IOException {
        relayServer = new RelayServer(0);
        relayServer.start();
        assertTrue(relayServer.isRunning());

        relayServer.start();
        assertTrue(relayServer.isRunning());
    }

    @Test
    void testStopAlreadyStoppedServer() throws IOException {
        relayServer = new RelayServer(0);
        relayServer.start();
        relayServer.stop();
        assertFalse(relayServer.isRunning());

        relayServer.stop();
        assertFalse(relayServer.isRunning());
    }

    @Test
    void testCloseServer() throws IOException {
        relayServer = new RelayServer(0);
        relayServer.start();
        assertTrue(relayServer.isRunning());

        relayServer.close();
        assertFalse(relayServer.isRunning());
    }

    @Test
    void testMultipleStartStop() throws Exception {
        relayServer = new RelayServer(0);

        relayServer.start();
        assertTrue(relayServer.isRunning());
        relayServer.stop();
        assertFalse(relayServer.isRunning());

        Thread.sleep(500);

        relayServer = new RelayServer(0);
        relayServer.start();
        assertTrue(relayServer.isRunning());
        relayServer.stop();
        assertFalse(relayServer.isRunning());
    }
}
