import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * ChatServer - Multi-room chat server that handles client connections
 * and manages room-based messaging system with enhanced features.
 */
public class ChatServer {
    // Configuration
    private static int PORT = 8888;
    private static final int MAX_CONNECTIONS = 1000;
    private static final int CONNECTION_RATE_LIMIT = 10; // Max connections per second
    private static final int SOCKET_TIMEOUT = 30000; // 30 seconds

    // Server state
    private static final Set<Socket> clientSockets = Collections.synchronizedSet(new HashSet<>());
    private static final ChatHistoryManager chatHistoryManager = new ChatHistoryManager();
    private static final Map<String, String> onlineUsers = new ConcurrentHashMap<>();
    private static volatile boolean isRunning = true;
    private static final AtomicInteger connectionCount = new AtomicInteger(0);
    private static final AtomicLong totalConnections = new AtomicLong(0);
    private static final AtomicInteger rejectedConnections = new AtomicInteger(0);
    private static final RateLimiter rateLimiter = new RateLimiter(CONNECTION_RATE_LIMIT, 1000);
    private static final ExecutorService clientHandlerPool = Executors.newCachedThreadPool();
    private static final SimpleDateFormat logDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // Statistics
    private static long serverStartTime = System.currentTimeMillis();

    public static void main(String[] args) {
        // Parse command line arguments
        parseArguments(args);

        log("🚀 Chat Server starting on port " + PORT + "...");

        // Add shutdown hook for graceful server shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log("\n🛑 Shutting down server...");
            isRunning = false;
            closeAllConnections();
            clientHandlerPool.shutdown();
            try {
                if (!clientHandlerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    clientHandlerPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                clientHandlerPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log("Server shutdown complete");
        }));

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            serverSocket.setSoTimeout(1000); // Check for shutdown every second
            log("✅ Chat Server is running on port " + PORT);
            log("💡 Press Ctrl+C to stop the server");
            log("📊 Server info: Max connections: " + MAX_CONNECTIONS + 
                ", Connection timeout: " + SOCKET_TIMEOUT/1000 + "s");

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();

                    // Check connection limits
                    if (connectionCount.get() >= MAX_CONNECTIONS) {
                        rejectConnection(clientSocket, "Server at capacity");
                        continue;
                    }

                    // Check rate limiting
                    if (!rateLimiter.allowRequest()) {
                        rejectConnection(clientSocket, "Connection rate limit exceeded");
                        continue;
                    }

                    // Configure socket
                    clientSocket.setSoTimeout(SOCKET_TIMEOUT);
                    clientSockets.add(clientSocket);
                    connectionCount.incrementAndGet();
                    totalConnections.incrementAndGet();

                    log("🔗 New client connected: " + 
                        clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort() +
                        " (Active: " + connectionCount.get() + ", Total: " + totalConnections.get() + ")");

