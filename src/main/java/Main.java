import api.ConnectionMode;
import api.NetworkManager;
import api.NetworkManagerFactory;
import util.NetworkConfig;
import util.NATDetector;
import util.UPnPManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.LogManager;

public class Main {
    public static void main(String[] args) {
        try {
            InputStream configFile = Main.class.getClassLoader().getResourceAsStream("logging.properties");
            if (configFile != null) {
                LogManager.getLogManager().readConfiguration(configFile);
            }
        } catch (IOException e) {
            System.err.println("Could not load logging configuration");
        }

        ConnectionMode mode = selectBestMode();
        int port = 9000;
        List<String> bootstrapPeers = new ArrayList<>();
        String relayServer = null;
        int relayPort = 9090;
        boolean enableUPnP = true;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--mode") && i + 1 < args.length) {
                mode = args[++i].equalsIgnoreCase("p2p") ? ConnectionMode.P2P : ConnectionMode.LAN;
            } else if (args[i].equals("--port") && i + 1 < args.length) {
                try {
                    port = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid port number, using default: " + port);
                }
            } else if (args[i].equals("--bootstrap-peers") && i + 1 < args.length) {
                String[] peers = args[++i].split(",");
                for (String peer : peers) {
                    bootstrapPeers.add(peer.trim());
                }
            } else if (args[i].equals("--relay-server") && i + 1 < args.length) {
                String[] parts = args[++i].split(":");
                if (parts.length == 2) {
                    relayServer = parts[0];
                    try {
                        relayPort = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid relay port, using default: " + relayPort);
                    }
                } else {
                    relayServer = args[i];
                }
            } else if (args[i].equals("--no-upnp")) {
                enableUPnP = false;
            }
        }

        try {
            NetworkConfig config = new NetworkConfig();
            config.setLocalPort(port);
            config.setBootstrapPeers(bootstrapPeers);
            if (relayServer != null) {
                config.setRelayServerAddress(relayServer);
                config.setRelayServerPort(relayPort);
            }
            config.setEnableUPnP(enableUPnP);

            NetworkManager manager = NetworkManagerFactory.createNetworkManager(mode, config);

            manager.setMessageListener((peerId, message) -> {
                System.out.println("[" + peerId + "]: " + message);
            });

            manager.setPeerConnectionListener(new NetworkManager.PeerConnectionListener() {
                @Override
                public void onPeerConnected(String peerId, String address) {
                    System.out.println("[SYSTEM] Peer connected: " + peerId + " from " + address);
                }

                @Override
                public void onPeerDisconnected(String peerId) {
                    System.out.println("[SYSTEM] Peer disconnected: " + peerId);
                }
            });

            manager.start();

            System.out.println("=================================");
            System.out.println("Network Application Started");
            System.out.println("Mode: " + mode);
            System.out.println("Port: " + port);
            if (mode == ConnectionMode.P2P && !bootstrapPeers.isEmpty()) {
                System.out.println("Bootstrap Peers: " + bootstrapPeers);
            }
            if (relayServer != null) {
                System.out.println("Relay Server: " + relayServer + ":" + relayPort);
            }
            System.out.println("=================================");
            System.out.println("Commands:");
            System.out.println("  send <peer-id> <message>  - Send message to specific peer");
            System.out.println("  broadcast <message>       - Send message to all peers");
            System.out.println("  peers                     - List discovered peers");
            System.out.println("  connect <address> <port>  - Connect to a peer manually");
            System.out.println("  quit                      - Exit application");
            System.out.println("=================================");

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String input;

            while ((input = reader.readLine()) != null) {
                if (input.equals("quit")) {
                    break;
                } else if (input.equals("peers")) {
                    List<network.lan.PeerInfo> peers = manager.getPeers();
                    System.out.println("Discovered peers: " + peers.size());
                    for (network.lan.PeerInfo peer : peers) {
                        System.out.println("  - " + peer.getPeerId() + " (" + peer.getAddress() + ":" + peer.getPort() + ")");
                    }
                } else if (input.startsWith("connect ")) {
                    String rest = input.substring(8);
                    String[] parts = rest.split(" ");
                    if (parts.length == 2) {
                        try {
                            String address = parts[0];
                            int connectPort = Integer.parseInt(parts[1]);
                            manager.connect(address, connectPort);
                            System.out.println("Attempting to connect to " + address + ":" + connectPort);
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid port number");
                        } catch (Exception e) {
                            System.out.println("Failed to connect: " + e.getMessage());
                        }
                    } else {
                        System.out.println("Usage: connect <address> <port>");
                    }
                } else if (input.startsWith("send ")) {
                    String rest = input.substring(5);
                    int spaceIndex = rest.indexOf(' ');
                    if (spaceIndex > 0) {
                        String targetPeer = rest.substring(0, spaceIndex);
                        String message = rest.substring(spaceIndex + 1);
                        manager.sendMessage(targetPeer, message);
                        System.out.println("Message sent to " + targetPeer + ": " + message);
                    } else {
                        System.out.println("Usage: send <peer-id> <message>");
                    }
                } else if (input.startsWith("broadcast ")) {
                    String message = input.substring(10);
                    manager.broadcast(message);
                    System.out.println("Broadcast sent: " + message);
                } else {
                    System.out.println("Unknown command. Type 'quit' to exit.");
                }
            }

            manager.close();
            System.out.println("Application stopped.");

        } catch (Exception e) {
            System.err.println("Error starting application: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private static ConnectionMode selectBestMode(){
        System.out.println("[Network Detection] Evaluating best network mode...");

        boolean behindNAT = NATDetector.isBehindNAT();
        System.out.println("  - Behind NAT: " + behindNAT);

        boolean upnpAvailable = false;
        try{
            UPnPManager upnpManager = new UPnPManager();
            upnpAvailable = upnpManager.initialize();
            upnpManager.close();
            System.out.println("  - UPnP gateway available: " + upnpAvailable);
        } catch (Exception e){
            System.out.println("UPnP check failed: " + e.getMessage());
        }

        if (!behindNAT){
            System.out.println("Selecting P2P mode.");
            return ConnectionMode.P2P;
        }

        if (upnpAvailable){
            System.out.println("Selecting P2P mode with UPnP.");
            return ConnectionMode.P2P;
        }

        System.out.println("Selecting LAN mode.");
        return ConnectionMode.LAN;
    }
}
