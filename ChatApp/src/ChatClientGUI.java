// ChatClientGUI.java
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;

public class ChatClientGUI {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private JFrame frame;
    private JTextArea chatArea;
    private JTextField messageField;
    private String username;
    
    public ChatClientGUI() {
        initializeGUI();
    }
    
    private void initializeGUI() {
        frame = new JFrame("Chat Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 400);
        
        // Chat area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);
        
        // Input area
        messageField = new JTextField();
        messageField.addActionListener(e -> sendMessage());
        
        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendMessage());
        
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        
        frame.setLayout(new BorderLayout());
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(inputPanel, BorderLayout.SOUTH);
        
        // Get username
        username = JOptionPane.showInputDialog(frame, "Enter your username:", 
                                              "Username", JOptionPane.PLAIN_MESSAGE);
        if (username == null || username.trim().isEmpty()) {
            username = "User" + (int)(Math.random() * 1000);
        }
        
        frame.setTitle("Chat Client - " + username);
        frame.setVisible(true);
        
        connectToServer();
    }
    
    private void connectToServer() {
        try {
            socket = new Socket("localhost", 12345);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            
            // Send username
            out.println(username);
            
            // Start thread to read messages
            new Thread(this::readMessages).start();
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Cannot connect to server: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private void readMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                String finalMessage = message;
                SwingUtilities.invokeLater(() -> {
                    chatArea.append(finalMessage + "\n");
                });
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                chatArea.append("Disconnected from server\n");
            });
        }
    }
    
    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            out.println(message);
            messageField.setText("");
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClientGUI::new);
    }
}