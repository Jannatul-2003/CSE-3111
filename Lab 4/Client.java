import java.io.*;
import java.net.*;
import java.util.*;
 
public class Client {
    // private static final String SERVER_IP = "localhost";
    private static final String SERVER_IP = "10.42.0.114";
    private static final int SERVER_PORT = 22222;
    private static final String EXIT_COMMAND = "EXIT";
    private static final String LIST_FILES_COMMAND = "LIST_FILES";
    private static final String FILE_REQUEST_PREFIX = "FILE:";
    private static final String DOWNLOADS_DIR = "downloads";
    private static final int BUFFER_SIZE = 4096;
 
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private boolean isRunning = true;
 
    public static void main(String[] args) {
        // Create downloads directory if it doesn't exist
        File downloadsDir = new File(DOWNLOADS_DIR);
        if (!downloadsDir.exists()) {
            downloadsDir.mkdir();
            System.out.println("Created directory: " + DOWNLOADS_DIR);
        }
 
        new Client().start();
    }
 
    public void start() {
        try {
            System.out.println("Connecting to server at " + SERVER_IP + ":" + SERVER_PORT + "...");
            socket = new Socket(SERVER_IP, SERVER_PORT);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            System.out.println("Connected to server.");
 
            // Start a thread to listen for server messages
            Thread serverListener = new Thread(this::listenToServer);
            serverListener.setDaemon(true);
            serverListener.start();
 
            // Handle user input
            handleUserInput();
        } catch (IOException e) {
            System.out.println("Error connecting to server: " + e.getMessage());
        } finally {
            disconnect();
        }
    }
 
    private void listenToServer() { // 
        try {
            while (isRunning) {
                String message = in.readUTF();
 
                if (message.equals("SERVER_SHUTDOWN")) {
                    System.out.println("Server is shutting down. Disconnecting...");
                    isRunning = false;
                    break;
                } else if (message.equals("FILE_LIST")) {
                    handleFileListResponse();
                } else if (message.equals("FOUND")) {
                    handleFileDownload();
                } else if (message.equals("NOT_FOUND")) {
                    System.out.println("Requested file not found on server.");
                } else {
                    System.out.println(message);
                }
            }
        } catch (SocketException e) {
            if (isRunning) {
                System.out.println("Connection to server lost: " + e.getMessage());
                isRunning = false;
            }
        } catch (IOException e) {
            if (isRunning) {
                System.out.println("Error receiving message: " + e.getMessage());
                isRunning = false;
            }
        }
    }
    HashMap<Integer, String> fileMap = new HashMap<>();
 
    private void handleFileListResponse() {
        try {
            int fileCount = in.readInt();
 
            if (fileCount == 0) {
                System.out.println("No files available on the server.");
                return;
            }
 
            System.out.println("\nAvailable files on server:");
            System.out.println("-----------------------------");
 
            for (int i = 0; i < fileCount; i++) {
                String fileName = in.readUTF();
                System.out.println((i + 1) + ". " + fileName);
                fileMap.put(i + 1, fileName);
            }
 
            System.out.println("\nTo download a file, type: FILE:<file_name>");
        } catch (IOException e) {
            System.out.println("Error receiving file list: " + e.getMessage());
        }
    }
 
    private void handleFileDownload() {
        try {
            // Get the currently requested filename
            String currentRequest = getCurrentRequestFileName();
 
            // First, receive the file size
            long fileSize = in.readLong();
 
            // Preserve original filename
            String fileName = currentRequest;
 
            File outputFile = new File(DOWNLOADS_DIR + File.separator + fileName);
            System.out.println("Downloading file (" + formatFileSize(fileSize) + ")...");
 
            // Create output file stream
            try (FileOutputStream fileOut = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                long totalReceived = 0;
                long startTime = System.currentTimeMillis();
 
                // Read file data in chunks
                while (totalReceived < fileSize) {
                    bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalReceived));
 
                    if (bytesRead == -1) {
                        break; // End of stream
                    }
 
                    fileOut.write(buffer, 0, bytesRead);
                    totalReceived += bytesRead;
 
                    // Show progress for larger files
                    if (fileSize > 1024 * 1024) {
                        int progress = (int) ((totalReceived * 100) / fileSize);
                        if (progress % 20 == 0 && progress > 0) {
                            System.out.println("Downloaded: " + progress + "% (" +
                                    formatFileSize(totalReceived) + " / " +
                                    formatFileSize(fileSize) + ")");
                        }
                    }
                }
 
                long endTime = System.currentTimeMillis();
                double duration = (endTime - startTime) / 1000.0;
 
                System.out.println("Download complete! File saved to: " + outputFile.getAbsolutePath());
                System.out.println("Time: " + String.format("%.2f", duration) + " seconds, Speed: " +
                        formatFileSize((long) (fileSize / duration)) + "/s");
 
                // Send acknowledgment to server
                out.writeUTF("FILE_RECEIVED");
            }
 
        } catch (IOException e) {
            System.out.println("Error downloading file: " + e.getMessage());
        }
    }
 
    // Track the current file being requested
    private String lastRequest = "";
 
    private String getCurrentRequestFileName() {
        if (lastRequest.startsWith(FILE_REQUEST_PREFIX)) {
            String fileName = lastRequest.substring(FILE_REQUEST_PREFIX.length()).trim();
            int fileIndex = Integer.parseInt(fileName);
            fileName = fileMap.get(fileIndex);
            // Remove size information if present
            if (fileName.contains(" (")) {
                fileName = fileName.substring(0, fileName.indexOf(" ("));
            }
 
            return fileName;
        }
        return "download_" + System.currentTimeMillis() + ".dat";
    }
 
    private void handleUserInput() {
        Scanner scanner = new Scanner(System.in);
 
        System.out.println("Type '" + LIST_FILES_COMMAND + "' to see available files.");
        System.out.println("Type '" + FILE_REQUEST_PREFIX + "<filename>' to download a file.");
        System.out.println("Type '" + EXIT_COMMAND + "' to disconnect.");
 
        try {
            while (isRunning) {
                System.out.print("> ");
                String message = scanner.nextLine();
 
                if (message.equalsIgnoreCase(EXIT_COMMAND)) {
                    out.writeUTF(EXIT_COMMAND);
                    isRunning = false;
                    break;
                } else {
                    // Store the request if it's a file request
                    if (message.toUpperCase().startsWith(FILE_REQUEST_PREFIX)) {
                        lastRequest = message;
                    }
                    out.writeUTF(message);
                }
            }
        } catch (IOException e) {
            if (isRunning) {
                System.out.println("Error sending message: " + e.getMessage());
            }
        }
 
        scanner.close();
    }
 
    private void disconnect() {
        isRunning = false;
 
        try {
            if (in != null)
                in.close();
            if (out != null)
                out.close();
            if (socket != null && !socket.isClosed())
                socket.close();
            System.out.println("Disconnected from server.");
        } catch (IOException e) {
            System.out.println("Error disconnecting: " + e.getMessage());
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
}