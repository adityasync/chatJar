import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ChatClientUI {
    private JFrame frame;
    private JTextPane chatArea;
    private JTextField inputField;
    private JLabel typingLabel;
    private JButton sendButton, emojiButton, themeToggleButton;
    private PrintWriter out;
    private String username;
    private Socket socket;
    private TrayIcon trayIcon;

    private JComboBox<String> roomSelector;
    private String currentRoom = "";

    private boolean isDarkMode = false;

    private static final String[] ROOMS = {"Sun Squad", "Cake Squad", "Moon Crew", "Star Gang"};

    public ChatClientUI() {
        username = JOptionPane.showInputDialog(null, "Enter your username:");
        if (username == null || username.trim().isEmpty()) {
            JOptionPane.showMessageDialog(null, "Username is required.");
            System.exit(0);
        }

        selectRoom();

        initUI();
        loadHistoryForRoom(currentRoom);
        connectToServer();
        setupSystemTray();
    }

    private void selectRoom() {
        currentRoom = (String) JOptionPane.showInputDialog(
            null,
            "Choose a chat room:",
            "Room Selection",
            JOptionPane.PLAIN_MESSAGE,
            null,
            ROOMS,
            ROOMS[0]);
        if (currentRoom == null || currentRoom.trim().isEmpty()) {
            JOptionPane.showMessageDialog(null, "You must select a room.");
            System.exit(0);
        }
    }

    private JLabel title;  // declare title label globally for easy update

    private void initUI() {
        UIManager.put("TextField.font", new Font("Segoe UI", Font.PLAIN, 14));
        UIManager.put("TextArea.font", new Font("Segoe UI", Font.PLAIN, 14));
        UIManager.put("Label.font", new Font("Segoe UI", Font.PLAIN, 14));
        UIManager.put("Button.font", new Font("Segoe UI", Font.PLAIN, 14));

        frame = new JFrame(username + " @ " + currentRoom);
        frame.setSize(700, 600);
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // Header
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBackground(new Color(30, 144, 255));
        header.setPreferredSize(new Dimension(frame.getWidth(), 50));

        title = new JLabel("  " + currentRoom);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        header.add(title, BorderLayout.WEST);

        // Room selector dropdown
        roomSelector = new JComboBox<>(ROOMS);
        roomSelector.setSelectedItem(currentRoom);
        header.add(roomSelector, BorderLayout.CENTER);

        // Dark mode toggle button
        themeToggleButton = new JButton("Dark Mode");
        header.add(themeToggleButton, BorderLayout.EAST);

        typingLabel = new JLabel(" ");
        typingLabel.setForeground(Color.WHITE);
        typingLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        header.add(typingLabel, BorderLayout.SOUTH);

        frame.add(header, BorderLayout.NORTH);

        // Chat area
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setBackground(new Color(245, 245, 245));
        JScrollPane scrollPane = new JScrollPane(chatArea);
        frame.add(scrollPane, BorderLayout.CENTER);

        // Input area
        inputField = new JTextField();
        sendButton = new JButton("Send");
        emojiButton = new JButton("😊");

        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        JPanel rightPanel = new JPanel(new GridLayout(1, 3, 5, 5)); 
        rightPanel.add(emojiButton);
        rightPanel.add(sendButton);

        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(rightPanel, BorderLayout.EAST);

        frame.add(bottomPanel, BorderLayout.SOUTH);

        // Listeners
        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());
        emojiButton.addActionListener(e -> showEmojiMenu());
        themeToggleButton.addActionListener(e -> toggleTheme());

        inputField.addKeyListener(new KeyAdapter() {
            public void keyTyped(KeyEvent e) {
                if (out != null) {
                    out.println("[TYPING] " + username + "@" + currentRoom);
                }
            }
        });

        roomSelector.addActionListener(e -> {
            String selectedRoom = (String) roomSelector.getSelectedItem();
            if (!selectedRoom.equals(currentRoom)) {
                currentRoom = selectedRoom;
                frame.setTitle(username + " @ " + currentRoom);
                title.setText("  " + currentRoom);
                clearChat();
                loadHistoryForRoom(currentRoom);
                if (out != null) {
                    out.println("[ROOM_CHANGE] " + username + " " + currentRoom);
                }
            }
        });

        applyTheme();

        frame.setVisible(true);
    }

    private void toggleTheme() {
        isDarkMode = !isDarkMode;
        applyTheme();
        themeToggleButton.setText(isDarkMode ? "Light Mode" : "Dark Mode");
    }

    private void applyTheme() {
        if (isDarkMode) {
            frame.getContentPane().setBackground(Color.DARK_GRAY);
            chatArea.setBackground(new Color(34, 34, 34));
            chatArea.setForeground(Color.WHITE);
            typingLabel.setForeground(Color.LIGHT_GRAY);
            inputField.setBackground(new Color(64, 64, 64));
            inputField.setForeground(Color.WHITE);
            sendButton.setBackground(new Color(45, 140, 240));
            sendButton.setForeground(Color.WHITE);
            emojiButton.setBackground(new Color(45, 140, 240));
            emojiButton.setForeground(Color.WHITE);
            roomSelector.setBackground(new Color(64, 64, 64));
            roomSelector.setForeground(Color.WHITE);
        } else {
            frame.getContentPane().setBackground(null);
            chatArea.setBackground(new Color(245, 245, 245));
            chatArea.setForeground(Color.BLACK);
            typingLabel.setForeground(Color.BLACK);
            inputField.setBackground(Color.WHITE);
            inputField.setForeground(Color.BLACK);
            sendButton.setBackground(null);
            sendButton.setForeground(Color.BLACK);
            emojiButton.setBackground(null);
            emojiButton.setForeground(Color.BLACK);
            roomSelector.setBackground(Color.WHITE);
            roomSelector.setForeground(Color.BLACK);
        }
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (!text.isEmpty() && out != null) {
            String timestamp = new SimpleDateFormat("HH:mm").format(new Date());
            String formatted = "[" + currentRoom + "] " + username + " [" + timestamp + "]: " + text;
            out.println(formatted);
            appendMessage("Me [" + timestamp + "]: " + text, true);
            inputField.setText("");
            saveToHistory("Me [" + timestamp + "]: " + text);
        }
    }

    private void appendMessage(String message, boolean isSelf) {
        try {
            StyledDocument doc = chatArea.getStyledDocument();
            Style style = chatArea.addStyle("Style", null);
            StyleConstants.setFontSize(style, 14);
            StyleConstants.setFontFamily(style, "Segoe UI");
            StyleConstants.setForeground(style, isSelf ? new Color(30, 144, 255) : (isDarkMode ? Color.LIGHT_GRAY : Color.DARK_GRAY));
            StyleConstants.setBold(style, isSelf);
            doc.insertString(doc.getLength(), message + "\n", style);

            chatArea.setCaretPosition(chatArea.getDocument().getLength());

        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void clearChat() {
        chatArea.setText("");
    }

    private void loadHistoryForRoom(String room) {
        chatArea.setText("");
        File historyFile = new File("history_" + room.replaceAll("\\s+", "_") + ".txt");
        if (historyFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(historyFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    appendMessage(line, line.startsWith("Me [") || line.contains(username + " ["));
                }
            } catch (IOException e) {
                System.out.println("⚠️ Failed to load history for " + room);
            }
        }
    }

    private void saveToHistory(String msg) {
        File historyFile = new File("history_" + currentRoom.replaceAll("\\s+", "_") + ".txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(historyFile, true))) {
            writer.write(msg + "\n");
        } catch (IOException e) {
            System.out.println("⚠️ Failed to save chat history for " + currentRoom);
        }
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 12345);
            out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println("[JOIN_ROOM] " + username + " " + currentRoom);

            new Thread(() -> {
                String msg;
                try {
                    while ((msg = in.readLine()) != null) {
                        if (msg.startsWith("[TYPING] ")) {
                            String[] parts = msg.substring(8).split("@");
                            if (parts.length == 2) {
                                String typer = parts[0];
                                String room = parts[1];
                                if (!typer.equals(username) && room.equals(currentRoom)) {
                                    showTypingIndicator(typer);
                                }
                            }
                        } else if (msg.startsWith("[" + currentRoom + "] ")) {
                            appendMessage(msg.substring(currentRoom.length() + 3), msg.startsWith("[" + currentRoom + "] " + username));
                            saveToHistory(msg.substring(currentRoom.length() + 3));
                        }
                    }
                } catch (IOException e) {
                    appendMessage("⚠️ Disconnected from server.", false);
                }
            }).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "❌ Cannot connect to server", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void showTypingIndicator(String user) {
        typingLabel.setText(user + " is typing...");
        Timer timer = new Timer(2000, e -> typingLabel.setText(" "));
        timer.setRepeats(false);
        timer.start();
    }

    private void showEmojiMenu() {
        JPopupMenu emojiMenu = new JPopupMenu();
        String[] emojis = {"😊", "😂", "😢", "❤️", "👍", "🎉"};
        for (String emoji : emojis) {
            JMenuItem item = new JMenuItem(emoji);
            item.addActionListener(e -> inputField.setText(inputField.getText() + emoji));
            emojiMenu.add(item);
        }
        emojiMenu.show(emojiButton, 0, emojiButton.getHeight());
    }

    private void setupSystemTray() {
        if (!SystemTray.isSupported()) return;

        SystemTray tray = SystemTray.getSystemTray();
        Image icon = Toolkit.getDefaultToolkit().createImage(new byte[0]);
        trayIcon = new TrayIcon(icon, "Java Group Chat");
        trayIcon.setImageAutoSize(true);
        trayIcon.setToolTip("Java Chat Running");

        PopupMenu popup = new PopupMenu();
        MenuItem openItem = new MenuItem("Open Chat");
        openItem.addActionListener(e -> frame.setVisible(true));
        popup.add(openItem);

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));
        popup.add(exitItem);

        trayIcon.setPopupMenu(popup);
        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            e.printStackTrace();
        }

        frame.addWindowStateListener(e -> {
            if (e.getNewState() == Frame.ICONIFIED) {
                frame.setVisible(false);
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClientUI::new);
    }
}
