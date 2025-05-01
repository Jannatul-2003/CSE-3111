import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

public class FileTransferServer {
    private static final int PORT = 22222;
    private static final String EXIT_COMMAND = "EXIT";
    private static boolean serverRunning = true;
    private static List<ClientHandler> clients = new ArrayList<>();
    private static ServerSocket serverSocket;

    public static void main(String[] args) {
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("File Transfer Server Started on port " + PORT);
            System.out.println("Type '" + EXIT_COMMAND + "' to shut down the server");
            System.out.println("Files will be served from the current directory: " + new File(".").getAbsolutePath());

            startServerMonitor();

            while (serverRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());
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
        } finally {
            shutdown();
        }
    }

    private static void startServerMonitor() {
        Thread monitor = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (serverRunning) {
                String command = scanner.nextLine();
                if (command.equalsIgnoreCase(EXIT_COMMAND)) {
                    System.out.println("Server shutdown initiated...");
                    serverRunning = false;

                    for (ClientHandler client : new ArrayList<>(clients)) {
                        if (client != null) {
                            try {
                                client.closeConnection("SERVER_SHUTDOWN");
                            } catch (Exception e) {
                                System.out.println("Error notifying client: " + e.getMessage());
                            }
                        }
                    }

                    try {
                        if (serverSocket != null && !serverSocket.isClosed()) {
                            serverSocket.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                } else if (command.equalsIgnoreCase("LIST")) {
                    listAvailableFiles();
                } else if (command.equalsIgnoreCase("CLIENTS")) {
                    System.out.println("Connected clients: " + clients.size());
                    for (int i = 0; i < clients.size(); i++) {
                        System.out.println((i + 1) + ". " + clients.get(i).clientAddress);
                    }
                }
            }
            scanner.close();
        });
        monitor.setDaemon(true);
        monitor.start();
    }
    
    private static void listAvailableFiles() {
        File directory = new File(".");  // Current directory
        File[] files = directory.listFiles();
        
        if (files == null || files.length == 0) {
            System.out.println("No files available in the current directory.");
            return;
        }
        
        System.out.println("\nAvailable files:");
        int count = 0;
        for (File file : files) {
            if (file.isFile()) {
                count++;
                System.out.println(count + ". " + file.getName() + " (" + formatFileSize(file.length()) + ")");
            }
        }
        
        if (count == 0) {
            System.out.println("No files found in the current directory.");
        }
    }
    
    private static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    private static void shutdown() {
        System.out.println("Shutting down server...");

        try {
            for (ClientHandler client : new ArrayList<>(clients)) {
                if (client != null) {
                    try {
                        client.closeConnection("SERVER_SHUTDOWN");
                    } catch (Exception e) {
                        System.out.println("Error closing client connection: " + e.getMessage());
                    }
                }
            }

            clients.clear();

            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception e) {
            System.out.println("Error during shutdown: " + e.getMessage());
        }

        System.out.println("Server has been shut down.");
    }

    public static synchronized void removeClient(ClientHandler client) {
        if (client != null && clients.contains(client)) {
            clients.remove(client);
            System.out.println("Client " + client.clientAddress + " disconnected. Current client count: " + clients.size());
        }
    }
    
    private static List<String> getAvailableFiles() {
        List<String> fileList = new ArrayList<>();
        File directory = new File(".");  
        
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    fileList.add(file.getName() + " (" + formatFileSize(file.length()) + ")");
                }
            }
        }
        
        return fileList;
    }

    static class ClientHandler extends Thread {
        private Socket socket;
        private DataOutputStream out;
        private DataInputStream in;
        private boolean isRunning = true;
        private String clientAddress;
        private static final String FILE_REQUEST = "FILE:";
        private static final String LIST_FILES_REQUEST = "LIST_FILES";
        private static final int BUFFER_SIZE = 4096;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.clientAddress = socket.getInetAddress() + ":" + socket.getPort();

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
                out.writeUTF("Welcome to the file transfer server! Type 'EXIT' to disconnect, 'LIST_FILES' to see available files, or 'FILE:<filename>' to download a file.");

                while (isRunning && serverRunning) {
                    try {
                        String message = in.readUTF();

                        if (message.equalsIgnoreCase("EXIT")) {
                            out.writeUTF("Goodbye! Disconnecting your session.");
                            break;
                        } else if (message.equalsIgnoreCase(LIST_FILES_REQUEST)) {
                            handleFileListRequest();
                        } else if (message.startsWith(FILE_REQUEST)) {
                            String fileSelected = message.substring(FILE_REQUEST.length()).trim();
                            int fileNo= Integer.parseInt(fileSelected);
                            if(fileNo < 1 || fileNo > client_fileList.size()){
                                out.writeUTF("Invalid file number. Please try again.");
                                continue;
                            }
                            String fileName = client_fileList.get(fileNo);
                            handleFileRequest(fileName);
                        } else {
                            out.writeUTF("Message received: " + message);
                        }
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
        HashMap<Integer, String> client_fileList = new HashMap<>();
        private void handleFileListRequest() {
            try {
                List<String> fileList = getAvailableFiles();
                System.out.println("Client " + clientAddress + " requested file list. Sending " + fileList.size() + " files.");
                
                // Send the number of files
                out.writeUTF("FILE_LIST");
                out.writeInt(fileList.size());
                int i=1;
                // Send each file name
                for (String fileName : fileList) {
                    out.writeUTF(fileName);
                    client_fileList.put(i, fileName);
                    i++;
                }
                
                System.out.println("File list sent to client " + clientAddress);
            } catch (IOException e) {
                System.out.println("Error sending file list to client: " + e.getMessage());
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

        private void handleFileRequest(String fileName) {
            try {
                System.out.println("Client " + clientAddress + " requested file: " + fileName);
                
                // Extract just the filename without the size information
                if (fileName.contains(" (")) {
                    fileName = fileName.substring(0, fileName.indexOf(" ("));
                }

                File file = new File(fileName);  // File in current directory

                if (file.exists() && file.isFile()) {
                    out.writeUTF("FOUND");
                    long fileSize = file.length();
                    out.writeLong(fileSize);
                    out.flush();

                    try (FileInputStream fileIn = new FileInputStream(file)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;
                        long totalSent = 0;
                        
                        while ((bytesRead = fileIn.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                            totalSent += bytesRead;
                            
                            if (fileSize > 1024 * 1024 && totalSent % (1024 * 1024) == 0) {
                                System.out.println("Sent " + formatFileSize(totalSent) + " of " + 
                                                  formatFileSize(fileSize) + " to client " + clientAddress);
                            }
                        }
                        out.flush();
                    }

                    System.out.println("File '" + fileName + "' (" + formatFileSize(fileSize) + ") sent to client " + clientAddress);

                    try {
                        String ack = in.readUTF();
                        if ("FILE_RECEIVED".equals(ack)) {
                            System.out.println("Client " + clientAddress + " acknowledged file receipt.");
                        }
                    } catch (IOException e) {
                        System.out.println("Client disconnected before acknowledgment: " + e.getMessage());
                        throw e;
                    }
                } else {
                    out.writeUTF("NOT_FOUND");
                    System.out.println("File '" + fileName + "' not found for client " + clientAddress);
                }
            } catch (IOException e) {
                System.out.println("Error handling file request: " + e.getMessage());
            }
        }
    }
}