import java.io.*;
import java.net.Socket;

public class Client {
    public static boolean ack = true;

    public static void main(String[] args) throws IOException {
        System.out.println("Client started..");
        Socket socket = new Socket("10.42.0.114", 22222);
        // Socket socket = new Socket("localhost", 22222);
        System.out.println("Server Connected..");

        ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
        ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
        BufferedReader userInputReader = new BufferedReader(new InputStreamReader(System.in));


        // Thread for receiving messages from the server
        Thread readThread = new Thread(() -> {
            try {
                // boolean ack = false;

                while (true) {
                    Object fromServer = ois.readObject();
                    System.out.println("\nFrom Server: " + (String) fromServer);

                    if(ack)
                        oos.writeObject("ACK");
                    ack = false;

                    System.out.print("To server: ");
                }
            } catch (Exception e) {
                System.out.println("Disconnected from server.");
            }
        });

        // Thread for sending messages to the server
        Thread writeThread = new Thread(() -> {
            try {
                while (true) {
                    System.out.print("To server: ");
                    String message = userInputReader.readLine();
                    if (message.equalsIgnoreCase("exit")) {
                        socket.close();
                        break;
                    }
                    oos.writeObject(message);
                    ack = true;
                }
            } catch (IOException e) {
                System.out.println("Connection closed.");
            }
        });

        readThread.start();
        writeThread.start();
    }
}