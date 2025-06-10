import java.io.*;
import java.net.*;
import java.util.Scanner;

public class TCPFlowControlClient {
    private static final String SERVER_ADDRESS = "192.168.168.185";
    private static final int SERVER_PORT = 5000;
    public static void main(String[] args) {
        Socket socket = null;
        DataInputStream dataIn = null;
        DataOutputStream dataOut = null;
        int PACKET_SIZE = 0;
        try {
            System.out.println("Connecting to server at " + SERVER_ADDRESS + ":" + SERVER_PORT);
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            System.out.println("Connected to server!");
            dataIn = new DataInputStream(socket.getInputStream());
            dataOut = new DataOutputStream(socket.getOutputStream());
            Scanner scanner = new Scanner(System.in);
            String bufferSize = dataIn.readUTF();
            System.out.println("Server says: " + bufferSize);
            String[] parts = bufferSize.split(":");
            PACKET_SIZE = Integer.parseInt(parts[1].trim());
            String serverRequest = dataIn.readUTF();
            System.out.println("Server says: " + serverRequest);
            String filePath = scanner.nextLine();
            File file = new File(filePath);
            if (!file.exists()) {
                System.out.println("File does not exist!");
                scanner.close();
                return;
            }
            String fileName = file.getName();
            System.out.println("Sending file name: " + fileName);
            dataOut.writeUTF(fileName);
            dataOut.flush();
            FileInputStream fileIn = new FileInputStream(file);
            long fileSize = file.length();
            System.out.println("File size: " + fileSize + " bytes");
            int bytesRead;
            long totalBytesSent = 0;
            long lastByteSent = 0;
            long lastByteAcked = 0;
            int cwnd = PACKET_SIZE;
            byte[] buffer = new byte[PACKET_SIZE];
            System.out.println("Sending file data...");
            while (totalBytesSent < fileSize) {
                cwnd = PACKET_SIZE - (int) (lastByteSent - lastByteAcked);
                cwnd = Math.max(1, Math.min(cwnd, PACKET_SIZE));
                bytesRead = fileIn.read(buffer, 0, cwnd);
                if (bytesRead <= 0)
                    break;
                dataOut.write(buffer, 0, bytesRead);
                dataOut.flush();
                lastByteSent += bytesRead;
                totalBytesSent += bytesRead;
                System.out.println("Packet sent: " + bytesRead + " bytes, CWND: " + cwnd);
                String ack = dataIn.readUTF();
                System.out.println("Received Acknowledgment: " + ack);
                try {
                    String[] ackParts = ack.split(" ");
                    if (ackParts.length >= 3) {
                        lastByteAcked = Long.parseLong(ackParts[2]);
                        System.out.println("Last bytes ACKed: " + lastByteAcked);
                    }
                } catch (Exception e) {
                    System.out.println("Error parsing ACK: " + e.getMessage());
                }
            }
            System.out.println("File sent successfully! Total bytes sent: " + totalBytesSent);
            fileIn.close();
            scanner.close();
        } catch (IOException e) {
            System.out.println("Error in client: " + e.getMessage());
        } finally {
            try {
                if (dataOut != null)
                    dataOut.close();
                if (dataIn != null)
                    dataIn.close();
                if (socket != null && !socket.isClosed())
                    socket.close();
            } catch (IOException e) {
                System.out.println("Error closing resources: " + e.getMessage());
            }
        }
    }
}