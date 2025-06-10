import java.io.*;
import java.net.*;

public class Server {
    private static final int PORT = 5000;
    private static final int RECEIVE_WINDOW_SIZE = 1024;

    public static void main(String[] args) {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("The server started on port " + PORT);
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Client connected: " + clientSocket.getInetAddress());
                    clientSocket.setReceiveBufferSize(RECEIVE_WINDOW_SIZE);
                    ClientHandler clientHandler = new ClientHandler(clientSocket, RECEIVE_WINDOW_SIZE);
                    clientHandler.start();
                } catch (IOException e) {
                    System.out.println("Error handling client connection: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("Could not listen on port " + PORT);
            System.out.println(e.getMessage());
        } finally {
            if (serverSocket != null && !serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    System.out.println("Error closing server socket: " + e.getMessage());
                }
            }
        }
    }

    private static class ClientHandler extends Thread {
        private Socket clientSocket;
        private int totalBytesReceived = 0;
        private int RECEIVE_WINDOW_SIZE;
        private int lastAckedByte = 0;

        public ClientHandler(Socket socket, int receiveWindowSize) {
            this.clientSocket = socket;
            this.RECEIVE_WINDOW_SIZE = receiveWindowSize;
        }

        @Override
        public void run() {
            FileOutputStream fileOutputStream = null;
            try {
                InputStream inputStream = clientSocket.getInputStream();
                OutputStream outputStream = clientSocket.getOutputStream();
                DataInputStream dataIn = new DataInputStream(inputStream);
                DataOutputStream dataOut = new DataOutputStream(outputStream);
                dataOut.writeUTF("Receive window size: " + RECEIVE_WINDOW_SIZE);
                dataOut.writeUTF("File Name: ");
                dataOut.flush();
                String fileName = dataIn.readUTF();
                System.out.println("Receiving file: " + fileName);
                fileOutputStream = new FileOutputStream(fileName);
                byte[] buffer = new byte[RECEIVE_WINDOW_SIZE];
                int bytesRead;
                int availableWindow = RECEIVE_WINDOW_SIZE;
                int consecutiveEmptyReads = 0;
                final int MAX_EMPTY_READS = 5;
                while (true) {
                    availableWindow = RECEIVE_WINDOW_SIZE - (totalBytesReceived - lastAckedByte);
                    try {
                        int readSize = Math.min(availableWindow, buffer.length);
                        bytesRead = inputStream.read(buffer, 0, readSize);
                        if (bytesRead == -1 || (bytesRead == 0 && ++consecutiveEmptyReads >= MAX_EMPTY_READS)) {
                            System.out.println("End of file detected after " +
                                    (bytesRead == -1 ? "EOF" : consecutiveEmptyReads + " empty reads"));
                            break;
                        }
                        if (bytesRead > 0) {
                            consecutiveEmptyReads = 0;
                            totalBytesReceived += bytesRead;
                            System.out.println("Received " + bytesRead + " bytes, Total: " + totalBytesReceived +
                                    " bytes, Available Window: " + availableWindow);
                            fileOutputStream.write(buffer, 0, bytesRead);
                            fileOutputStream.flush();
                            lastAckedByte = totalBytesReceived;
                            dataOut.writeUTF("ACK for " + lastAckedByte + " bytes");
                            dataOut.flush();
                            System.out.println("Sent ACK for " + lastAckedByte + " bytes");
                        } else {
                            System.out.println("Empty read #" + consecutiveEmptyReads + ", continuing...");
                            Thread.sleep(10);
                        }
                    } catch (SocketTimeoutException e) {
                        System.out.println("Socket timeout, continuing to listen");
                        continue;
                    } catch (InterruptedException e) {
                        System.out.println("Thread sleep interrupted");
                    }
                }
                System.out.println("File '" + fileName + "' successfully saved. Total bytes: " + totalBytesReceived);
                System.out.println("Client disconnected.");
            } catch (IOException e) {
                System.out.println("Error in client handler: " + e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    if (fileOutputStream != null) {
                        fileOutputStream.close();
                    }
                    if (clientSocket != null && !clientSocket.isClosed()) {
                        clientSocket.close();
                    }
                } catch (IOException e) {
                    System.out.println("Error closing resources: " + e.getMessage());
                }
            }
        }
    }
}