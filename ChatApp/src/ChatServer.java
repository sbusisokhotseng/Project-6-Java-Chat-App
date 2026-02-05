
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 12345;
    private static final int FILE_PORT = 12346;
    private static Set<ClientHandler> clientHandlers = Collections.synchronizedSet(new HashSet<>());
    private static Map<String, FileInfo> sharedFiles = new ConcurrentHashMap<>();
    private static ExecutorService threadPool = Executors.newCachedThreadPool();
    
    public static void main(String[] args) {
        System.out.println("Chat Server started on port " + PORT);
        System.out.println("File Server started on port " + FILE_PORT);
        
        // Start main chat server
        new Thread(() -> startChatServer()).start();
        
        // Start file transfer server
        new Thread(() -> startFileServer()).start();
    }
    
    private static void startChatServer() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected: " + socket.getInetAddress());
                
                ClientHandler clientHandler = new ClientHandler(socket);
                clientHandlers.add(clientHandler);
                threadPool.execute(clientHandler);
            }
        } catch (IOException e) {
            System.err.println("Chat server error: " + e.getMessage());
        }
    }
    
    private static void startFileServer() {
        try (ServerSocket fileServerSocket = new ServerSocket(FILE_PORT)) {
            while (true) {
                Socket fileSocket = fileServerSocket.accept();
                threadPool.execute(new FileHandler(fileSocket));
            }
        } catch (IOException e) {
            System.err.println("File server error: " + e.getMessage());
        }
    }
    
    public static void broadcast(String message, ClientHandler excludeClient) {
        synchronized (clientHandlers) {
            for (ClientHandler client : clientHandlers) {
                if (client != excludeClient) {
                    client.sendMessage(message);
                }
            }
        }
    }
    
    public static void sendPrivateMessage(String recipient, String message, ClientHandler sender) {
        synchronized (clientHandlers) {
            for (ClientHandler client : clientHandlers) {
                if (client.getUsername().equalsIgnoreCase(recipient)) {
                    client.sendMessage("[Private from " + sender.getUsername() + "]: " + message);
                    sender.sendMessage("[Private to " + recipient + "]: " + message);
                    return;
                }
            }
            sender.sendMessage("User " + recipient + " not found.");
        }
    }
    
    public static void shareFile(String filename, long fileSize, String sender, String fileId) {
        FileInfo fileInfo = new FileInfo(filename, fileSize, sender, fileId);
        sharedFiles.put(fileId, fileInfo);
        broadcast("[FILE] " + sender + " shared: " + filename + " (" + formatFileSize(fileSize) + ") - ID: " + fileId, null);
    }
    
    public static FileInfo getFileInfo(String fileId) {
        return sharedFiles.get(fileId);
    }
    
    private static String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }
    
    public static void removeClient(ClientHandler client) {
        clientHandlers.remove(client);
        System.out.println("Client disconnected. Total clients: " + clientHandlers.size());
    }
    
    public static List<String> getOnlineUsers() {
        List<String> users = new ArrayList<>();
        synchronized (clientHandlers) {
            for (ClientHandler client : clientHandlers) {
                users.add(client.getUsername());
            }
        }
        return users;
    }
    
    public static Map<String, FileInfo> getSharedFiles() {
        return new HashMap<>(sharedFiles);
    }
    
    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;
        
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }
        
        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                
                // Ask for username
                out.println("Enter your username:");
                username = in.readLine();
                System.out.println(username + " joined the chat");
                
                // Send list of online users
                sendOnlineUsers();
                broadcast(username + " joined the chat!", this);
                
                String clientMessage;
                while ((clientMessage = in.readLine()) != null) {
                    if (clientMessage.equalsIgnoreCase("exit")) {
                        break;
                    } else if (clientMessage.startsWith("@")) {
                        // Private message
                        String[] parts = clientMessage.split(" ", 2);
                        if (parts.length == 2) {
                            String recipient = parts[0].substring(1);
                            String message = parts[1];
                            sendPrivateMessage(recipient, message, this);
                        }
                    } else if (clientMessage.equalsIgnoreCase("!users")) {
                        // List online users
                        sendOnlineUsers();
                    } else if (clientMessage.equalsIgnoreCase("!files")) {
                        // List shared files
                        sendSharedFiles();
                    } else if (clientMessage.startsWith("!download ")) {
                        // Request file download
                        String fileId = clientMessage.substring(10);
                        FileInfo fileInfo = getFileInfo(fileId);
                        if (fileInfo != null) {
                            out.println("DOWNLOAD_READY:" + fileId + ":" + fileInfo.getFilename() + 
                                      ":" + fileInfo.getFileSize());
                        } else {
                            out.println("ERROR:File not found");
                        }
                    } else {
                        // Regular chat message
                        System.out.println(username + ": " + clientMessage);
                        broadcast(username + ": " + clientMessage, this);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error with client " + username + ": " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                removeClient(this);
                if (username != null) {
                    broadcast(username + " left the chat!", null);
                }
            }
        }
        
        private void sendOnlineUsers() {
            List<String> users = getOnlineUsers();
            out.println("=== Online Users (" + users.size() + ") ===");
            for (String user : users) {
                out.println("- " + user);
            }
            out.println("========================");
        }
        
        private void sendSharedFiles() {
            Map<String, FileInfo> files = getSharedFiles();
            out.println("=== Shared Files (" + files.size() + ") ===");
            for (Map.Entry<String, FileInfo> entry : files.entrySet()) {
                FileInfo info = entry.getValue();
                out.println("ID: " + entry.getKey() + " | " + info.getFilename() + 
                          " (" + formatFileSize(info.getFileSize()) + ") from " + info.getSender());
            }
            out.println("Use !download <fileId> to download");
            out.println("========================");
        }
        
        public void sendMessage(String message) {
            out.println(message);
        }
        
        public String getUsername() {
            return username;
        }
    }
    
    static class FileHandler implements Runnable {
        private Socket socket;
        
        public FileHandler(Socket socket) {
            this.socket = socket;
        }
        
        @Override
        public void run() {
            try (
                DataInputStream dis = new DataInputStream(socket.getInputStream());
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream())
            ) {
                // Read file info
                String fileId = dis.readUTF();
                FileInfo fileInfo = getFileInfo(fileId);
                
                if (fileInfo == null) {
                    dos.writeUTF("ERROR:File not found");
                    return;
                }
                
                dos.writeUTF("READY:" + fileInfo.getFilename() + ":" + fileInfo.getFileSize());
                
                // Read file data
                String response = dis.readUTF();
                if (response.equals("START")) {
                    File file = new File("server_files/" + fileId + "_" + fileInfo.getFilename());
                    file.getParentFile().mkdirs();
                    
                    FileOutputStream fos = new FileOutputStream(file);
                    byte[] buffer = new byte[4096];
                    long bytesToRead = fileInfo.getFileSize();
                    int bytesRead;
                    
                    while (bytesToRead > 0 && 
                           (bytesRead = dis.read(buffer, 0, (int)Math.min(buffer.length, bytesToRead))) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        bytesToRead -= bytesRead;
                    }
                    
                    fos.close();
                    dos.writeUTF("SUCCESS:File saved as " + file.getName());
                    System.out.println("File " + fileInfo.getFilename() + " saved on server");
                }
            } catch (IOException e) {
                System.err.println("File transfer error: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    static class FileInfo {
        private String filename;
        private long fileSize;
        private String sender;
        private String fileId;
        
        public FileInfo(String filename, long fileSize, String sender, String fileId) {
            this.filename = filename;
            this.fileSize = fileSize;
            this.sender = sender;
            this.fileId = fileId;
        }
        
        public String getFilename() { return filename; }
        public long getFileSize() { return fileSize; }
        public String getSender() { return sender; }
        public String getFileId() { return fileId; }
    }
}