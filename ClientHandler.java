import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * ClientHandler - Handles individual client connections and manages
 * room-based messaging, user management, and client communication.
 */
public class ClientHandler implements Runnable {
    // Room management - maps room names to sets of connected clients
    public static final Map<String, Set<ClientHandler>> roomClients = new ConcurrentHashMap<>();
    
    // Client connection components
    private final Socket socket;
    private final Set<Socket> clientSockets;
    @SuppressWarnings("unused")
    private final Map<String, String> onlineUsers;
    private PrintWriter out;
    private BufferedReader in;
    
    // Client state
    private String username;
    private String currentRoom;
    protected volatile boolean isConnected = true;
    
    /**
     * Check if the client is connected
     */
    public boolean isConnected() {
        return isConnected;
    }
    
    public ClientHandler(Socket socket, Set<Socket> clientSockets, Map<String, String> onlineUsers) {
        this.socket = socket;
        this.clientSockets = clientSockets;
        this.onlineUsers = onlineUsers;
        
        try {
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            System.err.println("❌ Failed to setup client streams: " + e.getMessage());
            cleanup();
        }
    }
    
    /**
     * Send a message to this client
     */
    public void sendMessage(String message) {
        if (out != null && message != null) {
            out.println(message);
        }
    }
    
    /**
     * Handle joining a room
     */
    private void joinRoom(String room) {
        if (room == null || room.trim().isEmpty()) return;
        
        // Leave current room if any
        leaveCurrentRoom();
        
        // Add to new room
        roomClients.computeIfAbsent(room, _ -> ConcurrentHashMap.newKeySet()).add(this);
        currentRoom = room;
        
        // Send chat history for the room
        List<String> history = ChatServer.getChatHistory(room);
        for (String message : history) {
            sendMessage(message);
        }
        
        // Broadcast updated user list to all clients in the room
        broadcastUserList(room);
    }
    
    /**
     * Leave current room if any
     */
    private void leaveCurrentRoom() {
        if (currentRoom != null) {
            Set<ClientHandler> room = roomClients.get(currentRoom);
            if (room != null) {
                room.remove(this);
                if (room.isEmpty()) {
                    roomClients.remove(currentRoom);
                } else {
                    // Broadcast updated user list to remaining clients in the room
                    broadcastUserList(currentRoom);
                }
            }
            currentRoom = null;
        }
    }
    
