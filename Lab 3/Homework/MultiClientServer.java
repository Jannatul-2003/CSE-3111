import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
 
public class MultiClientServer {
    private static final int PORT = 22222;
    private static final String TERMINATION_COMMAND = "EXIT_SERVER";
    private static final String EXIT_COMMAND = "EXIT";
    private static final String SEND_COMMAND = "SEND";
    private static boolean serverRunning = true;
    private static List<ClientHandler> clients = new ArrayList<>();
    private static ServerSocket serverSocket;
 
    public static void main(String[] args) {
        try {
 
            serverSocket = new ServerSocket(PORT);
            System.out.println("Server Started on port " + PORT);
            System.out.println("Type '" + TERMINATION_COMMAND + "' or '" + EXIT_COMMAND + "' to shut down the server");
            System.out.println("Type '" + SEND_COMMAND + "' to send a message to clients");
 
 
            startServerMonitor();
 
 
            while (serverRunning) {
                try {
 
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " + clientSocket.getPort());
                    System.out.println("Current client count: " + (clients.size() + 1));
 
 
                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    clients.add(clientHandler);
                    clientHandler.start();
                } catch (IOException e) {
                    if (!serverRunning) {
 
                        break;
                    }
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }
 
 
    private static void startServerMonitor() {
        Thread monitor = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (serverRunning) {
                String command = scanner.nextLine();
                if (command.equalsIgnoreCase(TERMINATION_COMMAND) || command.equalsIgnoreCase(EXIT_COMMAND)) {
                    System.out.println("Server shutdown initiated...");
                    serverRunning = false;
 
 
                    for (ClientHandler client : clients) {
                        client.closeConnection("SERVER_SHUTDOWN");
                    }
 
 
                    try {
                        if (serverSocket != null && !serverSocket.isClosed()) {
                            serverSocket.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
 
                    break;
                } else if (command.equalsIgnoreCase(SEND_COMMAND)) {
                    handleSendCommand(scanner);
                }
            }
            scanner.close();
        });
        monitor.setDaemon(true);
        monitor.start();
    }
 
 
    private static void handleSendCommand(Scanner scanner) {
        if (clients.isEmpty()) {
            System.out.println("No clients connected. Cannot send messages.");
            return;
        }
 
 
        System.out.println("\nConnected clients:");
        for (int i = 0; i < clients.size(); i++) {
            ClientHandler client = clients.get(i);
            System.out.println((i + 1) + ". Client " + client.clientAddress);
        }
 
 
        System.out.print("\nEnter client number (1-" + clients.size() + ") or 0 for all clients: ");
        int clientNumber;
        try {
            clientNumber = Integer.parseInt(scanner.nextLine());
            if (clientNumber < 0 || clientNumber > clients.size()) {
                System.out.println("Invalid client number. Cancelling send operation.");
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Cancelling send operation.");
            return;
        }
 
 
        System.out.print("Enter message to send: ");
        String message = scanner.nextLine();
        if (message.trim().isEmpty()) {
            System.out.println("Message cannot be empty. Cancelling send operation.");
            return;
        }
 
 
        String serverMessage = "[SERVER MESSAGE] " + message;
 
 
        if (clientNumber == 0) {
 
            System.out.println("Sending message to all clients...");
            int successCount = 0;
            for (ClientHandler client : clients) {
                if (client.sendMessage(serverMessage)) {
                    successCount++;
                }
            }
            System.out.println("Message sent to " + successCount + " out of " + clients.size() + " clients.");
        } else {
 
            ClientHandler targetClient = clients.get(clientNumber - 1);
            System.out.println("Sending message to Client " + targetClient.clientAddress + "...");
            if (targetClient.sendMessage(serverMessage)) {
                System.out.println("Message sent successfully.");
            } else {
                System.out.println("Failed to send message to client.");
            }
        }
    }
 
 
    private static void shutdown() {
        System.out.println("Shutting down server...");
 
 
        for (ClientHandler client : clients) {
            client.closeConnection("SERVER_SHUTDOWN");
        }
 
        clients.clear();
 
 
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
 
        System.out.println("Server has been shut down.");
    }
 
 
    public static void removeClient(ClientHandler client) {
        clients.remove(client);
        System.out.println("Client disconnected. Current client count: " + clients.size());
    }
 
 
    static class ClientHandler extends Thread {
        private Socket socket;
        private DataOutputStream out;
        private DataInputStream in;
        private boolean isRunning = true;
        private String clientAddress;
 
        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.clientAddress = String.valueOf(socket.getPort());
 
 
            try {
                this.out = new DataOutputStream(socket.getOutputStream());
                this.in = new DataInputStream(socket.getInputStream());
            } catch (IOException e) {
                System.out.println("Error setting up streams for client: " + e.getMessage());
                isRunning = false;
            }
        }
 
        @Override
        public void run() {
            try {
 
                out.writeUTF("Welcome to the chat server! Type 'EXIT' to disconnect.");
 
 
                while (isRunning && serverRunning) {
                    try {
 
                        String message = in.readUTF();
 
 
                        if (message.equalsIgnoreCase("EXIT")) {
                            out.writeUTF("Goodbye! Disconnecting your session.");
                            break;
                        }
 
 
                        System.out.println("From client " + clientAddress + ": " + message);
 
 
                        String response = processMessage(message);
 
 
                        out.writeUTF(response);
                    } catch (IOException e) {
                        if (serverRunning && isRunning) {
                            System.out.println("Error reading from client: " + e.getMessage());
                        }
                        break;
                    }
                }
            } catch (IOException e) {
                System.out.println("Error in client handler: " + e.getMessage());
            } finally {
                closeConnection(null);
                removeClient(this);
            }
        }
 
 
        private String processMessage(String message) {
 
            String[] sentences = message.split("(?<=[.!?])\\s*");
            StringBuilder responseBuilder = new StringBuilder();
 
            for (String sentence : sentences) {
                sentence = sentence.trim();
                if (!sentence.isEmpty()) {
 
                    String processedSentence = sentence.toLowerCase();
 
 
                    String timestamp = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
                    responseBuilder.append("[").append(timestamp).append("] ")
                                   .append("Processed: \"").append(processedSentence).append("\"\n");
                }
            }
 
            return responseBuilder.toString().trim();
        }
 
 
        public boolean sendMessage(String message) {
            if (!isRunning || out == null) {
                return false;
            }
 
            try {
                out.writeUTF(message);
                return true;
            } catch (IOException e) {
                System.out.println("Error sending message to client " + clientAddress + ": " + e.getMessage());
                return false;
            }
        }
 
 
        public void closeConnection(String finalMessage) {
            isRunning = false;
 
            try {
                if (finalMessage != null && out != null) {
                    out.writeUTF(finalMessage);
                }
 
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                System.out.println("Error closing client connection: " + e.getMessage());
            }
        }
    }
}