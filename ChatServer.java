import java.net.*;
import java.io.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 12345;
    static Set<Socket> clientSockets = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        // socket = new Socket("192.168.1.4", 12345);  // replace with IP for LAN
        System.out.println("Server started on port " + PORT);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            clientSockets.add(clientSocket);
            new Thread(new ClientHandler(clientSocket, clientSockets)).start();
        }
    }
}
