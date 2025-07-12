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
    private JButton sendButton, emojiButton;
    private PrintWriter out;
    private String username;
    private Socket socket;
    private TrayIcon trayIcon;

    public ChatClientUI() {
        username = JOptionPane.showInputDialog(null, "Enter your username:");
        if (username == null || username.trim().isEmpty()) {
            JOptionPane.showMessageDialog(null, "Username is required.");
            System.exit(0);
        }

        initUI();
        connectToServer();
        setupSystemTray();
    }

    private void initUI() {
        UIManager.put("TextField.font", new Font("Segoe UI", Font.PLAIN, 14));
        UIManager.put("TextArea.font", new Font("Segoe UI", Font.PLAIN, 14));
        UIManager.put("Label.font", new Font("Segoe UI", Font.PLAIN, 14));
        UIManager.put("Button.font", new Font("Segoe UI", Font.PLAIN, 14));

        frame = new JFrame("Group Chat - " + username);
        frame.setSize(650, 600);
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(30, 144, 255));
        header.setPreferredSize(new Dimension(frame.getWidth(), 50));
        JLabel title = new JLabel("  Group Chat");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        header.add(title, BorderLayout.WEST);

        typingLabel = new JLabel(" ");
        typingLabel.setForeground(Color.WHITE);
        typingLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        header.add(typingLabel, BorderLayout.EAST);
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
        JPanel rightPanel = new JPanel(new GridLayout(1, 2, 5, 5));
        rightPanel.add(emojiButton);
        rightPanel.add(sendButton);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(rightPanel, BorderLayout.EAST);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());
        emojiButton.addActionListener(e -> showEmojiMenu());

        inputField.addKeyListener(new KeyAdapter() {
            public void keyTyped(KeyEvent e) {
                out.println("[TYPING] " + username);
            }
        });

        frame.setVisible(true);
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (!text.isEmpty()) {
            String timestamp = new SimpleDateFormat("HH:mm").format(new Date());
            String formatted = username + " [" + timestamp + "]: " + text;
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
            StyleConstants.setForeground(style, isSelf ? new Color(30, 144, 255) : Color.DARK_GRAY);
            StyleConstants.setBold(style, isSelf);
            doc.insertString(doc.getLength(), message + "\n", style);

            chatArea.setCaretPosition(chatArea.getDocument().getLength());

        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 12345);  // replace with IP for LAN
            out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            new Thread(() -> {
                String msg;
                try {
                    while ((msg = in.readLine()) != null) {
                        if (msg.startsWith("[TYPING] ")) {
                            String typer = msg.substring(9);
                            if (!typer.equals(username)) {
                                showTypingIndicator(typer);
                            }
                        } else {
                            appendMessage(msg, msg.startsWith(username + " "));
                            saveToHistory(msg);
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

    private void saveToHistory(String msg) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("chat-history.txt", true))) {
            writer.write(msg + "\n");
        } catch (IOException e) {
            System.out.println("⚠️ Failed to save chat history.");
        }
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
