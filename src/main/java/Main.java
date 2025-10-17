import network.lan.LANManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.util.UUID;
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
        int port = 9000;
        String peerId = "peer-" + UUID.randomUUID().toString().substring(0, 8);

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default: " + port);
            }
        }

        if (args.length > 1) {
            peerId = args[1];
        }

        try {
            LANManager manager = new LANManager(peerId, port);

            manager.addMessageListener(message -> {
                System.out.println("[" + message.getSenderId() + "]: " + message.getContent());
            });

            manager.start();

            System.out.println("=================================");
            System.out.println("LAN Network Application Started");
            System.out.println("Peer ID: " + peerId);
            System.out.println("Port: " + port);
            System.out.println("=================================");
            System.out.println("Commands:");
            System.out.println("  send <peer-id> <message>  - Send message to specific peer");
            System.out.println("  broadcast <message>       - Send message to all peers");
            System.out.println("  peers                     - List connected peers");
            System.out.println("  quit                      - Exit application");
            System.out.println("=================================");

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String input;

            while ((input = reader.readLine()) != null) {
                if (input.equals("quit")) {
                    break;
                } else if (input.equals("peers")) {
                    System.out.println("Connected peers: " + manager.getConnectedPeerCount());
                    manager.getDiscoveredPeers().forEach((id, info) ->
                        System.out.println("  - " + id + " (" + info.getAddress() + ":" + info.getPort() + ")")
                    );
                } else if (input.startsWith("send ")) {
                    String rest = input.substring(5);
                    int spaceIndex = rest.indexOf(' ');
                    if (spaceIndex > 0) {
                        String targetPeer = rest.substring(0, spaceIndex);
                        String message = rest.substring(spaceIndex + 1);
                        try {
                            manager.sendTo(targetPeer, message);
                            System.out.println("Message sent to " + targetPeer + ": " + message);
                        } catch (IOException e) {
                            System.out.println("Failed to send message: " + e.getMessage());
                        }
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

        } catch (IOException e) {
            System.err.println("Error starting application: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
