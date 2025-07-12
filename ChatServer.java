import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 12345;

    // Map room name -> list of clients in that room
    private static final Map<String, CopyOnWriteArrayList<ClientHandler>> rooms = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Server started on port " + PORT);

        while (true) {
            Socket socket = serverSocket.accept();
            new ClientHandler(socket).start();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private String username;
        private String room;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                out = new PrintWriter(socket.getOutputStream(), true);

                // Read username and room from client commands
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("[JOIN_ROOM] ")) {
                        String[] parts = line.substring(11).split(" ", 2);
                        if (parts.length == 2) {
                            username = parts[0];
                            String newRoom = parts[1];
                            joinRoom(newRoom);
                        }
                    } else if (line.startsWith("[ROOM_CHANGE] ")) {
                        String[] parts = line.substring(13).split(" ", 2);
                        if (parts.length == 2) {
                            String user = parts[0];
                            String newRoom = parts[1];
                            if (user.equals(username)) {
                                joinRoom(newRoom);
                            }
                        }
                    } else if (line.startsWith("[TYPING] ")) {
                        broadcastTyping(line.substring(8));
                    } else {
                        // Regular message
                        broadcastMessage(line);
                    }
                }
            } catch (IOException e) {
                System.out.println("Client disconnected: " + username);
            } finally {
                leaveRoom();
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void joinRoom(String newRoom) {
            leaveRoom();
            this.room = newRoom;

            rooms.putIfAbsent(room, new CopyOnWriteArrayList<>());
            rooms.get(room).add(this);

            System.out.println(username + " joined room " + room);
        }

        private void leaveRoom() {
            if (room != null && rooms.containsKey(room)) {
                rooms.get(room).remove(this);
                if (rooms.get(room).isEmpty()) {
                    rooms.remove(room);
                }
                room = null;
            }
        }

        private void broadcastMessage(String msg) {
            if (room == null) return;

            for (ClientHandler client : rooms.get(room)) {
                client.out.println(msg);
            }
        }

        private void broadcastTyping(String typingMsg) {
            if (room == null) return;

            for (ClientHandler client : rooms.get(room)) {
                client.out.println("[TYPING] " + typingMsg);
            }
        }
    }
}
