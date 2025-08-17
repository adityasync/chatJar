import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Set;
import java.util.HashSet;

/**
 * ChatClientUI - Swing-based GUI client for the multi-room chat system
 */
public class ChatClientUI {
    // UI Components
    private JFrame frame;
    private JTextPane chatArea;
    private JTextField inputField;
    private JLabel typingLabel;
    private JLabel titleLabel;
    private JButton sendButton;
    private JButton emojiButton;
    private JComboBox<String> roomSelector;
    private JList<String> usersList;
    private DefaultListModel<String> usersListModel;
    
    // Network components
    private PrintWriter out;
    private Socket socket;
    private BufferedReader in;
    
    // Client state
    private String username;
    private String currentRoom = "";
    private volatile boolean isConnected = false;
    
    // Message types
    private static final String MSG_HISTORY = "HISTORY";
    private static final String HEARTBEAT = "PING";
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8888;
    private static final long HEARTBEAT_INTERVAL = 30000; // 30 seconds
    private volatile long lastHeartbeatTime = System.currentTimeMillis();
    private static final String[] AVAILABLE_ROOMS = {
        "Sun Squad", "Cake Squad", "Moon Crew", "Star Gang"
    };
    
    // Online users set
    private Set<String> onlineUsers = new HashSet<>();
    
    public ChatClientUI() {
        SwingUtilities.invokeLater(() -> {
            try {
                // Set system look and feel
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                System.err.println("⚠️ Could not set system look and feel: " + e.getMessage());
            }
            
            initializeClient();
        });
    }
    
    /**
     * Initialize the client - get user input and setup UI
     */
    private void initializeClient() {
        // Get username
        do {
            username = JOptionPane.showInputDialog(
                null,
                "Enter your username:",
                "ChatJar - Login",
                JOptionPane.QUESTION_MESSAGE
            );
            
            if (username == null) {
                System.exit(0); // User cancelled
            }
            
            username = username.trim();
            
            if (username.isEmpty()) {
                JOptionPane.showMessageDialog(
                    null,
                    "Username cannot be empty. Please try again.",
                    "Invalid Username",
                    JOptionPane.WARNING_MESSAGE
                );
            }
        } while (username.isEmpty());
        
        // Get room selection
        currentRoom = (String) JOptionPane.showInputDialog(
            null,
            "Choose a chat room:",
            "ChatJar - Room Selection",
            JOptionPane.QUESTION_MESSAGE,
            null,
            AVAILABLE_ROOMS,
            AVAILABLE_ROOMS[0]
        );
        
        if (currentRoom == null) {
            System.exit(0); // User cancelled
        }
        
        // Initialize UI and connect to server
        initializeUI();
        connectToServer();
        
        frame.setVisible(true);
    }
    
    /**
     * Initialize the user interface
     */
    private void initializeUI() {
        frame = new JFrame("ChatJar - " + username);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(1000, 700);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());
        
