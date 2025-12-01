package gui;

import api.ConnectionMode;
import api.NetworkManager;
import api.NetworkManagerFactory;
import network.lan.PeerInfo;
import util.NetworkConfig;
import util.NATDetector;
import util.UPnPManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public class ChatGUI extends JFrame {
    private NetworkManager manager;
    
    // UI Components
    private JTextArea chatArea;
    private JTextField messageField;
    private JList<String> peerList;
    private DefaultListModel<String> peerListModel;
    private JButton sendButton;
    private JButton broadcastButton;
    private JButton connectButton;
    private JLabel statusLabel;
    
    public ChatGUI() {
        super("P2P Chat Application");
        initializeUI();
    }
    
    private void initializeUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setMinimumSize(new Dimension(600, 400));
        setLocationRelativeTo(null);
        
        // Main panel with border layout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Top panel - Status bar
        JPanel topPanel = createTopPanel();
        mainPanel.add(topPanel, BorderLayout.NORTH);
        
        // Center panel - Chat area
        JPanel centerPanel = createCenterPanel();
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        // Right panel - Peer list
        JPanel rightPanel = createRightPanel();
        mainPanel.add(rightPanel, BorderLayout.EAST);
        
        // Bottom panel - Message input
        JPanel bottomPanel = createBottomPanel();
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
        
        // Add window listener for cleanup
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
        
        // Show startup dialog
        SwingUtilities.invokeLater(this::showStartupDialog);
    }
    
    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY),
            new EmptyBorder(5, 5, 5, 5)
        ));
        
        statusLabel = new JLabel("Not connected");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        panel.add(statusLabel, BorderLayout.WEST);
        
        JButton settingsButton = new JButton("⚙ Settings");
        settingsButton.addActionListener(e -> showStartupDialog());
        panel.add(settingsButton, BorderLayout.EAST);
        
        return panel;
    }
    
    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Chat"));
        
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        
        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new TitledBorder("Peers"));
        panel.setPreferredSize(new Dimension(180, 0));
        
        peerListModel = new DefaultListModel<>();
        peerList = new JList<>(peerListModel);
        peerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        peerList.setFont(new Font("Monospaced", Font.PLAIN, 11));
        
        JScrollPane scrollPane = new JScrollPane(peerList);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Buttons panel
        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        
        connectButton = new JButton("Connect to Peer...");
        connectButton.addActionListener(e -> showConnectDialog());
        buttonPanel.add(connectButton);
        
        JButton refreshButton = new JButton("Refresh Peers");
        refreshButton.addActionListener(e -> refreshPeerList());
        buttonPanel.add(refreshButton);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new EmptyBorder(5, 0, 0, 0));
        
        messageField = new JTextField();
        messageField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        messageField.addActionListener(e -> sendMessage());
        panel.add(messageField, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        
        sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendMessage());
        sendButton.setEnabled(false);
        buttonPanel.add(sendButton);
        
        broadcastButton = new JButton("Broadcast");
        broadcastButton.addActionListener(e -> broadcastMessage());
        broadcastButton.setEnabled(false);
        buttonPanel.add(broadcastButton);
        
        panel.add(buttonPanel, BorderLayout.EAST);
        
        return panel;
    }
    
    private void showStartupDialog() {
        JDialog dialog = new JDialog(this, "Network Settings", true);
        dialog.setSize(400, 350);
        dialog.setLocationRelativeTo(this);
        
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // Username
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 1;
        panel.add(new JLabel("Username:"), gbc);
        
        JTextField usernameField = new JTextField("");
        gbc.gridx = 1; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(usernameField, gbc);
        
        // Mode selection
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        panel.add(new JLabel("Mode:"), gbc);
        
        JComboBox<String> modeCombo = new JComboBox<>(new String[]{"Auto-Detect", "P2P", "LAN"});
        gbc.gridx = 1; gbc.gridy = 1; gbc.gridwidth = 2;
        panel.add(modeCombo, gbc);
        
        // Port
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1;
        panel.add(new JLabel("Port:"), gbc);
        
        JSpinner portSpinner = new JSpinner(new SpinnerNumberModel(9000, 1024, 65535, 1));
        gbc.gridx = 1; gbc.gridy = 2; gbc.gridwidth = 2;
        panel.add(portSpinner, gbc);
        
        // Bootstrap peers
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1;
        panel.add(new JLabel("Bootstrap Peers:"), gbc);
        
        JTextField bootstrapField = new JTextField("localhost:9000");
        gbc.gridx = 1; gbc.gridy = 3; gbc.gridwidth = 2;
        panel.add(bootstrapField, gbc);
        
        gbc.gridx = 1; gbc.gridy = 4; gbc.gridwidth = 2;
        panel.add(new JLabel("<html><small>(comma-separated, e.g., host1:9000,host2:9001)</small></html>"), gbc);
        
        // UPnP checkbox
        JCheckBox upnpCheckbox = new JCheckBox("Enable UPnP", true);
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 3;
        panel.add(upnpCheckbox, gbc);
        
        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        JButton startButton = new JButton("Start");
        startButton.addActionListener(e -> {
            dialog.dispose();
            
            String username = usernameField.getText().trim();
            String modeStr = (String) modeCombo.getSelectedItem();
            int port = (Integer) portSpinner.getValue();
            String bootstrapStr = bootstrapField.getText().trim();
            boolean enableUPnP = upnpCheckbox.isSelected();
            
            List<String> bootstrapPeers = new ArrayList<>();
            if (!bootstrapStr.isEmpty()) {
                for (String peer : bootstrapStr.split(",")) {
                    bootstrapPeers.add(peer.trim());
                }
            }
            
            ConnectionMode mode;
            if ("P2P".equals(modeStr)) {
                mode = ConnectionMode.P2P;
            } else if ("LAN".equals(modeStr)) {
                mode = ConnectionMode.LAN;
            } else {
                mode = detectBestMode();
            }
            
            startNetwork(mode, port, bootstrapPeers, enableUPnP, username);
        });
        buttonPanel.add(startButton);
        
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(cancelButton);
        
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 3;
        gbc.insets = new Insets(15, 5, 5, 5);
        panel.add(buttonPanel, gbc);
        
        dialog.add(panel);
        dialog.setVisible(true);
    }
    
    private ConnectionMode detectBestMode() {
        appendChat("[SYSTEM] Detecting best network mode...");
        
        boolean behindNAT = NATDetector.isBehindNAT();
        appendChat("[SYSTEM] Behind NAT: " + behindNAT);
        
        boolean upnpAvailable = false;
        try {
            UPnPManager upnpManager = new UPnPManager();
            upnpAvailable = upnpManager.initialize();
            upnpManager.close();
        } catch (Exception e) {
            // ignore
        }
        appendChat("[SYSTEM] UPnP available: " + upnpAvailable);
        
        if (!behindNAT || upnpAvailable) {
            appendChat("[SYSTEM] Selected P2P mode");
            return ConnectionMode.P2P;
        } else {
            appendChat("[SYSTEM] Selected LAN mode");
            return ConnectionMode.LAN;
        }
    }
    
    private void startNetwork(ConnectionMode mode, int port, List<String> bootstrapPeers, boolean enableUPnP, String username) {
        // Stop existing connection if any
        if (manager != null) {
            try {
                manager.close();
            } catch (Exception e) {
                // ignore
            }
        }
        
        try {
            NetworkConfig config = new NetworkConfig();
            config.setLocalPort(port);
            config.setBootstrapPeers(bootstrapPeers);
            config.setEnableUPnP(enableUPnP);
            if (username != null && !username.isEmpty()) {
                config.setUsername(username);
            }
            
            manager = NetworkManagerFactory.createNetworkManager(mode, config);
            
            // Set up listeners
            manager.setMessageListener((peerId, message) -> {
                SwingUtilities.invokeLater(() -> {
                    appendChat("[" + peerId + "]: " + message);
                });
            });
            
            manager.setPeerConnectionListener(new NetworkManager.PeerConnectionListener() {
                @Override
                public void onPeerConnected(String peerId, String address) {
                    SwingUtilities.invokeLater(() -> {
                        appendChat("[SYSTEM] Peer connected: " + peerId + " from " + address);
                        refreshPeerList();
                    });
                }
                
                @Override
                public void onPeerDisconnected(String peerId) {
                    SwingUtilities.invokeLater(() -> {
                        appendChat("[SYSTEM] Peer disconnected: " + peerId);
                        refreshPeerList();
                    });
                }
            });
            
            manager.start();
            
            // Update UI
            String usernameInfo = (username != null && !username.isEmpty()) ? " | User: " + username : "";
            statusLabel.setText("Connected | Mode: " + mode + " | Port: " + port + usernameInfo);
            sendButton.setEnabled(true);
            broadcastButton.setEnabled(true);
            
            appendChat("=================================");
            appendChat("Network Started");
            appendChat("Mode: " + mode);
            appendChat("Port: " + port);
            if (username != null && !username.isEmpty()) {
                appendChat("Username: " + username);
            }
            if (!bootstrapPeers.isEmpty()) {
                appendChat("Bootstrap Peers: " + bootstrapPeers);
            }
            appendChat("=================================");
            
            // Initial peer refresh
            refreshPeerList();
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, 
                "Failed to start network: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            appendChat("[ERROR] " + e.getMessage());
        }
    }
    
    private void sendMessage() {
        if (manager == null) {
            JOptionPane.showMessageDialog(this, "Not connected!", "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String message = messageField.getText().trim();
        if (message.isEmpty()) return;
        
        String selectedPeer = peerList.getSelectedValue();
        if (selectedPeer == null || selectedPeer.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Please select a peer from the list or use Broadcast.",
                "No Peer Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Extract peer ID (format: "peer-xxx (address:port)")
        String peerId = selectedPeer.split(" ")[0];
        
        manager.sendMessage(peerId, message);
        appendChat("[You -> " + peerId + "]: " + message);
        messageField.setText("");
    }
    
    private void broadcastMessage() {
        if (manager == null) {
            JOptionPane.showMessageDialog(this, "Not connected!", "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String message = messageField.getText().trim();
        if (message.isEmpty()) return;
        
        manager.broadcast(message);
        appendChat("[You -> ALL]: " + message);
        messageField.setText("");
    }
    
    private void showConnectDialog() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
        
        panel.add(new JLabel("Address:"));
        JTextField addressField = new JTextField("localhost");
        panel.add(addressField);
        
        panel.add(new JLabel("Port:"));
        JSpinner portSpinner = new JSpinner(new SpinnerNumberModel(9000, 1024, 65535, 1));
        panel.add(portSpinner);
        
        int result = JOptionPane.showConfirmDialog(this, panel, 
            "Connect to Peer", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        
        if (result == JOptionPane.OK_OPTION) {
            String address = addressField.getText().trim();
            int port = (Integer) portSpinner.getValue();
            
            try {
                manager.connect(address, port);
                appendChat("[SYSTEM] Connecting to " + address + ":" + port + "...");
            } catch (Exception e) {
                appendChat("[ERROR] Failed to connect: " + e.getMessage());
            }
        }
    }
    
    private void refreshPeerList() {
        if (manager == null) return;
        
        peerListModel.clear();
        List<PeerInfo> peers = manager.getPeers();
        
        for (PeerInfo peer : peers) {
            peerListModel.addElement(peer.getPeerId() + " (" + peer.getAddress() + ":" + peer.getPort() + ")");
        }
    }
    
    private void appendChat(String message) {
        chatArea.append(message + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }
    
    private void shutdown() {
        if (manager != null) {
            try {
                manager.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }
    
    public static void main(String[] args) {
        // Set look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Use default look and feel
        }
        
        SwingUtilities.invokeLater(() -> {
            ChatGUI gui = new ChatGUI();
            gui.setVisible(true);
        });
    }
}