    @Override
    public void run() {
        System.out.println("🔄 Client handler started for: " + socket.getInetAddress());
        
        try {
            String message;
            while (isConnected && (message = in.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                
                if (message.startsWith("[DISCONNECT]")) {
                    System.out.println("👋 Client requested disconnect: " + username);
                    break;
                }
                
                handleMessage(message.trim());
            }
        } catch (IOException e) {
            if (isConnected) {
                System.out.println("🔌 Client disconnected unexpectedly: " + 
                    (username != null ? username : socket.getInetAddress()));
            }
        } finally {
            cleanup();
        }
    }
    
    /**
     * Processes incoming messages from client
     */
    private void handleMessage(String message) {
        if (message.isEmpty()) return;
        
        System.out.println("📨 Received from " + (username != null ? username : "unknown") + ": " + message);
        
        try {
            if (message.startsWith("[JOIN_ROOM] ")) {
                // Handle join room command: [JOIN_ROOM] username room
                String[] parts = message.substring(11).trim().split("\\s+", 2);
                if (parts.length == 2) {
                    this.username = parts[0];
                    joinRoom(parts[1]);
                }
            } else if (message.equals("[GET_USERS]")) {
                sendUserList();
            } else if (message.startsWith("[TYPING] ")) {
                handleTyping(message);
            } else if (message.startsWith("[ROOM_CHANGE] ")) {
                handleRoomChange(message);
            } else if (message.startsWith("[")) {
                int endBracket = message.indexOf("]");
                if (endBracket > 0) {
                    String room = message.substring(1, endBracket).trim();
                    String content = message.substring(endBracket + 1).trim();
                    
                    // If this is a new room for the client, join it
                    if (currentRoom == null || !currentRoom.equals(room)) {
                        joinRoom(room);
                    }
                    
                    // If this is a chat message (contains a colon after room name)
                    if (content.contains(":")) {
                        // Save to chat history
                        ChatServer.addToHistory(room, message);
                        // Broadcast to all in the room
                        broadcastToRoom(message);
                    } else {
                        // Handle other types of messages
                        broadcastToRoom(message);
                    }
                }
            } else {
                System.out.println("⚠️ Unknown message format: " + message);
            }
        } catch (Exception e) {
            System.err.println("❌ Error handling message: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Broadcast updated user list to all clients in the room
     */
    private void broadcastUserList(String room) {
        Set<ClientHandler> roomClients = ClientHandler.roomClients.get(room);
        if (roomClients != null && !roomClients.isEmpty()) {
            // Build user list message
            StringBuilder userList = new StringBuilder("[USER_LIST]");
            for (ClientHandler client : roomClients) {
                if (client.username != null && !client.username.isEmpty()) {
                    userList.append(client.username).append(",");
                }
            }
            // Remove trailing comma if any users exist
            if (userList.length() > "[USER_LIST]".length()) {
                userList.setLength(userList.length() - 1);
            }
            
            // Send to each client in the room
            for (ClientHandler client : roomClients) {
                client.sendMessage(userList.toString());
            }
        }
    }
    
    /**
     * Handles room change request
     */
    private void handleRoomChange(String message) {
        String[] parts = message.substring(14).trim().split("\\s+", 2);
        if (parts.length < 2) {
            out.println("[ERROR] Invalid ROOM_CHANGE format");
            return;
        }
        
        String oldRoom = currentRoom;
        String newRoom = parts[1].trim();
        
        if (newRoom.isEmpty()) {
            out.println("[ERROR] Invalid room name");
            return;
        }
        
        System.out.println("🔄 " + username + " changing from '" + oldRoom + "' to '" + newRoom + "'");
        
        // Remove from old room
        if (oldRoom != null) {
            Set<ClientHandler> oldRoomClients = roomClients.get(oldRoom);
            if (oldRoomClients != null) {
                oldRoomClients.remove(this);
                if (oldRoomClients.isEmpty()) {
                    roomClients.remove(oldRoom);
                }
                // Notify old room
                broadcastToRoom("[" + oldRoom + "] 👋 " + username + " has left the room", oldRoom);
            }
        }
        
        // Join new room
        currentRoom = newRoom;
        ChatServer.updateUserRoom(username, currentRoom);
        roomClients.computeIfAbsent(currentRoom, _ -> ConcurrentHashMap.newKeySet()).add(this);
        
        // Notify new room
        broadcastToRoom("[" + currentRoom + "] 🎉 " + username + " has joined the room");
        broadcastUserListToRoom();
    }
    
    /**
     * Handles typing indicator
     */
    private void handleTyping(String message) {
        if (currentRoom != null) {
            broadcastToRoom(message);
        }
    }
    
    /**
     * Sends user list to requesting client
     */
    private void sendUserList() {
        Set<ClientHandler> roomUsers = roomClients.getOrDefault(currentRoom, Collections.emptySet());
        StringBuilder userList = new StringBuilder("[USERS]");
        
        for (ClientHandler client : roomUsers) {
            if (client.username != null) {
                userList.append(" ").append(client.username);
            }
        }
        
        out.println(userList.toString());
    }
    
    /**
     * Broadcasts message to all users in current room
     */
    private void broadcastToRoom(String message) {
        broadcastToRoom(message, currentRoom);
    }
    
    /**
     * Broadcasts message to all users in specified room
     */
    private void broadcastToRoom(String message, String room) {
        if (room == null) return;
        
        Set<ClientHandler> roomUsers = roomClients.getOrDefault(room, Collections.emptySet());
        
        // Create a copy to avoid concurrent modification
        Set<ClientHandler> usersCopy = new HashSet<>(roomUsers);
        
        for (ClientHandler client : usersCopy) {
            if (client.isConnected() && client.out != null) {
                try {
                    client.out.println(message);
                } catch (Exception e) {
                    System.err.println("❌ Error sending to " + client.username + ": " + e.getMessage());
                    // Remove disconnected client
                    roomUsers.remove(client);
                    client.cleanup();
                }
            }
        }
    }
    
    /**
     * Broadcasts updated user list to all users in current room
     */
    private void broadcastUserListToRoom() {
        if (currentRoom == null) return;
        
        String userList = ChatServer.getOnlineUsersList();
        Set<ClientHandler> roomUsers = roomClients.getOrDefault(currentRoom, Collections.emptySet());
        
        for (ClientHandler client : new HashSet<>(roomUsers)) {
            if (client.isConnected() && client.out != null) {
                try {
                    client.out.println("[USERS]" + userList);
                } catch (Exception e) {
                    System.err.println("❌ Error sending user list to " + client.username + ": " + e.getMessage());
                    roomUsers.remove(client);
                    client.cleanup();
                }
            }
        }
    }
    
    /**
     * Cleanup resources when client disconnects
     */
    private void cleanup() {
        isConnected = false;
        leaveCurrentRoom();
        if (username != null) {
            ChatServer.removeUser(username);
            System.out.println("🧹 Cleanup completed for: " + username);
            username = null;
        } else {
            System.out.println("🧹 Cleanup completed for unknown client");
        }
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            clientSockets.remove(socket);
        } catch (IOException e) {
            System.err.println("❌ Error cleaning up client resources: " + e.getMessage());
        }
    }
}