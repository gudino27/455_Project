package network.p2p;

import network.lan.PeerInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

public class P2PDiscovery implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(P2PDiscovery.class.getName());
    private static final long PEER_EXPIRATION_MS = 120000;

    private final List<String> bootstrapPeers;
    private final ConcurrentHashMap<String, PeerInfo> discoveredPeers;
    private final List<DiscoveryListener> listeners;
    private final String localPeerId;

    public P2PDiscovery(String localPeerId, List<String> bootstrapPeers) {
        this.localPeerId = localPeerId;
        this.bootstrapPeers = new ArrayList<>(bootstrapPeers);
        this.discoveredPeers = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
    }

    public void start() {
        logger.info("P2P discovery started with " + bootstrapPeers.size() + " bootstrap peers");

        for (String bootstrapPeer : bootstrapPeers) {
            String[] parts = bootstrapPeer.split(":");
            if (parts.length == 2) {
                try {
                    String address = parts[0];
                    int port = Integer.parseInt(parts[1]);
                    addPeer(generatePeerId(address, port), address, port);
                } catch (NumberFormatException e) {
                    logger.warning("Invalid bootstrap peer format: " + bootstrapPeer);
                }
            }
        }
    }

    public void addPeer(String peerId, String address, int port) {
        if (peerId.equals(localPeerId)) {
            return;
        }

        PeerInfo existingPeer = discoveredPeers.get(peerId);
        if (existingPeer == null) {
            PeerInfo peerInfo = new PeerInfo(peerId, address, port);
            discoveredPeers.put(peerId, peerInfo);
            logger.info("Discovered peer: " + peerId + " at " + address + ":" + port);
            notifyPeerDiscovered(peerInfo);
        } else {
            PeerInfo updatedPeer = new PeerInfo(peerId, address, port);
            discoveredPeers.put(peerId, updatedPeer);
        }
    }

    public void exchangePeers(List<PeerInfo> remotePeers) {
        logger.fine("Peer exchange received: " + remotePeers.size() + " peers");

        for (PeerInfo remotePeer : remotePeers) {
            if (!remotePeer.getPeerId().equals(localPeerId)) {
                addPeer(remotePeer.getPeerId(), remotePeer.getAddress(), remotePeer.getPort());
            }
        }
    }

    public List<PeerInfo> getKnownPeers() {
        cleanupExpiredPeers();
        return new ArrayList<>(discoveredPeers.values());
    }

    public List<PeerInfo> getPeersForExchange() {
        cleanupExpiredPeers();
        List<PeerInfo> peers = new ArrayList<>(discoveredPeers.values());

        if (peers.size() > 20) {
            Collections.shuffle(peers);
            return peers.subList(0, 20);
        }

        return peers;
    }

    public void removePeer(String peerId) {
        PeerInfo removed = discoveredPeers.remove(peerId);
        if (removed != null) {
            logger.info("Removed peer: " + peerId);
        }
    }

    private void cleanupExpiredPeers() {
        Iterator<Map.Entry<String, PeerInfo>> iterator = discoveredPeers.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, PeerInfo> entry = iterator.next();
            if (entry.getValue().isExpired(PEER_EXPIRATION_MS)) {
                logger.info("Peer expired: " + entry.getKey());
                iterator.remove();
            }
        }
    }

    public void addDiscoveryListener(DiscoveryListener listener) {
        listeners.add(listener);
    }

    public void removeDiscoveryListener(DiscoveryListener listener) {
        listeners.remove(listener);
    }

    private void notifyPeerDiscovered(PeerInfo peerInfo) {
        for (DiscoveryListener listener : listeners) {
            try {
                listener.onPeerDiscovered(peerInfo);
            } catch (Exception e) {
                logger.warning("Error in discovery listener: " + e.getMessage());
            }
        }
    }

    private String generatePeerId(String address, int port) {
        return address + ":" + port;
    }

    @Override
    public void close() {
        discoveredPeers.clear();
        listeners.clear();
        logger.info("P2P discovery stopped");
    }

    @FunctionalInterface
    public interface DiscoveryListener {
        void onPeerDiscovered(PeerInfo peerInfo);
    }
}
