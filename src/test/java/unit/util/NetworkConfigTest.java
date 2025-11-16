package unit.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import util.NetworkConfig;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NetworkConfigTest {

    private NetworkConfig config;

    @BeforeEach
    void setUp() {
        config = new NetworkConfig();
    }

    @Test
    void testDefaultValues() {
        assertEquals(8080, config.getLocalPort());
        assertTrue(config.getBootstrapPeers().isEmpty());
        assertNull(config.getRelayServerAddress());
        assertEquals(9090, config.getRelayServerPort());
        assertTrue(config.isEnableUPnP());
        assertEquals(30000, config.getHeartbeatIntervalMs());
        assertEquals(10000, config.getConnectionTimeoutMs());
        assertEquals(5000, config.getReconnectionDelayMs());
        assertEquals(3, config.getMaxReconnectionAttempts());
    }

    @Test
    void testSetLocalPort() {
        config.setLocalPort(8080);
        assertEquals(8080, config.getLocalPort());
    }

    @Test
    void testSetBootstrapPeers() {
        config.addBootstrapPeer("192.168.1.100:9000");
        config.addBootstrapPeer("192.168.1.101:9001");

        List<String> peers = config.getBootstrapPeers();
        assertEquals(2, peers.size());
        assertTrue(peers.contains("192.168.1.100:9000"));
        assertTrue(peers.contains("192.168.1.101:9001"));
    }

    @Test
    void testSetRelayServer() {
        config.setRelayServerAddress("relay.example.com");
        config.setRelayServerPort(9090);

        assertEquals("relay.example.com", config.getRelayServerAddress());
        assertEquals(9090, config.getRelayServerPort());
    }

    @Test
    void testSetUPnPEnabled() {
        config.setEnableUPnP(false);
        assertFalse(config.isEnableUPnP());

        config.setEnableUPnP(true);
        assertTrue(config.isEnableUPnP());
    }

    @Test
    void testSetHeartbeatInterval() {
        config.setHeartbeatIntervalMs(60000);
        assertEquals(60000, config.getHeartbeatIntervalMs());
    }

    @Test
    void testSetConnectionTimeout() {
        config.setConnectionTimeoutMs(15000);
        assertEquals(15000, config.getConnectionTimeoutMs());
    }

    @Test
    void testSetReconnectionDelay() {
        config.setReconnectionDelayMs(10000);
        assertEquals(10000, config.getReconnectionDelayMs());
    }

    @Test
    void testSetMaxReconnectionAttempts() {
        config.setMaxReconnectionAttempts(5);
        assertEquals(5, config.getMaxReconnectionAttempts());
    }

    @Test
    void testLoadFromPropertiesFile() throws IOException {
        File tempFile = File.createTempFile("network-test", ".properties");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("local.port=8888\n");
            writer.write("bootstrap.peers=192.168.1.1:9000,192.168.1.2:9001\n");
            writer.write("relay.server.address=relay.test.com\n");
            writer.write("relay.server.port=9999\n");
            writer.write("upnp.enabled=false\n");
            writer.write("heartbeat.interval.ms=45000\n");
            writer.write("connection.timeout.ms=12000\n");
            writer.write("reconnection.delay.ms=8000\n");
            writer.write("max.reconnection.attempts=7\n");
        }

        NetworkConfig loadedConfig = NetworkConfig.fromFile(tempFile.getAbsolutePath());

        assertEquals(8888, loadedConfig.getLocalPort());
        assertEquals(2, loadedConfig.getBootstrapPeers().size());
        assertEquals("relay.test.com", loadedConfig.getRelayServerAddress());
        assertEquals(9999, loadedConfig.getRelayServerPort());
        assertFalse(loadedConfig.isEnableUPnP());
        assertEquals(45000, loadedConfig.getHeartbeatIntervalMs());
        assertEquals(12000, loadedConfig.getConnectionTimeoutMs());
        assertEquals(8000, loadedConfig.getReconnectionDelayMs());
        assertEquals(7, loadedConfig.getMaxReconnectionAttempts());
    }

    @Test
    void testLoadFromNonExistentFile() {
        assertThrows(IOException.class, () -> NetworkConfig.fromFile("nonexistent.properties"));
    }

    @Test
    void testExternalIpAndPort() {
        config.setExternalIp("203.0.113.42");
        config.setExternalPort(8888);

        assertEquals("203.0.113.42", config.getExternalIp());
        assertEquals(8888, config.getExternalPort());
    }

    @Test
    void testToString() {
        config.setLocalPort(9000);
        config.addBootstrapPeer("192.168.1.1:9000");
        config.setRelayServerAddress("relay.test.com");

        String str = config.toString();
        assertTrue(str.contains("localPort=9000"));
        assertTrue(str.contains("192.168.1.1:9000"));
        assertTrue(str.contains("relay.test.com"));
    }
}
