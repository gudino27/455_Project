package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.*;
import java.util.logging.Logger;

public class UPnPManager implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(UPnPManager.class.getName());
    private static final String MULTICAST_ADDRESS = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final int DISCOVERY_TIMEOUT_MS = 5000;

    private String gatewayUrl = null;
    private boolean initialized = false;

    public boolean initialize() {
        try {
            gatewayUrl = discoverGateway();
            if (gatewayUrl != null) {
                logger.info("UPnP gateway discovered: " + gatewayUrl);
                initialized = true;
                return true;
            } else {
                logger.warning("No UPnP gateway found");
                return false;
            }
        } catch (Exception e) {
            logger.warning("UPnP initialization failed: " + e.getMessage());
            return false;
        }
    }

    public boolean addPortMapping(int externalPort, int internalPort, String protocol, String description) {
        if (!initialized) {
            logger.warning("UPnP not initialized, cannot add port mapping");
            return false;
        }

        try {
            String localIP = InetAddress.getLocalHost().getHostAddress();
            String soapRequest = buildAddPortMappingRequest(externalPort, internalPort, localIP, protocol, description);

            String response = sendSOAPRequest(soapRequest);
            if (response != null && !response.contains("error")) {
                logger.info(String.format("Port mapping added: %d -> %s:%d (%s)",
                    externalPort, localIP, internalPort, protocol));
                return true;
            } else {
                logger.warning("Failed to add port mapping: " + response);
                return false;
            }
        } catch (Exception e) {
            logger.warning("Error adding port mapping: " + e.getMessage());
            return false;
        }
    }

    public boolean deletePortMapping(int externalPort, String protocol) {
        if (!initialized) {
            logger.warning("UPnP not initialized, cannot delete port mapping");
            return false;
        }

        try {
            String soapRequest = buildDeletePortMappingRequest(externalPort, protocol);
            String response = sendSOAPRequest(soapRequest);

            if (response != null && !response.contains("error")) {
                logger.info(String.format("Port mapping deleted: %d (%s)", externalPort, protocol));
                return true;
            } else {
                logger.warning("Failed to delete port mapping: " + response);
                return false;
            }
        } catch (Exception e) {
            logger.warning("Error deleting port mapping: " + e.getMessage());
            return false;
        }
    }

    private String discoverGateway() throws IOException {
        String ssdpRequest =
            "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: " + MULTICAST_ADDRESS + ":" + SSDP_PORT + "\r\n" +
            "ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 3\r\n" +
            "\r\n";

        InetAddress multicastAddress = InetAddress.getByName(MULTICAST_ADDRESS);
        MulticastSocket socket = new MulticastSocket();
        socket.setSoTimeout(DISCOVERY_TIMEOUT_MS);

        try {
            DatagramPacket requestPacket = new DatagramPacket(
                ssdpRequest.getBytes(),
                ssdpRequest.length(),
                multicastAddress,
                SSDP_PORT
            );

            socket.send(requestPacket);

            byte[] buffer = new byte[1024];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);

            socket.receive(responsePacket);
            String response = new String(responsePacket.getData(), 0, responsePacket.getLength());

            return extractLocationUrl(response);
        } catch (SocketTimeoutException e) {
            logger.fine("UPnP discovery timeout - no gateway found");
            return null;
        } finally {
            socket.close();
        }
    }

    private String extractLocationUrl(String response) {
        String[] lines = response.split("\r\n");
        for (String line : lines) {
            if (line.toLowerCase().startsWith("location:")) {
                return line.substring(9).trim();
            }
        }
        return null;
    }

    private String buildAddPortMappingRequest(int externalPort, int internalPort, String internalClient,
                                              String protocol, String description) {
        return "<?xml version=\"1.0\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body>" +
            "<u:AddPortMapping xmlns:u=\"urn:schemas-upnp-org:service:WANIPConnection:1\">" +
            "<NewRemoteHost></NewRemoteHost>" +
            "<NewExternalPort>" + externalPort + "</NewExternalPort>" +
            "<NewProtocol>" + protocol + "</NewProtocol>" +
            "<NewInternalPort>" + internalPort + "</NewInternalPort>" +
            "<NewInternalClient>" + internalClient + "</NewInternalClient>" +
            "<NewEnabled>1</NewEnabled>" +
            "<NewPortMappingDescription>" + description + "</NewPortMappingDescription>" +
            "<NewLeaseDuration>0</NewLeaseDuration>" +
            "</u:AddPortMapping>" +
            "</s:Body>" +
            "</s:Envelope>";
    }

    private String buildDeletePortMappingRequest(int externalPort, String protocol) {
        return "<?xml version=\"1.0\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body>" +
            "<u:DeletePortMapping xmlns:u=\"urn:schemas-upnp-org:service:WANIPConnection:1\">" +
            "<NewRemoteHost></NewRemoteHost>" +
            "<NewExternalPort>" + externalPort + "</NewExternalPort>" +
            "<NewProtocol>" + protocol + "</NewProtocol>" +
            "</u:DeletePortMapping>" +
            "</s:Body>" +
            "</s:Envelope>";
    }

    private String sendSOAPRequest(String soapRequest) throws IOException {
        if (gatewayUrl == null) {
            return null;
        }

        URL url = new URL(gatewayUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "text/xml");
        connection.setRequestProperty("SOAPAction",
            "\"urn:schemas-upnp-org:service:WANIPConnection:1#AddPortMapping\"");

        try (OutputStream os = connection.getOutputStream()) {
            os.write(soapRequest.getBytes());
        }

        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            }
        } else {
            return "error: HTTP " + responseCode;
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void close() {
        gatewayUrl = null;
        initialized = false;
    }
}
