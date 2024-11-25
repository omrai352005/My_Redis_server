package My_Redis_server;
import java.io.*;
import java.net.*;

public class RedisServer {
    public static void main(String[] args) {
        int port = 8000;

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Redis-like server is listening on port " + port);

            while (true) {
                // Accept a connection
                Socket socket = serverSocket.accept();
                System.out.println("New client connected: " + socket.getInetAddress());

                try (BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                     BufferedWriter output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

                    // Read and handle RESP commands
                    String line;
                    while ((line = input.readLine()) != null) {
                        System.out.println("Received: " + line);

                        if (line.equals("*1") && input.readLine().equals("$4") && input.readLine().equals("PING")) {
                            // Respond to PING command
                            output.write("+PONG\r\n");
                            output.flush();
                        } else {
                            // RESP error for unsupported commands
                            output.write("-ERR unknown command\r\n");
                            output.flush();
                        }
                    }
                } catch (IOException ex) {
                    System.out.println("Error communicating with client: " + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
