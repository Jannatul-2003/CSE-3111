import java.io.*;
import java.net.*;
import java.util.*;
 
public class Server {
 
    // Sample user database (card_no -> PIN, balance)
    static class Account {
        String pin;
        double balance;
 
        Account(String pin, double balance) {
            this.pin = pin;
            this.balance = balance;
        }
    }

    static class Pair {
        String transactionId;
        String response;
        Pair(String transactionId, String response) {
            this.transactionId = transactionId;
            this.response = response;
        }
        
    }
 
    static Map<String, Account> accounts = new HashMap<>();
    static Map<String, Pair> transactionLog = new HashMap<>(); // txnId -> response
    static Set<String> awaitingAck = new HashSet<>();
 
    static {
        accounts.put("12345678", new Account("1234", 5000.0));
        accounts.put("87654321", new Account("4321", 3000.0));
    }
 
    static class ClientHandler extends Thread {
        private final Socket socket;
        private final ObjectInputStream in;
        private final ObjectOutputStream out;
        private boolean authenticated = false;
        private String currentCard = "";
 
        public ClientHandler(Socket socket, ObjectInputStream in, ObjectOutputStream out) {
            this.socket = socket;
            this.in = in;
            this.out = out;
        }
 
        @Override
        public void run() {
            try {
                int number_of_withdraws = 1;
 
                while (true) {
                    String message = (String) in.readObject();
                    System.out.println("Client (" + socket.getPort() + "): " + message);
 
                    if (message.equalsIgnoreCase("exit"))
                        break;
 
                    if (message.startsWith("AUTH:")) {
                        String[] parts = message.split(":");
                        if (parts.length == 3 && accounts.containsKey(parts[1])
                                && accounts.get(parts[1]).pin.equals(parts[2])) {
                            authenticated = true;
                            currentCard = parts[1];
                            out.writeObject("AUTH_OK");
                        } else {
                            out.writeObject("AUTH_FAIL");
                        }
                        String transactionId = UUID.randomUUID().toString();
                        System.out.println("Logged: "+parts[1]+" "+transactionId+" "+message);
                        transactionLog.put(parts[1], new Pair(transactionId, message));

                    } else if (!authenticated && !message.equals("ACK")) {
                        out.writeObject("ERROR: Not authenticated.");
                    } else if (message.startsWith("BALANCE_REQ")) {
                        double balance = accounts.get(currentCard).balance;
                        out.writeObject("BALANCE_RES:" + balance);
                        String transactionId = UUID.randomUUID().toString();
                        System.out.println("Logged: "+currentCard+" "+transactionId+" "+message);
                        transactionLog.put(currentCard, new Pair(transactionId, message));
                    } else if (message.startsWith("WITHDRAW:")) {
                        if(number_of_withdraws <= 0)
                        {
                            out.writeObject("Withdrawal cannot be done more than one time.");
                            continue;
                        }
 
                        String[] parts = message.split(":");
                        if (parts.length == 2) {
                            double amount = Double.parseDouble(parts[1]);
 
                            // First time request
                            Account tempAccount = accounts.get(currentCard);
                            if (tempAccount == null) {
                                out.writeObject("ERROR: Account not found.");
                                continue;
                            }
                            double currentBalance = tempAccount.balance;
                            String response;
 
                            if (amount <= currentBalance) {
                                tempAccount.balance -= amount;
                                response = "WITHDRAW_OK";
                                number_of_withdraws--;
                                String transactionId = UUID.randomUUID().toString();
                                System.out.println("Logged: "+currentCard+" "+transactionId+" "+message);
                                transactionLog.put(currentCard, new Pair(transactionId, message));
                            } else {
                                response = "INSUFFICIENT_FUNDS";
                            }
                            out.writeObject(response);
 
                        }
                    } else if (message.startsWith("ACK")) {

                        System.out.println("Message is received by Client: " + socket.getPort());
                    } else {
                        out.writeObject("ERROR: Unknown command.");
                    }
 
                    out.flush();
                }
            } catch (Exception e) {
                System.out.println("Client disconnected: " + socket.getPort());
            }
        }
    }
 
    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(22222)) {
            System.out.println("Bank server started on port 22222.");
 
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("ATM connected on port " + clientSocket.getPort());
 
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
 
                ClientHandler clientThread = new ClientHandler(clientSocket, in, out);
                clientThread.start();
            }
 
        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }
}
