package unit.util;

import org.junit.jupiter.api.Test;
import util.NATDetector;

import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;

class NATDetectorTest {

    @Test
    void testGetLocalIP() {
        String localIp = NATDetector.getLocalIP();
        assertNotNull(localIp);
        assertFalse(localIp.isEmpty());
        assertTrue(localIp.matches("\\d+\\.\\d+\\.\\d+\\.\\d+"));
    }

    @Test
    void testIsPrivateIP_LocalhostIPv4() {
        assertTrue(NATDetector.isPrivateIP("127.0.0.1"));
        assertTrue(NATDetector.isPrivateIP("127.0.1.1"));
    }

    @Test
    void testIsPrivateIP_PrivateRanges() {
        assertTrue(NATDetector.isPrivateIP("192.168.1.1"));
        assertTrue(NATDetector.isPrivateIP("192.168.0.100"));
        assertTrue(NATDetector.isPrivateIP("10.0.0.1"));
        assertTrue(NATDetector.isPrivateIP("10.255.255.255"));
        assertTrue(NATDetector.isPrivateIP("172.16.0.1"));
        assertTrue(NATDetector.isPrivateIP("172.31.255.255"));
    }

    @Test
    void testIsPrivateIP_PublicIPs() {
        assertFalse(NATDetector.isPrivateIP("8.8.8.8"));
        assertFalse(NATDetector.isPrivateIP("1.1.1.1"));
        assertFalse(NATDetector.isPrivateIP("203.0.113.42"));
    }

    @Test
    void testIsPrivateIP_InvalidIP() {
        assertFalse(NATDetector.isPrivateIP(null));
        assertFalse(NATDetector.isPrivateIP(""));
    }

    @Test
    void testIsPrivateIP_LinkLocal() {
        assertTrue(NATDetector.isPrivateIP("169.254.1.1"));
        assertTrue(NATDetector.isPrivateIP("169.254.255.255"));
    }

    @Test
    void testIsPortAccessible_AvailablePort() throws Exception {
        ServerSocket testSocket = new ServerSocket(0);
        int availablePort = testSocket.getLocalPort();
        testSocket.close();

        Thread.sleep(100);

        assertTrue(NATDetector.isPortAccessible(availablePort));
    }

    @Test
    void testIsPortAccessible_OccupiedPort() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int occupiedPort = serverSocket.getLocalPort();
            assertFalse(NATDetector.isPortAccessible(occupiedPort));
        }
    }

    @Test
    void testIsBehindNAT() {
        boolean behindNat = NATDetector.isBehindNAT();
        assertNotNull(behindNat);
    }

    @Test
    void testGetExternalIP() {
        String externalIp = NATDetector.getExternalIP();
        if (externalIp != null) {
            assertFalse(externalIp.isEmpty());
            assertTrue(externalIp.matches("\\d+\\.\\d+\\.\\d+\\.\\d+"));
        }
    }
}
