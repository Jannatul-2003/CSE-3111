
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class server {

    public static boolean isPrime(int number) {
        if (number <= 1) {
            return false;
        }
        for (int i = 2; i <= Math.sqrt(number); i++) {
            if (number % i == 0) {
                return false;
            }
        }
        return true;
    }
    public static boolean isPalindrome(int number) {
        String strNum = String.valueOf(number);
        String reversedStrNum = new StringBuilder(strNum).reverse().toString();
        return strNum.equals(reversedStrNum);
    }
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(22222);
        System.out.println("Server Started..");
        Socket socket = serverSocket.accept();
        System.out.println("Client connected..");
        ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
        ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String msg = "";
        while (true) {
            try {
                // //read from client...
                Object cMsg = ois.readObject();
                System.out.println("From Client: " + (String) cMsg);

                String serverMsg = (String) cMsg;
                serverMsg = serverMsg.toLowerCase();
                if(serverMsg.contains("prime")){
                    String[] parts = serverMsg.split(" ");
                    int number = Integer.parseInt(parts[0]);
                    
                    if (isPrime(number)) {
                        oos.writeObject("The number " + number + " is prime.");
                    } else {
                        oos.writeObject("The number " + number + " is not prime.");
                    }
                }
                else if(serverMsg.contains("palindrome")){
                    String[] parts = serverMsg.split(" ");
                    int number = Integer.parseInt(parts[0]);

                    if (isPalindrome(number)) {
                        oos.writeObject("The number " + number + " is a palindrome.");
                    } else {
                        oos.writeObject("The number " + number + " is not a palindrome.");
                    }
                }
                else{
                String serverMsgToLowerCase = serverMsg.toLowerCase();
                //send to client..
                oos.writeObject(serverMsgToLowerCase);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}