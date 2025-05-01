import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class EnhancedClient {
    private static final String SERVER_IP = "localhost";
    private static final int SERVER_PORT = 22222;
    private static final String EXIT_COMMAND = "EXIT";
    private static AtomicBoolean clientRunning = new AtomicBoolean(true);

    public static void main(String[] args) {
        Socket socket = null;
        DataOutputStream out = null;
        DataInputStream in = null;

        try {
            System.out.println("Client starting...");

            socket = new Socket(SERVER_IP, SERVER_PORT);
            System.out.println("Connected to server at " + SERVER_IP + ":" + SERVER_PORT);

            out = new DataOutputStream(socket.getOutputStream());
            in = new DataInputStream(socket.getInputStream());

            final DataInputStream finalIn = in;
            final boolean[] isFirstMessage = { true };

            Thread responseHandler = new Thread(() -> {
                try {
                    while (clientRunning.get()) {
                        try {

                            String response = finalIn.readUTF();
                            System.out.println("\nServer response: \n" + response);

                            if ("SERVER_SHUTDOWN".equals(response)) {
                                System.out.println("Server has shut down. Press Enter to exit.");
                                clientRunning.set(false);
                                break;
                            }

                            if (clientRunning.get() && !isFirstMessage[0]) {
                                System.out.print("\nEnter message (or '" + EXIT_COMMAND + "' to quit): ");
                            }

                            isFirstMessage[0] = false;
                        } catch (SocketException e) {
                            if (clientRunning.get()) {
                                System.out.println("Connection to server lost.");
                            }
                            clientRunning.set(false);
                            break;
                        }
                    }
                } catch (IOException e) {
                    if (clientRunning.get()) {
                        System.out.println("Error reading from server: " + e.getMessage());
                    }
                    clientRunning.set(false);
                }
            });
            responseHandler.setDaemon(true);
            responseHandler.start();

            Scanner scanner = new Scanner(System.in);

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {

            }

            System.out.print("\nEnter message (or '" + EXIT_COMMAND + "' to quit): ");

            while (clientRunning.get()) {
                String message = scanner.nextLine();

                if (!clientRunning.get()) {
                    break;
                }

                if (message.equalsIgnoreCase(EXIT_COMMAND)) {
                    try {
                        out.writeUTF(EXIT_COMMAND);
                    } catch (IOException e) {

                        System.out.println("Could not send exit command, connection already closed.");
                    }
                    clientRunning.set(false);
                    break;
                }

                if (!message.trim().isEmpty()) {
                    try {
                        out.writeUTF(message);
                    } catch (IOException e) {
                        System.out.println("Error sending message: " + e.getMessage());
                        System.out.println("Server may have shut down. Exiting.");
                        clientRunning.set(false);
                        break;
                    }
                }
            }

            scanner.close();

        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        } finally {

            try {
                if (in != null)
                    in.close();
                if (out != null)
                    out.close();
                if (socket != null)
                    socket.close();
            } catch (IOException e) {
                System.out.println("Error closing resources: " + e.getMessage());
            }
            System.out.println("Client terminated.");
        }
    }
}