import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private static final int SERVER_PORT = 5000;
    private static final int RECEIVE_WINDOW_SIZE = 4;
    private static final double PACKET_LOSS_PROBABILITY = 0.1;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(SERVER_PORT)) {
            System.out.println("Server is connected at port no: " + SERVER_PORT);
            System.out.println("Server is connecting");
            System.out.println("Waiting for the client");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client request is accepted at port no: " + clientSocket.getPort());
                System.out.println("Server's Communication Port: " + SERVER_PORT);

                new ClientHandler(clientSocket).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private final Socket socket;
        private int expectedSeqNum = 1;
        private int lastAckedSeq = 0;
        private final Random rand = new Random();

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

                out.writeInt(RECEIVE_WINDOW_SIZE);
                out.flush();

                out.writeUTF("Enter the file name:");
                out.flush();

                String fileName = in.readUTF();
                File outputFile = new File("received_" + fileName);

                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    out.writeUTF("File found, starting transfer...");
                    out.flush();

                    while (true) {
                        try {
                            int seqNum = in.readInt();
                            int dataLength = in.readInt();
                            byte[] data = new byte[dataLength];
                            in.readFully(data);

                            if (rand.nextDouble() < PACKET_LOSS_PROBABILITY) {
                                System.out.println("— Packet " + seqNum + " not received —");
                                continue;
                            }

                            if (seqNum == expectedSeqNum) {
                                fos.write(data);
                                fos.flush();
                                expectedSeqNum++;
                                lastAckedSeq = seqNum;
                                System.out.println("Received Packet " + seqNum + ". Sending ACK " + lastAckedSeq);
                            } else if (seqNum > expectedSeqNum) {

                                System.out.println(
                                        "Received Packet " + seqNum + ". Sending Duplicate ACK " + lastAckedSeq);
                            } else {

                                System.out.println(
                                        "Received Packet " + seqNum + " (retransmitted). Sending ACK " + lastAckedSeq);
                            }

                            out.writeInt(lastAckedSeq);
                            out.flush();

                        } catch (EOFException e) {
                            System.out.println("Client finished sending file.");
                            break;
                        } catch (SocketTimeoutException e) {
                            System.out.println("Timeout waiting for packets.");
                            break;
                        }
                    }
                }

                System.out.println("File saved: " + outputFile.getAbsolutePath());

            } catch (IOException e) {
                System.err.println("Connection error with client: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                    System.out.println("Connection closed.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
