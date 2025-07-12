import java.net.*;
import java.io.*;
import java.util.*;

public class ClientHandler implements Runnable {
    private Socket socket;
    private Set<Socket> clientSockets;
    private BufferedReader in;

    public ClientHandler(Socket socket, Set<Socket> clientSockets) {
        this.socket = socket;
        this.clientSockets = clientSockets;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            System.out.println("Client setup failed: " + e.getMessage());
        }
    }

    public void run() {
        try {
            String msg;
            while ((msg = in.readLine()) != null) {
                System.out.println("Received: " + msg);
                broadcast(msg);
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + socket);
        } finally {
            try {
                clientSockets.remove(socket);
                socket.close();
            } catch (IOException e) {
            }
        }
    }

    private void broadcast(String msg) {
        for (Socket s : clientSockets) {
            try {
                if (s != socket) {
                    PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                    out.println(msg);
                }
            } catch (IOException e) {
            }
        }
    }
}
