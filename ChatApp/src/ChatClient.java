// ChatClient.java
import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ChatClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;
    
    public static void main(String[] args) {
        try (
            Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            Scanner scanner = new Scanner(System.in)
        ) {
            System.out.println("Connected to chat server");
            
            // Start a thread to read messages from server
            Thread readThread = new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = in.readLine()) != null) {
                        System.out.println(serverMessage);
                        System.out.print("> ");
                    }
                } catch (IOException e) {
                    System.out.println("Disconnected from server");
                }
            });
            readThread.start();
            
            // Main thread writes messages to server
            String userInput;
            while (true) {
                System.out.print("> ");
                userInput = scanner.nextLine();
                out.println(userInput);
                
                if (userInput.equalsIgnoreCase("exit")) {
                    break;
                }
            }
            
            readThread.join();
        } catch (IOException | InterruptedException e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }
}