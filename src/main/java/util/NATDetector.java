package util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.util.logging.Logger;

public class NATDetector {
    private static final Logger logger = Logger.getLogger(NATDetector.class.getName());
    private static final String[] EXTERNAL_IP_SERVICES = {
        "http://checkip.amazonaws.com",
        "http://icanhazip.com",
        "http://ifconfig.me/ip"
    };

    public static boolean isBehindNAT() {
        try {
            String externalIp = getExternalIP();
            String localIp = getLocalIP();

            if (externalIp == null || localIp == null) {
                return true;
            }

            return !externalIp.equals(localIp);
        } catch (Exception e) {
            logger.warning("Failed to detect NAT status: " + e.getMessage());
            return true;
        }
    }

    public static String getExternalIP() {
        for (String serviceUrl : EXTERNAL_IP_SERVICES) {
            try {
                URL url = new URL(serviceUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    String ip = reader.readLine().trim();
                    if (isValidIP(ip)) {
                        logger.info("External IP detected: " + ip);
                        return ip;
                    }
                }
            } catch (Exception e) {
                logger.fine("Failed to get external IP from " + serviceUrl + ": " + e.getMessage());
            }
        }
        logger.warning("Failed to detect external IP from all services");
        return null;
    }

    public static String getLocalIP() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            String ip = localHost.getHostAddress();
            logger.info("Local IP detected: " + ip);
            return ip;
        } catch (UnknownHostException e) {
            logger.warning("Failed to get local IP: " + e.getMessage());
            return null;
        }
    }

    public static boolean isPortAccessible(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            logger.info("Port " + port + " is available");
            return true;
        } catch (Exception e) {
            logger.warning("Port " + port + " is not accessible: " + e.getMessage());
            return false;
        }
    }

    public static boolean isPrivateIP(String ip) {
        if (ip == null || ip.isEmpty()) return false;

        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isSiteLocalAddress() ||
                   address.isLoopbackAddress() ||
                   address.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static boolean isValidIP(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
