import java.io.*;
import java.net.*;
import java.util.*;

public class Client {
    private static final int SERVER_PORT = 5000;
    private static final int PACKET_SIZE = 1024;
    private static int WINDOW_SIZE;
    private static final int DUPLICATE_ACK_THRESHOLD = 3;

    private static class DataPacket {
        int seqNum;
        byte[] data;

        DataPacket(int seqNum, byte[] data) {
            this.seqNum = seqNum;
            this.data = data;
        }
    }

    private static class EWMA {
        private static double estimatedRTT = 105.0;
        private static double devRTT = 0.0;
        private static double timeoutInterval = estimatedRTT;

        private static final double ALPHA = 0.125;
        private static final double BETA = 0.25;

        private static Map<Integer, Long> sendTimes = new HashMap<>();

        public static void recordSendTime(int seqNum) {
            sendTimes.put(seqNum, System.currentTimeMillis());
        }

        public static void onAckReceived(int ackNum) {
            Long sendTime = sendTimes.remove(ackNum);
            if (sendTime == null)
                return;

            long sampleRTT = System.currentTimeMillis() - sendTime;

            estimatedRTT = (1 - ALPHA) * estimatedRTT + ALPHA * sampleRTT;
            devRTT = (1 - BETA) * devRTT + BETA * Math.abs(sampleRTT - estimatedRTT);
            timeoutInterval = estimatedRTT + 4 * devRTT;

            System.out.printf("ACK %d received. RTT = %dms. EstimatedRTT = %.1fms, DevRTT = %.2fms, Timeout = %.2fms\n",
                    ackNum, sampleRTT, estimatedRTT, devRTT, timeoutInterval);
        }

        public static double getTimeoutInterval() {
            return timeoutInterval;
        }

        public static void onTimeout() {
            timeoutInterval *= 2;
        }
    }

    public static List<DataPacket> divideFileIntoPackets(File file) throws IOException {
        List<DataPacket> packets = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[PACKET_SIZE];
            int bytesRead;
            int seqNum = 1;
            while ((bytesRead = fis.read(buffer)) != -1) {
                packets.add(new DataPacket(seqNum, Arrays.copyOf(buffer, bytesRead)));
                seqNum++;
            }
        }
        return packets;
    }

    private static void sendPacket(DataPacket packet, DataOutputStream out) throws IOException {
        out.writeInt(packet.seqNum);
        out.writeInt(packet.data.length);
        out.write(packet.data);
        out.flush();
        System.out.println("Sending Packet " + packet.seqNum + " with Seq# " + packet.seqNum);
        EWMA.recordSendTime(packet.seqNum);
    }

    public static void main(String[] args) throws Exception {
        String serverIP = "localhost";

        try (Socket socket = new Socket(serverIP, SERVER_PORT)) {
            socket.setSoTimeout(1000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            System.out.println("Connected to server!");

            WINDOW_SIZE = in.readInt();

            String prompt = in.readUTF();
            System.out.println(prompt);

            Scanner scanner = new Scanner(System.in);
            String fileName = scanner.nextLine();
            out.writeUTF(fileName);
            out.flush();

            String response = in.readUTF();
            System.out.println("Server: " + response);
            if (!response.startsWith("File found"))
                return;

            List<DataPacket> packets = divideFileIntoPackets(new File(fileName));
            Map<Integer, DataPacket> buffer = new HashMap<>();
            int base = 1, nextSeqNum = 1, lastAckedSeq = 0;
            int duplicateAckCount = 0;
            int lastDuplicateAck = -1;
            long lastSendTime = System.currentTimeMillis();

            while (lastAckedSeq < packets.size()) {

                while (nextSeqNum <= packets.size() && (nextSeqNum - base) < WINDOW_SIZE) {
                    DataPacket packet = packets.get(nextSeqNum - 1);
                    sendPacket(packet, out);
                    buffer.put(packet.seqNum, packet);
                    nextSeqNum++;
                    lastSendTime = System.currentTimeMillis();
                }

                if (System.currentTimeMillis() - lastSendTime > EWMA.getTimeoutInterval()) {
                    if (buffer.containsKey(base)) {
                        System.out.println("Packet " + base + " lost during transmission");
                        System.out.println("Timeout occurred, retransmitting packet " + base);
                        sendPacket(buffer.get(base), out);
                        EWMA.onTimeout();
                        lastSendTime = System.currentTimeMillis();
                    }
                }

                try {
                    if (in.available() > 0) {
                        int ack = in.readInt();

                        if (ack > lastAckedSeq) {

                            lastAckedSeq = ack;
                            base = ack + 1;
                            duplicateAckCount = 0;
                            lastDuplicateAck = -1;
                            EWMA.onAckReceived(ack);
                            lastSendTime = System.currentTimeMillis();
                        } else if (ack == lastAckedSeq && ack > 0) {

                            if (ack == lastDuplicateAck) {
                                duplicateAckCount++;
                            } else {
                                duplicateAckCount = 1;
                                lastDuplicateAck = ack;
                            }

                            System.out.println("Received Duplicate ACK for Seq " + ack);

                            if (duplicateAckCount == DUPLICATE_ACK_THRESHOLD) {
                                int nextExpectedSeq = ack + 1;
                                if (buffer.containsKey(nextExpectedSeq)) {
                                    System.out.println("Fast Retransmit Triggered for Packet " + nextExpectedSeq);
                                    System.out.println("Resending Packet " + nextExpectedSeq);
                                    sendPacket(buffer.get(nextExpectedSeq), out);
                                    duplicateAckCount = 0;
                                    lastDuplicateAck = -1;
                                }
                            }
                        }
                    }
                } catch (SocketTimeoutException e) {

                }

                Thread.sleep(10);
            }

            System.out.println("ACK " + packets.size() + " received. All packets delivered successfully!");
            scanner.close();
        }
    }
}
