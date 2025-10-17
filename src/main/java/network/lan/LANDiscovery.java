package network.lan;

import java.io.*;
import java.net.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class LANDiscovery implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(LANDiscovery.class.getName());
    private static final int BROADCAST_PORT = 8888;
    private static final String DISCOVERY_PREFIX = "PEER_DISCOVERY:";
    private static final String DISCOVERY_RESPONSE = "PEER_RESPONSE:";

    private final String peerId;
    private final int serverPort;
    private final AtomicBoolean running;
    private final DatagramSocket socket;
    private final Thread listenerThread;
    private final Thread announcerThread;
    private final CopyOnWriteArrayList<DiscoveryListener> listeners;

    public LANDiscovery(String peerId, int serverPort) throws SocketException {
        this.peerId = peerId;
        this.serverPort = serverPort;
        this.running = new AtomicBoolean(false);
        this.socket = new DatagramSocket(null);
        this.socket.setReuseAddress(true);
        this.socket.bind(new InetSocketAddress(BROADCAST_PORT));
        this.socket.setBroadcast(true);
        this.listenerThread = new Thread(this::listen);
        this.announcerThread = new Thread(this::announce);
        this.listeners = new CopyOnWriteArrayList<>();
        this.listenerThread.setDaemon(true);
        this.announcerThread.setDaemon(true);
    }

    public void addListener(DiscoveryListener listener) {
        listeners.add(listener);
    }

    public void removeListener(DiscoveryListener listener) {
        listeners.remove(listener);
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            listenerThread.start();
            announcerThread.start();
            logger.info("LAN discovery started for peer " + peerId);
        }
    }

    private void announce() {
        while (running.get()) {
            try {
                sendDiscoveryBroadcast();
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                logger.warning("Error sending discovery broadcast: " + e.getMessage());
            }
        }
    }

    private void sendDiscoveryBroadcast() throws IOException {
        String message = DISCOVERY_PREFIX + peerId + ":" + serverPort;
        byte[] buffer = message.getBytes();

        InetAddress broadcast = InetAddress.getByName("255.255.255.255");
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, broadcast, BROADCAST_PORT);
        socket.send(packet);
    }

    private void listen() {
        byte[] buffer = new byte[1024];

        while (running.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                String senderAddress = packet.getAddress().getHostAddress();

                handleDiscoveryMessage(message, senderAddress);
            } catch (SocketException e) {
                if (running.get()) {
                    logger.warning("Socket exception in listener: " + e.getMessage());
                }
                break;
            } catch (IOException e) {
                logger.warning("Error receiving discovery message: " + e.getMessage());
            }
        }
    }

    private void handleDiscoveryMessage(String message, String senderAddress) throws IOException {
        if (message.startsWith(DISCOVERY_PREFIX)) {
            handleDiscoveryRequest(message, senderAddress);
        } else if (message.startsWith(DISCOVERY_RESPONSE)) {
            handleDiscoveryResponse(message, senderAddress);
        }
    }

    private void handleDiscoveryRequest(String message, String senderAddress) throws IOException {
        String content = message.substring(DISCOVERY_PREFIX.length());
        String[] parts = content.split(":");

        if (parts.length == 2) {
            String remotePeerId = parts[0];
            int remotePort = Integer.parseInt(parts[1]);

            if (!remotePeerId.equals(peerId)) {
                sendDiscoveryResponse(senderAddress);

                PeerInfo peerInfo = new PeerInfo(remotePeerId, senderAddress, remotePort);
                notifyPeerDiscovered(peerInfo);
            }
        }
    }

    private void handleDiscoveryResponse(String message, String senderAddress) {
        String content = message.substring(DISCOVERY_RESPONSE.length());
        String[] parts = content.split(":");

        if (parts.length == 2) {
            String remotePeerId = parts[0];
            int remotePort = Integer.parseInt(parts[1]);

            if (!remotePeerId.equals(peerId)) {
                PeerInfo peerInfo = new PeerInfo(remotePeerId, senderAddress, remotePort);
                notifyPeerDiscovered(peerInfo);
            }
        }
    }

    private void sendDiscoveryResponse(String targetAddress) throws IOException {
        String message = DISCOVERY_RESPONSE + peerId + ":" + serverPort;
        byte[] buffer = message.getBytes();

        InetAddress address = InetAddress.getByName(targetAddress);
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, BROADCAST_PORT);
        socket.send(packet);
    }

    private void notifyPeerDiscovered(PeerInfo peerInfo) {
        for (DiscoveryListener listener : listeners) {
            try {
                listener.onPeerDiscovered(peerInfo);
            } catch (Exception e) {
                logger.warning("Error notifying listener: " + e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        running.set(false);

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        if (listenerThread != null && listenerThread.isAlive()) {
            listenerThread.interrupt();
        }

        if (announcerThread != null && announcerThread.isAlive()) {
            announcerThread.interrupt();
        }

        logger.info("LAN discovery stopped");
    }

    public interface DiscoveryListener {
        void onPeerDiscovered(PeerInfo peerInfo);
    }
}
