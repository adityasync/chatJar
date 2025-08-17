import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages chat history for different rooms
 */
public class ChatHistoryManager {
    private static final String HISTORY_DIR = "chat_history";
    private static final int MAX_HISTORY_PER_ROOM = 1000; // Max messages per room to store
    
    private final Map<String, List<String>> roomHistories;
    
    public ChatHistoryManager() {
        this.roomHistories = new ConcurrentHashMap<>();
        createHistoryDirectory();
        loadAllHistories();
    }
    
    private void createHistoryDirectory() {
        try {
            Files.createDirectories(Paths.get(HISTORY_DIR));
        } catch (IOException e) {
            System.err.println("Failed to create chat history directory: " + e.getMessage());
        }
    }
    
    /**
     * Add a message to a room's history
     */
    public void addMessage(String room, String message) {
        if (room == null || message == null) return;
        
        roomHistories.computeIfAbsent(room, _ -> new LinkedList<>());
        List<String> history = roomHistories.get(room);
        
        synchronized (history) {
            history.add(message);
            // Trim history if it gets too large
            while (history.size() > MAX_HISTORY_PER_ROOM) {
                history.remove(0);
            }
        }
        
        // Save to file asynchronously
        saveRoomHistory(room);
    }
    
    /**
     * Get recent messages from a room's history
     * @param room Room name
     * @param limit Maximum number of messages to return
     * @return List of recent messages
     */
    public List<String> getRecentMessages(String room, int limit) {
        if (room == null) return Collections.emptyList();
        
        List<String> history = roomHistories.get(room);
        if (history == null) return Collections.emptyList();
        
        synchronized (history) {
            int fromIndex = Math.max(0, history.size() - limit);
            return new ArrayList<>(history.subList(fromIndex, history.size()));
        }
    }
    
    /**
     * Save a room's history to disk
     */
    private void saveRoomHistory(String room) {
        List<String> history = roomHistories.get(room);
        if (history == null) return;
        
        Path file = Paths.get(HISTORY_DIR, sanitizeFilename(room) + ".log");
        
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file.toFile()), "UTF-8"))) {
            
            synchronized (history) {
                for (String message : history) {
                    writer.println(message);
                }
            }
            
        } catch (IOException e) {
            System.err.println("Failed to save chat history for room " + room + ": " + e.getMessage());
        }
    }
    
    /**
     * Load all room histories from disk
     */
    private void loadAllHistories() {
        File dir = new File(HISTORY_DIR);
        if (!dir.exists() || !dir.isDirectory()) return;
        
        File[] files = dir.listFiles((_, name) -> name.endsWith(".log"));
        if (files == null) return;
        
        for (File file : files) {
            String room = file.getName().replace(".log", "");
            List<String> history = new LinkedList<>();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        history.add(line);
                    }
                }
                
                if (!history.isEmpty()) {
                    roomHistories.put(room, history);
                }
                
            } catch (IOException e) {
                System.err.println("Failed to load chat history from " + file.getName() + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Sanitize room name for use as filename
     */
    private String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9.-]+", "_");
    }
}