                    // Submit client handler to thread pool
                    clientHandlerPool.submit(() -> {
                        ClientHandler handler = new ClientHandler(clientSocket, clientSockets, onlineUsers);
                        try {
                            handler.run();
                        } finally {
                            connectionCount.decrementAndGet();
                        }
                    });

                } catch (SocketTimeoutException e) {
                    // Expected timeout for shutdown check
                } catch (IOException e) {
                    if (isRunning) {
                        logError("Error accepting client connection", e);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Server exception: " + e.getMessage());
        }

        System.out.println("👋 Server stopped");
    }

    /**
     * Updates user's current room
     */
    /**
     * Add a message to the chat history
     */
    public static synchronized void addToHistory(String room, String message) {
        if (room != null && message != null) {
            chatHistoryManager.addMessage(room, message);
        }
    }
    
    /**
     * Update user's current room
     */
    public static synchronized void updateUserRoom(String username, String room) {
        if (username != null && room != null && !username.trim().isEmpty() && !room.trim().isEmpty()) {
            onlineUsers.put(username, room);
            log("👤 User '" + username + "' joined room '" + room + "'");
        }
    }

    /**
        // Send to all clients in the room
        for (ClientHandler client : clientsCopy) {
            if (client.isConnected()) {
                client.sendMessage(message);
            }
        }
        
        // If this is a join/leave message, update user lists for everyone in the room
        if (message.contains(" joined ") || message.contains(" left ")) {
            broadcastUserListToRoom(room);
        }
    }
    
    /**
     * Broadcast updated user list to all clients in a room
     */
    private static void broadcastUserListToRoom(String room) {
        if (room == null) return;
        
        Set<ClientHandler> roomClients = ClientHandler.roomClients.get(room);
        if (roomClients == null) return;
        
        // Get all users in this room
        Set<String> usersInRoom = new HashSet<>();
        for (Map.Entry<String, String> entry : onlineUsers.entrySet()) {
            if (room.equals(entry.getValue())) {
                usersInRoom.add(entry.getKey());
            }
        }
        
        String userList = "[USERS] " + String.join(" ", usersInRoom);
        
        // Send to all clients in the room
        for (ClientHandler client : roomClients) {
            if (client.isConnected()) {
                client.sendMessage(userList);
            }
        }
    }

    /**
     * Removes user from online users list
     */
    public static synchronized void removeUser(String username) {
        if (username != null && !username.trim().isEmpty()) {
            String room = onlineUsers.remove(username);
            if (room != null) {
                log("👋 User '" + username + "' left from room '" + room + "'");
            }
        }
    }

    /**
     * Gets list of all online users as space-separated string
     */
    public static synchronized String getOnlineUsersList() {
        return String.join(" ", onlineUsers.keySet());
    }

    /**
     * Gets the room that a user is currently in
     */
    public static synchronized String getUserRoom(String username) {
        return onlineUsers.get(username);
    }

    /**
     * Gets count of online users
     */
    public static synchronized int getOnlineUsersCount() {
        return onlineUsers.size();
    }

    /**
     * Gets users in a specific room
     */
    public static synchronized Set<String> getUsersInRoom(String room) {
        Set<String> usersInRoom = new HashSet<>();
        for (Map.Entry<String, String> entry : onlineUsers.entrySet()) {
            if (room.equals(entry.getValue())) {
                usersInRoom.add(entry.getKey());
            }
        }
        return usersInRoom;
    }
    
    /**
     * Get chat history for a room
     */
    public static List<String> getChatHistory(String room) {
        return chatHistoryManager.getRecentMessages(room, 100); // Last 100 messages
    }

    /**
     * Parse command line arguments
     */
    private static void parseArguments(String[] args) {
        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "-p":
                    case "--port":
                        if (i + 1 < args.length) {
                            PORT = Integer.parseInt(args[++i]);
                        }
                        break;
                    case "-h":
                    case "--help":
                        printHelp();
                        System.exit(0);
                        break;
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing arguments: " + e.getMessage());
            printHelp();
            System.exit(1);
        }
    }

    /**
     * Print help message
     */
    private static void printHelp() {
        System.out.println("\nChatServer - Multi-room chat server");
        System.out.println("Usage: java ChatServer [options]");
        System.out.println("Options:");
        System.out.println("  -p, --port PORT    Set server port (default: 8888)");
        System.out.println("  -h, --help         Show this help message\n");
    }

    /**
     * Reject a connection with a message
     */
    private static void rejectConnection(Socket socket, String reason) {
        try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println("[ERROR] " + reason);
            log("❌ Connection rejected: " + reason + " from " + 
                socket.getInetAddress().getHostAddress());
            rejectedConnections.incrementAndGet();
        } catch (IOException e) {
            logError("Error sending rejection message", e);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                logError("Error closing rejected socket", e);
            }
        }
    }

    /**
     * Closes all client connections gracefully
     */
    private static void closeAllConnections() {
        log("Closing all client connections...");
        int count = 0;
        synchronized (clientSockets) {
            count = clientSockets.size();
            for (Socket socket : clientSockets) {
                try {
                    if (!socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException e) {
                    logError("Error closing client socket", e);
                }
            }
            clientSockets.clear();
        }
        onlineUsers.clear();
        log("Closed " + count + " client connections");
    }

    /**
     * Get server statistics
     */
    public static synchronized String getServerStats() {
        long uptime = (System.currentTimeMillis() - serverStartTime) / 1000;
        long hours = uptime / 3600;
        long minutes = (uptime % 3600) / 60;
        long seconds = uptime % 60;

        return String.format(
            "📊 Server Statistics:%n" +
            "• Uptime: %d hours, %d minutes, %d seconds%n" +
            "• Active connections: %d%n" +
            "• Total connections: %d%n" +
            "• Rejected connections: %d%n" +
            "• Online users: %d%n" +
            "• Active rooms: %d",
            hours, minutes, seconds,
            connectionCount.get(),
            totalConnections.get(),
            rejectedConnections.get(),
            onlineUsers.size(),
            new HashSet<>(onlineUsers.values()).size()
        );
    }

    /**
     * Log a message with timestamp
     */
    private static void log(String message) {
        System.out.printf("[%s] %s%n", logDateFormat.format(new Date()), message);
    }

    /**
     * Log an error with stack trace
     */
    private static void logError(String message, Throwable t) {
        System.err.printf("[%s] ❌ %s: %s%n", 
            logDateFormat.format(new Date()), message, t.getMessage());
    }
    
    /**
     * Simple rate limiter implementation using a sliding window algorithm
     */
    private static class RateLimiter {
        private final int maxRequests;
        private final long timeWindowInMillis;
        private final Queue<Long> requestTimes;
        
        public RateLimiter(int maxRequests, long timeWindowInMillis) {
            this.maxRequests = maxRequests;
            this.timeWindowInMillis = timeWindowInMillis;
            this.requestTimes = new ConcurrentLinkedQueue<>();
        }
        
        public synchronized boolean allowRequest() {
            long currentTime = System.currentTimeMillis();
            
            // Remove timestamps older than the time window
            while (!requestTimes.isEmpty() && 
                   currentTime - requestTimes.peek() > timeWindowInMillis) {
                requestTimes.poll();
            }
            
            // Check if we can allow the request
            if (requestTimes.size() < maxRequests) {
                requestTimes.offer(currentTime);
                return true;
            }
            
            return false;
        }
    }
}