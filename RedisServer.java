package My_Redis_server;
import java.io.*;
import java.net.*;
import java.util.HashMap;

public class RedisServer {
    private static ServerSocket serverSocket;
    private static final HashMap<String, String> dataStore = new HashMap<>(); // In-memory store

    public static void main(String[] args) {
        try {
            int port = 8000;
            serverSocket = new ServerSocket(port);
            System.out.println("Redis server is running on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            System.out.println("Server stopped: " + e.getMessage());
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream out = clientSocket.getOutputStream()
        ) {
            while (true) {
                String input = in.readLine();
                if (input == null) break;

                // Parse RESP command
                if (input.startsWith("*")) {
                    int arraySize = Integer.parseInt(input.substring(1));
                    String[] commandParts = new String[arraySize];
                    for (int i = 0; i < arraySize; i++) {
                        in.readLine(); // Read the `$length` line
                        commandParts[i] = in.readLine(); // Read the actual string
                    }
                    processCommand(commandParts, out);
                } else {
                    out.write("-ERR Protocol Error\r\n".getBytes());
                }
                out.flush();
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
        }
    }

    private static void processCommand(String[] commandParts, OutputStream out) throws IOException {
        if (commandParts.length == 0) {
            out.write("-ERR Unknown Command\r\n".getBytes());
            return;
        }

        String command = commandParts[0].toUpperCase();
        switch (command) {
            case "PING":
                out.write("+PONG\r\n".getBytes());
                break;
            case "SET":
                if (commandParts.length == 3) {
                    dataStore.put(commandParts[1], commandParts[2]);
                    out.write("+OK\r\n".getBytes());
                } else {
                    out.write("-ERR Wrong number of arguments for 'SET'\r\n".getBytes());
                }
                break;
            case "GET":
                if (commandParts.length == 2) {
                    String value = dataStore.get(commandParts[1]);
                    if (value != null) {
                        out.write(("$" + value.length() + "\r\n" + value + "\r\n").getBytes());
                    } else {
                        out.write("$-1\r\n".getBytes()); // Null bulk string
                    }
                } else {
                    out.write("-ERR Wrong number of arguments for 'GET'\r\n".getBytes());
                }
                break;
            default:
                out.write(("-ERR Unknown Command '" + command + "'\r\n").getBytes());
        }
    }
}
