package unit.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import util.UPnPManager;

import static org.junit.jupiter.api.Assertions.*;

class UPnPManagerTest {

    private UPnPManager upnpManager;

    @BeforeEach
    void setUp() {
        upnpManager = new UPnPManager();
    }

    @AfterEach
    void tearDown() {
        if (upnpManager != null) {
            upnpManager.close();
        }
    }

    @Test
    void testInitiallyNotInitialized() {
        assertFalse(upnpManager.isInitialized());
    }

    @Test
    void testAddPortMappingWithoutInitialization() {
        boolean result = upnpManager.addPortMapping(8080, 8080, "TCP", "Test");
        assertFalse(result);
    }

    @Test
    void testDeletePortMappingWithoutInitialization() {
        boolean result = upnpManager.deletePortMapping(8080, "TCP");
        assertFalse(result);
    }

    @Test
    void testInitialize() {
        boolean initialized = upnpManager.initialize();

        if (initialized) {
            assertTrue(upnpManager.isInitialized());
        } else {
            assertFalse(upnpManager.isInitialized());
        }
    }

    @Test
    void testClose() {
        upnpManager.initialize();
        upnpManager.close();

        assertFalse(upnpManager.isInitialized());
    }

    @Test
    void testAddPortMappingAfterClose() {
        upnpManager.initialize();
        upnpManager.close();

        boolean result = upnpManager.addPortMapping(8080, 8080, "TCP", "Test");
        assertFalse(result);
    }

    @Test
    void testDeletePortMappingAfterClose() {
        upnpManager.initialize();
        upnpManager.close();

        boolean result = upnpManager.deletePortMapping(8080, "TCP");
        assertFalse(result);
    }

    @Test
    void testMultipleInitializeCalls() {
        boolean firstInit = upnpManager.initialize();
        boolean secondInit = upnpManager.initialize();

        assertEquals(firstInit, secondInit);
    }

    @Test
    void testCloseMultipleTimes() {
        upnpManager.initialize();
        upnpManager.close();
        upnpManager.close();

        assertFalse(upnpManager.isInitialized());
    }
}
