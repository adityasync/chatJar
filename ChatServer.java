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
    private String currentRoom = "Sun Squad";
    private boolean isDarkMode = false;

    public ChatClientUI() {
        username = JOptionPane.showInputDialog(null, "Enter your username:");
        if (username == null || username.trim().isEmpty()) {
            JOptionPane.showMessageDialog(null, "Username is required.");
            System.exit(0);
        }

        String[] rooms = {"Sun Squad", "Cake Squad", "Moon Crew", "Star Gang"};
        currentRoom = (String) JOptionPane.showInputDialog(
                null, "Choose a chat room:", "Room Selection",
                JOptionPane.PLAIN_MESSAGE, null, rooms, rooms[0]);
        if (currentRoom == null || currentRoom.trim().isEmpty()) {
            JOptionPane.showMessageDialog(null, "Room selection is required.");
            System.exit(0);
        }

        initUI();
        connectToServer();
    }

    private void initUI() {
        frame = new JFrame(username + " @ " + currentRoom);
        frame.setSize(700, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // Header panel with room label and dark mode toggle
        JPanel header = new JPanel(new BorderLayout());
        JLabel roomLabel = new JLabel("Room: " + currentRoom);
        roomLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        header.add(roomLabel, BorderLayout.WEST);

        typingLabel = new JLabel(" ");
        typingLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        header.add(typingLabel, BorderLayout.EAST);

        themeToggleButton = new JButton("Dark Mode");
        header.add(themeToggleButton, BorderLayout.SOUTH);

        frame.add(header, BorderLayout.NORTH);

        // Chat display area
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);
        frame.add(scrollPane, BorderLayout.CENTER);

        // Input area with emoji and send buttons
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        inputField = new JTextField();
        sendButton = new JButton("Send");
        emojiButton = new JButton("😊");

        JPanel rightPanel = new JPanel(new GridLayout(1, 2, 5, 5));
        rightPanel.add(emojiButton);
        rightPanel.add(sendButton);

        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(rightPanel, BorderLayout.EAST);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        frame.add(bottomPanel, BorderLayout.SOUTH);

        // Listeners
        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());
        emojiButton.addActionListener(e -> showEmojiMenu());
        themeToggleButton.addActionListener(e -> toggleTheme());

        inputField.addKeyListener(new KeyAdapter() {
            public void keyTyped(KeyEvent e) {
                if (out != null) {
                    out.println("[TYPING]" + "|" + username + "|" + currentRoom);
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
        Color bg = isDarkMode ? Color.DARK_GRAY : Color.WHITE;
        Color fg = isDarkMode ? Color.WHITE : Color.BLACK;

        frame.getContentPane().setBackground(bg);
        chatArea.setBackground(bg);
        chatArea.setForeground(fg);
        inputField.setBackground(isDarkMode ? new Color(64, 64, 64) : Color.WHITE);
        inputField.setForeground(fg);
        typingLabel.setForeground(fg);
        sendButton.setBackground(isDarkMode ? new Color(30, 144, 255) : null);
        sendButton.setForeground(isDarkMode ? Color.WHITE : null);
        emojiButton.setBackground(isDarkMode ? new Color(30, 144, 255) : null);
        emojiButton.setForeground(isDarkMode ? Color.WHITE : null);
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (!text.isEmpty() && out != null) {
            String timestamp = new SimpleDateFormat("HH:mm").format(new Date());
            // Format: ROOM|USERNAME|HH:mm|MESSAGE
            String msg = currentRoom + "|" + username + "|" + timestamp + "|" + text;
            out.println(msg);
            appendMessage("Me [" + timestamp + "]: " + text, true);
            inputField.setText("");
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
            chatArea.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 12345);
            out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Inform server of join
            out.println("[JOIN_ROOM]" + "|" + username + "|" + currentRoom);

            new Thread(() -> {
                String msg;
                try {
                    while ((msg = in.readLine()) != null) {
                        // Handle typing notification
                        if (msg.startsWith("[TYPING]")) {
                            // Format: [TYPING]|username|room
                            String[] parts = msg.split("\\|");
                            if (parts.length == 3) {
                                String typer = parts[1];
                                String room = parts[2];
                                if (!typer.equals(username) && room.equals(currentRoom)) {
                                    showTypingIndicator(typer);
                                }
                            }
                            continue;
                        }

                        // Normal message format: ROOM|SENDER|HH:mm|TEXT
                        String[] parts = msg.split("\\|", 4);
                        if (parts.length == 4) {
                            String room = parts[0];
                            String sender = parts[1];
                            String time = parts[2];
                            String text = parts[3];

                            if (room.equals(currentRoom)) {
                                boolean isSelf = sender.equals(username);
                                appendMessage(sender + " [" + time + "]: " + text, isSelf);
                            }
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClientUI::new);
    }
}
