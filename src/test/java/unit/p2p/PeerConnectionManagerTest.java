package unit.p2p;

import network.lan.PeerInfo;
import network.p2p.PeerConnectionManager;
import network.socket.SocketConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import util.NetworkConfig;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PeerConnectionManagerTest {

    private PeerConnectionManager connectionManager;
    private NetworkConfig config;

    @BeforeEach
    void setUp() {
        config = new NetworkConfig();
        config.setLocalPort(9000);
        config.setHeartbeatIntervalMs(1000);
        config.setConnectionTimeoutMs(5000);
        config.setReconnectionDelayMs(500);
        config.setMaxReconnectionAttempts(3);
        connectionManager = new PeerConnectionManager(config);
    }

    @AfterEach
    void tearDown() {
        if (connectionManager != null) {
            connectionManager.close();
        }
    }

    @Test
    void testConnectionManagerCreation() {
        assertNotNull(connectionManager);
    }

    @Test
    void testConnectToPeer() {
        PeerInfo peerInfo = new PeerInfo("peer-1", "192.168.1.100", 9000);
        SocketConnection mockConnection = Mockito.mock(SocketConnection.class);

        boolean result = connectionManager.connect(peerInfo, mockConnection,
            PeerConnectionManager.ConnectionType.DIRECT);

        assertTrue(result);
        assertEquals(PeerConnectionManager.ConnectionState.CONNECTED,
            connectionManager.getConnectionState("peer-1"));
    }

    @Test
    void testGetConnection() {
        PeerInfo peerInfo = new PeerInfo("peer-1", "192.168.1.100", 9000);
        SocketConnection mockConnection = Mockito.mock(SocketConnection.class);

        connectionManager.connect(peerInfo, mockConnection,
            PeerConnectionManager.ConnectionType.DIRECT);

        SocketConnection retrieved = connectionManager.getConnection("peer-1");
        assertNotNull(retrieved);
        assertEquals(mockConnection, retrieved);
    }

    @Test
    void testGetConnectionForNonExistentPeer() {
        SocketConnection connection = connectionManager.getConnection("non-existent");
        assertNull(connection);
    }

    @Test
    void testGetConnectionState() {
        PeerInfo peerInfo = new PeerInfo("peer-1", "192.168.1.100", 9000);
        SocketConnection mockConnection = Mockito.mock(SocketConnection.class);

        assertEquals(PeerConnectionManager.ConnectionState.DISCONNECTED,
            connectionManager.getConnectionState("peer-1"));

        connectionManager.connect(peerInfo, mockConnection,
            PeerConnectionManager.ConnectionType.DIRECT);

        assertEquals(PeerConnectionManager.ConnectionState.CONNECTED,
            connectionManager.getConnectionState("peer-1"));
    }

    @Test
    void testDisconnect() throws Exception {
        PeerInfo peerInfo = new PeerInfo("peer-1", "192.168.1.100", 9000);
        SocketConnection mockConnection = Mockito.mock(SocketConnection.class);

        connectionManager.connect(peerInfo, mockConnection,
            PeerConnectionManager.ConnectionType.DIRECT);

        connectionManager.disconnect("peer-1");

        assertEquals(PeerConnectionManager.ConnectionState.DISCONNECTED,
            connectionManager.getConnectionState("peer-1"));
        verify(mockConnection, times(1)).close();
    }

    @Test
    void testDisconnectNonExistentPeer() {
        connectionManager.disconnect("non-existent");
        assertEquals(PeerConnectionManager.ConnectionState.DISCONNECTED,
            connectionManager.getConnectionState("non-existent"));
    }

    @Test
    void testRecordHeartbeat() throws InterruptedException {
        PeerInfo peerInfo = new PeerInfo("peer-1", "192.168.1.100", 9000);
        SocketConnection mockConnection = Mockito.mock(SocketConnection.class);

        connectionManager.connect(peerInfo, mockConnection,
            PeerConnectionManager.ConnectionType.DIRECT);

        Thread.sleep(100);
        connectionManager.recordHeartbeat("peer-1");

        assertEquals(PeerConnectionManager.ConnectionState.CONNECTED,
            connectionManager.getConnectionState("peer-1"));
    }

    @Test
    void testSendHeartbeat() {
        PeerInfo peerInfo = new PeerInfo("peer-1", "192.168.1.100", 9000);
        SocketConnection mockConnection = Mockito.mock(SocketConnection.class);

        connectionManager.connect(peerInfo, mockConnection,
            PeerConnectionManager.ConnectionType.DIRECT);

        boolean result = connectionManager.sendHeartbeat("peer-1");
        assertTrue(result);
    }

    @Test
    void testSendHeartbeatToNonExistentPeer() {
        boolean result = connectionManager.sendHeartbeat("non-existent");
        assertFalse(result);
    }

    @Test
    void testConnectionStateListener() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<PeerConnectionManager.ConnectionState> capturedState =
            new AtomicReference<>();

        connectionManager.setConnectionStateListener((peerId, newState) -> {
            capturedState.set(newState);
            latch.countDown();
        });

        PeerInfo peerInfo = new PeerInfo("peer-1", "192.168.1.100", 9000);
        SocketConnection mockConnection = Mockito.mock(SocketConnection.class);

        connectionManager.connect(peerInfo, mockConnection,
            PeerConnectionManager.ConnectionType.DIRECT);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(PeerConnectionManager.ConnectionState.CONNECTED, capturedState.get());
    }

    @Test
    void testConnectAlreadyConnectedPeer() {
        PeerInfo peerInfo = new PeerInfo("peer-1", "192.168.1.100", 9000);
        SocketConnection mockConnection1 = Mockito.mock(SocketConnection.class);
        SocketConnection mockConnection2 = Mockito.mock(SocketConnection.class);

        connectionManager.connect(peerInfo, mockConnection1,
            PeerConnectionManager.ConnectionType.DIRECT);

        boolean result = connectionManager.connect(peerInfo, mockConnection2,
            PeerConnectionManager.ConnectionType.DIRECT);

        assertTrue(result);
        assertEquals(PeerConnectionManager.ConnectionState.CONNECTED,
            connectionManager.getConnectionState("peer-1"));
    }

    @Test
    void testGetAllConnections() {
        PeerInfo peerInfo1 = new PeerInfo("peer-1", "192.168.1.100", 9000);
        PeerInfo peerInfo2 = new PeerInfo("peer-2", "192.168.1.101", 9001);
        SocketConnection mockConnection1 = Mockito.mock(SocketConnection.class);
        SocketConnection mockConnection2 = Mockito.mock(SocketConnection.class);

        connectionManager.connect(peerInfo1, mockConnection1,
            PeerConnectionManager.ConnectionType.DIRECT);
        connectionManager.connect(peerInfo2, mockConnection2,
            PeerConnectionManager.ConnectionType.DIRECT);

        assertEquals(2, connectionManager.getAllConnections().size());
    }

    @Test
    void testClose() throws Exception {
        PeerInfo peerInfo = new PeerInfo("peer-1", "192.168.1.100", 9000);
        SocketConnection mockConnection = Mockito.mock(SocketConnection.class);

        connectionManager.connect(peerInfo, mockConnection,
            PeerConnectionManager.ConnectionType.DIRECT);

        connectionManager.close();

        verify(mockConnection, times(1)).close();
    }

    @Test
    void testRelayedConnectionType() {
        PeerInfo peerInfo = new PeerInfo("peer-1", "192.168.1.100", 9000);
        SocketConnection mockConnection = Mockito.mock(SocketConnection.class);

        connectionManager.connect(peerInfo, mockConnection,
            PeerConnectionManager.ConnectionType.RELAYED);

        assertEquals(PeerConnectionManager.ConnectionState.CONNECTED,
            connectionManager.getConnectionState("peer-1"));
    }

    @Test
    void testScheduleReconnection() throws InterruptedException {
        PeerInfo peerInfo = new PeerInfo("peer-1", "192.168.1.100", 9000);

        connectionManager.scheduleReconnection(peerInfo);

        Thread.sleep(200);

        PeerConnectionManager.ConnectionState state = connectionManager.getConnectionState("peer-1");
        assertTrue(state == PeerConnectionManager.ConnectionState.RECONNECTING ||
                   state == PeerConnectionManager.ConnectionState.DISCONNECTED);
    }
}