        // Add window close listener
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
                System.exit(0);
            }
        });
        
        // Create main split pane
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setDividerLocation(250);
        mainSplitPane.setResizeWeight(0.2);
        
        // Create sidebar and chat panel
        JPanel sidebar = createSidebar();
        JPanel chatPanel = createChatPanel();
        
        mainSplitPane.setLeftComponent(sidebar);
        mainSplitPane.setRightComponent(chatPanel);
        
        frame.add(mainSplitPane, BorderLayout.CENTER);
        
        setupEventListeners();
        applyTheme();
    }
    
    /**
     * Creates the left sidebar with user info, rooms, and user list
     */
    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(new Color(240, 242, 245));
        sidebar.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // User info panel
        JPanel userPanel = new JPanel(new BorderLayout());
        userPanel.setOpaque(false);
        JLabel userLabel = new JLabel("  👤 " + username);
        userLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        userLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 15, 5));
        userPanel.add(userLabel, BorderLayout.WEST);
        
        // Room selection panel
        JPanel roomPanel = new JPanel(new BorderLayout());
        roomPanel.setOpaque(false);
        roomPanel.setBorder(BorderFactory.createTitledBorder("🏠 Rooms"));
        
        roomSelector = new JComboBox<>(AVAILABLE_ROOMS);
        roomSelector.setSelectedItem(currentRoom);
        roomSelector.setMaximumRowCount(10);
        roomPanel.add(roomSelector, BorderLayout.NORTH);
        
        // User list panel
        JPanel usersPanel = new JPanel(new BorderLayout());
        usersPanel.setOpaque(false);
        usersPanel.setBorder(BorderFactory.createTitledBorder("👥 Online Users (0)"));
        
        usersListModel = new DefaultListModel<>();
        usersList = new JList<>(usersListModel);
        usersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        usersList.setCellRenderer(new UserListCellRenderer());
        
        JScrollPane usersScrollPane = new JScrollPane(usersList);
        usersScrollPane.setPreferredSize(new Dimension(200, 200));
        usersPanel.add(usersScrollPane, BorderLayout.CENTER);
        
        sidebar.add(userPanel, BorderLayout.NORTH);
        sidebar.add(roomPanel, BorderLayout.CENTER);
        sidebar.add(usersPanel, BorderLayout.SOUTH);
        
        return sidebar;
    }
    
    /**
     * Creates the main chat panel with messages and input
     */
    private JPanel createChatPanel() {
        JPanel chatPanel = new JPanel(new BorderLayout());
        
        // Header panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        headerPanel.setBackground(Color.WHITE);
        
        titleLabel = new JLabel("# " + currentRoom);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        headerPanel.add(titleLabel, BorderLayout.WEST);
        
        typingLabel = new JLabel(" ");
        typingLabel.setForeground(Color.GRAY);
        typingLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        headerPanel.add(typingLabel, BorderLayout.EAST);
        
        // Chat area
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setContentType("text/plain");
        chatArea.setBackground(Color.WHITE);
        chatArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Set up styled document for better formatting
        StyledDocument doc = chatArea.getStyledDocument();
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setLineSpacing(attrs, 0.3f);
        doc.setParagraphAttributes(0, 0, attrs, false);
        
        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        chatScrollPane.setBorder(null);
        chatScrollPane.getViewport().setOpaque(false);
        chatScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        
        // Input panel
        JPanel inputPanel = new JPanel(new BorderLayout(10, 10));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        inputPanel.setBackground(Color.WHITE);
        
        emojiButton = new JButton("😊");
        emojiButton.setBorderPainted(false);
        emojiButton.setContentAreaFilled(false);
        emojiButton.setFocusPainted(false);
        emojiButton.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        emojiButton.setToolTipText("Add emoji");
        
        inputField = new JTextField();
        inputField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1, true),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        
        sendButton = new JButton("Send");
        sendButton.setBackground(new Color(0, 120, 212));
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        sendButton.setBorderPainted(false);
        sendButton.setOpaque(true);
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        
        JPanel inputFieldPanel = new JPanel(new BorderLayout(5, 0));
        inputFieldPanel.add(inputField, BorderLayout.CENTER);
        inputFieldPanel.add(sendButton, BorderLayout.EAST);
        
        JPanel bottomInputPanel = new JPanel(new BorderLayout(5, 0));
        bottomInputPanel.add(emojiButton, BorderLayout.WEST);
        bottomInputPanel.add(inputFieldPanel, BorderLayout.CENTER);
        
        inputPanel.add(bottomInputPanel, BorderLayout.CENTER);
        
        chatPanel.add(headerPanel, BorderLayout.NORTH);
        chatPanel.add(chatScrollPane, BorderLayout.CENTER);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);
        
        return chatPanel;
    }
    
    /**
     * Setup event listeners for UI components
     */
    private void setupEventListeners() {
        // Send button
        sendButton.addActionListener(_ -> sendMessage());
        
        // Input field - send on Enter
        inputField.addActionListener(_ -> sendMessage());
        
        // Room selector
        roomSelector.addActionListener(_ -> {
            String selectedRoom = (String) roomSelector.getSelectedItem();
            if (selectedRoom != null && !selectedRoom.equals(currentRoom) && isConnected) {
                joinRoom(selectedRoom);
            }
        });
        
        // Emoji button
        emojiButton.addActionListener(_ -> showEmojiPicker());
    }
    
    /**
     * Apply visual theme to components
     */
    private void applyTheme() {
        frame.getContentPane().setBackground(new Color(245, 245, 245));
        inputField.requestFocusInWindow();
    }
    
    /**
     * Send message to current room
     */
    private void sendMessage() {
        String message = inputField.getText().trim();
        if (!message.isEmpty() && out != null && isConnected) {
            // Format: [Room] user: message
            String formattedMessage = "[" + currentRoom + "] " + username + ": " + message;
            out.println(formattedMessage);
            inputField.setText("");
            inputField.requestFocusInWindow();
        }
    }

    /**
     * Join a chat room
     */
    private void joinRoom(String room) {
        if (room == null || room.isEmpty() || room.equals(currentRoom)) {
            return;
        }

        // Update current room
        currentRoom = room;

        // Clear online users when changing rooms
        onlineUsers.clear();
        updateUsersList();

        // Update UI
        roomSelector.setSelectedItem(room);
        chatArea.setText("");
        
        // Send join message to server if connected
        if (isConnected && out != null) {
            out.println("[JOIN_ROOM] " + username + " " + room);
        }
    }
    
    /**
     * Show emoji selection menu
     */
    private void showEmojiPicker() {
        String[] emojis = {"😊", "😂", "👍", "❤️", "😢", "😮", "😎", "🤔", "🎉", "👋"};
        String selectedEmoji = (String) JOptionPane.showInputDialog(
            frame,
            "Choose an emoji:",
            "Emoji Menu",
            JOptionPane.PLAIN_MESSAGE,
            null,
            emojis,
            emojis[0]
        );

        if (selectedEmoji != null) {
            inputField.setText(inputField.getText() + selectedEmoji);
            inputField.requestFocusInWindow();
        }
    }

    /**
     * Connect to the chat server
     */
    private void connectToServer() {
        try {
            socket = new Socket();
            // Set a read timeout to detect dead connections
            socket.setSoTimeout(60000); // 60 seconds
            socket.connect(new InetSocketAddress(SERVER_HOST, SERVER_PORT), 10000); // 10s connection timeout
            
            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            isConnected = true;
            lastHeartbeatTime = System.currentTimeMillis();
            
            // Start message listener thread
            Thread messageListener = new Thread(this::listenForMessages);
            messageListener.setDaemon(true);
            messageListener.start();
            
            // Start heartbeat thread
            startHeartbeat();
            
            // Join the selected room
            out.println("[JOIN_ROOM] " + username + " " + currentRoom);
            
            appendMessage("✅ Connected to ChatJar server!", false, true);
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                frame,
                "❌ Cannot connect to server at " + SERVER_HOST + ":" + SERVER_PORT + 
                "\n\nError: " + e.getMessage() + 
                "\n\nPlease make sure the server is running.",
                "Connection Error",
                JOptionPane.ERROR_MESSAGE
            );
            System.exit(1);
        }
    }
    
    /**
     * Listen for messages from server
     */
    private void listenForMessages() {
        try {
            String message;
            while (isConnected && (message = in.readLine()) != null) {
                lastHeartbeatTime = System.currentTimeMillis();
                if (message.equals(HEARTBEAT)) {
                    continue; // Skip heartbeat messages
                }
                processServerMessage(message);
            }
        } catch (SocketTimeoutException e) {
            System.err.println("Socket read timeout: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error reading from server: " + e.getMessage());
        } finally {
            if (isConnected) {
                SwingUtilities.invokeLater(() -> {
                    appendMessage("⚠️ Lost connection to server.", false, true);
                    updateConnectionStatus(false);
                });
                isConnected = false;
                updateConnectionStatus(false);
            }
        }
    }
    
    /**
     * Process incoming messages from server
     */
    private void processServerMessage(String message) {
        if (message == null || message.trim().isEmpty()) return;
        
        // Handle user list updates
        if (message.startsWith("[USER_LIST]")) {
            String[] users = message.substring("[USER_LIST]".length()).split(",");
            onlineUsers.clear();
            for (String user : users) {
                if (!user.trim().isEmpty()) {
                    onlineUsers.add(user.trim());
                }
            }
            SwingUtilities.invokeLater(this::updateUsersList);
            return;
        }
        
        try {
            // Check if it's a user list update
            if (message.startsWith("[USERS]")) {
                // Format: [USERS] user1 user2 user3
                String[] users = message.substring(7).trim().split("\\s+");
                onlineUsers.clear();
                for (String user : users) {
                    if (!user.trim().isEmpty()) {
                        onlineUsers.add(user.trim());
                    }
                }
                updateUsersList();
                return;
            }
            
            // Handle chat history messages (they start with [HISTORY])
            if (message.startsWith("[" + MSG_HISTORY + "]")) {
                message = message.substring(MSG_HISTORY.length() + 2).trim();
                appendMessage(message, false, true);
                return;
            }
            
            // Handle regular messages in format: [Room] user: message
            if (message.startsWith("[")) {
                int endBracket = message.indexOf("]");
                if (endBracket > 0) {
                    String room = message.substring(1, endBracket).trim();
                    String rest = message.substring(endBracket + 1).trim();
                    
                    // Skip if not in the current room
                    if (!room.equals(currentRoom)) return;
                    
                    // Check for system messages (join/leave)
                    if (rest.startsWith("*")) {
                        // System message (user join/leave)
                        appendMessage(rest, false, true);
                        
                        // Request updated user list
                        if (out != null) {
                            out.println("[GET_USERS]");
                        }
                    } else {
                        // Regular message
                        appendMessage(rest, isMessageFromMe(rest), false);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing server message: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Start heartbeat mechanism
     */
    private void startHeartbeat() {
        Thread heartbeatThread = new Thread(() -> {
            while (isConnected) {
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL);
                    long timeSinceLastHeartbeat = System.currentTimeMillis() - lastHeartbeatTime;
                    if (timeSinceLastHeartbeat > HEARTBEAT_INTERVAL * 2) {
                        // No response from server for too long, consider connection dead
                        System.err.println("No heartbeat response from server, disconnecting...");
                        disconnect();
                        break;
                    }
                    if (out != null) {
                        out.println(HEARTBEAT);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Error sending heartbeat: " + e.getMessage());
                    disconnect();
                    break;
                }
            }
        });
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }
    
    /**
     * Disconnect from server
     */
    private void disconnect() {
        if (!isConnected) return;
        isConnected = false;
        try {
            if (out != null) {
                out.println("[DISCONNECT]");
                out.close();
            }
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error disconnecting: " + e.getMessage());
        } finally {
            // Clear online users when disconnecting
            onlineUsers.clear();
            updateUsersList();
            updateConnectionStatus(false);
        }
    }
    
    /**
     * Update the users list in the UI
     */
    private void updateUsersList() {
        if (usersListModel == null) return;
        
        usersListModel.clear();
        
        // Add all online users to the list
        for (String user : onlineUsers) {
            usersListModel.addElement(user);
        }
        
        // Update the users list title with count
        updateUsersListTitle(onlineUsers.size());
    }
    
    private void updateUsersListTitle(int count) {
        Container parent = usersList.getParent();
        while (parent != null && !(parent instanceof JPanel)) {
            parent = parent.getParent();
        }
        
        if (parent instanceof JPanel) {
            JPanel panel = (JPanel) parent;
            if (panel.getBorder() instanceof TitledBorder) {
                TitledBorder border = (TitledBorder) panel.getBorder();
                border.setTitle("👥 Online Users (" + count + ")");
                panel.repaint();
            }
        }
    }
    
    /**
     * Update connection status in UI
     */
    private void updateConnectionStatus(boolean connected) {
        SwingUtilities.invokeLater(() -> {
            if (!connected) {
                usersListModel.clear();
                usersListModel.addElement("❌ Disconnected");
                updateUsersListTitle(0);
                
                sendButton.setEnabled(false);
                inputField.setEnabled(false);
                roomSelector.setEnabled(false);
                
                typingLabel.setText("❌ Offline");
            } else {
                sendButton.setEnabled(true);
                inputField.setEnabled(true);
                roomSelector.setEnabled(true);
                
                typingLabel.setText(" ");
            }
        });
    }
    
    /**
     * Append message to chat area with proper formatting
     */
    private void appendMessage(String message, boolean isFromMe, boolean isSystemMessage) {
        if (message == null || message.trim().isEmpty()) return;
        
        try {
            StyledDocument doc = chatArea.getStyledDocument();
            
            // Create styles
            Style defaultStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
            
            Style senderStyle = doc.addStyle("sender", defaultStyle);
            StyleConstants.setBold(senderStyle, true);
            StyleConstants.setFontSize(senderStyle, 13);
            
            Style messageStyle = doc.addStyle("message", defaultStyle);
            StyleConstants.setFontSize(messageStyle, 12);
            
            Style timeStyle = doc.addStyle("time", defaultStyle);
            StyleConstants.setFontSize(timeStyle, 10);
            StyleConstants.setForeground(timeStyle, Color.GRAY);
            
            Style systemStyle = doc.addStyle("system", defaultStyle);
            StyleConstants.setFontSize(systemStyle, 11);
            StyleConstants.setForeground(systemStyle, new Color(102, 102, 102));
            StyleConstants.setItalic(systemStyle, true);
            
            // Format timestamp
            String timestamp = new SimpleDateFormat("HH:mm").format(new Date());
            
            if (isSystemMessage) {
                // System message formatting
                doc.insertString(doc.getLength(), message + "\n", systemStyle);
                doc.insertString(doc.getLength(), timestamp + "\n\n", timeStyle);
            } else {
                // Parse sender and message
                String sender = "";
                String messageText = message;
                
                if (message.contains(":")) {
                    int colonIndex = message.indexOf(":");
                    sender = message.substring(0, colonIndex).trim();
                    messageText = message.substring(colonIndex + 1).trim();
                }
                
                // Set message alignment and background color
                SimpleAttributeSet alignment = new SimpleAttributeSet();
                int align = isFromMe ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT;
                StyleConstants.setAlignment(alignment, align);
                
                // Set background color for messages
                Color bgColor = isFromMe ? new Color(227, 242, 253) : 
                              sender.isEmpty() ? Color.WHITE : new Color(245, 245, 245);
                StyleConstants.setBackground(messageStyle, bgColor);
                
                // Apply alignment to the paragraph
                doc.setParagraphAttributes(doc.getLength(), 1, alignment, false);
                
                // Insert sender name (if not empty)
                if (!sender.isEmpty()) {
                    String displaySender = isFromMe ? "You" : sender;
                    StyleConstants.setForeground(senderStyle, isFromMe ? new Color(0, 120, 212) : Color.BLACK);
                    doc.insertString(doc.getLength(), displaySender + "\n", senderStyle);
                }
                
                // Insert message text
                doc.insertString(doc.getLength(), messageText + "\n", messageStyle);
                
                // Insert timestamp
                doc.insertString(doc.getLength(), timestamp + "\n\n", timeStyle);
            }
            
            // Auto-scroll to bottom
            chatArea.setCaretPosition(doc.getLength());
            
        } catch (BadLocationException e) {
            System.err.println("❌ Error appending message: " + e.getMessage());
        }
    }
    
    /**
     * Check if message is from current user
     */
    private boolean isMessageFromMe(String message) {
        if (message == null) return false;
        return message.startsWith(username + ":") || 
               message.startsWith("You:") || 
               message.startsWith("You ");
    }
    
    /**
     * Custom cell renderer for user list
     */
    private static class UserListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            String text = value.toString();
            if (text.contains("(You)")) {
                setForeground(isSelected ? Color.WHITE : new Color(0, 120, 212));
                setFont(getFont().deriveFont(Font.BOLD));
            } else if (text.equals("❌ Disconnected")) {
                setForeground(isSelected ? Color.WHITE : Color.RED);
                setFont(getFont().deriveFont(Font.ITALIC));
            } else {
                setForeground(isSelected ? Color.WHITE : Color.BLACK);
                setFont(getFont().deriveFont(Font.PLAIN));
            }
            
            return c;
        }
    }
    
    /**
     * Main method to start the chat client
     */
    public static void main(String[] args) {
        // Enable system properties for better font rendering
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        
        SwingUtilities.invokeLater(() -> {
            try {
                new ChatClientUI();
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(
                    null,
                    "❌ Failed to start ChatJar client:\n" + e.getMessage(),
                    "Startup Error",
                    JOptionPane.ERROR_MESSAGE
                );
                System.exit(1);
            }
        });
    }
}