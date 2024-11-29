package My_Redis_server;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class RedisServer {
    private static ServerSocket serverSocket;
    private static final HashMap<String, String> dataStore = new HashMap<>(); 
    private static final Map<String, Long> expiryMap = new HashMap<>();
    private static String dir;
    private static String dbfilename;

    public RedisServer(String dir, String dbfilename) {
    this.dir = dir;
    this.dbfilename = dbfilename;

    // Load existing RDB file, if present
    loadRDB(dir , dbfilename);
}
private static void loadRDB(String dir, String dbFilename) {
    File rdbFile = new File(dir, dbFilename);
    if (!rdbFile.exists()) {
        System.out.println("RDB file not found. Starting with an empty database.");
        return;
    }

    try (FileInputStream fis = new FileInputStream(rdbFile)) {
        while (fis.available() > 0) {
            String key = readLengthPrefixedString(fis);
            String value = readLengthPrefixedString(fis);
            dataStore.put(key, value); // Populate the in-memory datastore
        }
        System.out.println("RDB file loaded successfully.");
    } catch (IOException e) {
        System.err.println("Error reading RDB file: " + e.getMessage());
    }
}

private static String readLengthPrefixedString(InputStream inputStream) throws IOException {
    int length = inputStream.read(); // Read the length prefix
    if (length == -1) throw new EOFException("Unexpected end of file while reading string length");
    byte[] stringBytes = new byte[length];
    int bytesRead = inputStream.read(stringBytes);
    if (bytesRead != length) {
        throw new IOException("Failed to read the expected number of bytes for string");
    }
    return new String(stringBytes);
}




private static void saveRDB() {
    File rdbFile = new File(dir, dbfilename);
    try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(rdbFile))) {
        // Write the number of keys
        dos.writeInt(dataStore.size());
        for (Map.Entry<String, String> entry : dataStore.entrySet()) {
            // Write the key and value
            writeString(dos, entry.getKey());
            writeString(dos, entry.getValue());
        }
        System.out.println("RDB file saved successfully.");
    } catch (IOException e) {
        System.err.println("Error saving RDB file: " + e.getMessage());
    }
}
private static void writeString(DataOutputStream dos, String str) throws IOException {
    dos.writeInt(str.length());
    dos.writeBytes(str);
}

private static String readString(DataInputStream dis) throws IOException {
    int length = dis.readInt();
    byte[] bytes = new byte[length];
    dis.readFully(bytes);
    return new String(bytes);
}


public static void main(String[] args) {
    String dir = "E:/myRedis-data"; // Default directory
    String dbfilename = "dumb.rdb";  // Default RDB filename
    int port = 8000; 
    // Parse command-line arguments
    for (int i = 0; i < args.length; i++) {
        if (args[i].equals("--dir") && i + 1 < args.length) {
            dir = args[i + 1];
        } else if (args[i].equals("--dbfilename") && i + 1 < args.length) {
            dbfilename = args[i + 1];
        }
        else if(args[i].equals("--port")&& i+1<args.length){
            port = Integer.parseInt(args[i+1]); 
        }
    }

    System.out.println("Starting Redis server with dir: " + dir + ", dbfilename: " + dbfilename);

    try {
        
        serverSocket = new ServerSocket(port);
        System.out.println("Redis server is running on port " + port);

        RedisServer server = new RedisServer(dir, dbfilename);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("New client connected: " + clientSocket.getInetAddress());
            new Thread(() -> server.handleClient(clientSocket)).start();
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
            case "INFO":
                out.write("+role: master\r\n".getBytes());
                break;
            case "ECHO":
                if (commandParts.length == 2) {
                    String message = commandParts[1];
                    out.write(("+" + message + "\r\n").getBytes());
                } else {
                    out.write("-ERR Wrong number of arguments for 'ECHO'\r\n".getBytes());
                }
                break;
            
            case "SET":
                if (commandParts.length >= 3) {
                    String key = commandParts[1];
                    String value = commandParts[2];
                    dataStore.put(key, value); // Store the key-value pair
            
                    // Check for optional PX argument
                    if (commandParts.length == 5 && commandParts[3].equalsIgnoreCase("PX")) {
                        try {
                            long expiryMillis = Long.parseLong(commandParts[4]);
                            expiryMap.put(key, System.currentTimeMillis() + expiryMillis);
                        } catch (NumberFormatException e) {
                            out.write("-ERR PX argument must be a number\r\n".getBytes());
                            break;
                        }
                    }
                    else if (commandParts.length == 5 && commandParts[3].equalsIgnoreCase("EX")) {
                        try {
                            long expiryMillis = Long.parseLong(commandParts[4])*1000;
                            expiryMap.put(key, System.currentTimeMillis() + expiryMillis);
                        } catch (NumberFormatException e) {
                            out.write("-ERR EX argument must be a number\r\n".getBytes());
                            break;
                        }
                    }
            
                    out.write("+OK\r\n".getBytes()); // RESP Simple String for success
                } else {
                    out.write("-ERR Wrong number of arguments for 'SET'\r\n".getBytes());
                }
                break;
            
            case "GET":
                if (commandParts.length == 2) {
                    String key = commandParts[1];
            
                    // Check for key expiry
                    if (expiryMap.containsKey(key) && System.currentTimeMillis() > expiryMap.get(key)) {
                        dataStore.remove(key); // Remove expired key
                        expiryMap.remove(key);
                    }
            
                    if (dataStore.containsKey(key)) {
                        String value = dataStore.get(key);
                        out.write(("$" + value.length() + "\r\n" + value + "\r\n").getBytes()); // RESP Bulk String
                    } else {
                        out.write("$-1\r\n".getBytes()); // Null Bulk String
                    }
                } else {
                    out.write("-ERR Wrong number of arguments for 'GET'\r\n".getBytes());
                }
                break;
            case "DEL":
                if(commandParts.length== 2){
                  String key = commandParts[1];
                  if(dataStore.containsKey(key)){
                    dataStore.remove(key);
                    }
                  out.write("+OK\r\n".getBytes());
                }
                else {
                    out.write("-ERR Wrong number of arguments for 'DEL'\r\n".getBytes());
                }
                break;
            case "CONFIG":
                if (commandParts.length == 3 && commandParts[1].equalsIgnoreCase("GET")) {
                    String param = commandParts[2];
                    if (param.equals("dir")) {
                        out.write(("*2\r\n$3\r\ndir\r\n$" + dir.length() + "\r\n" + dir + "\r\n").getBytes());
                    } else if (param.equals("dbfilename")) {
                        out.write(("*2\r\n$10\r\ndbfilename\r\n$" + dbfilename.length() + "\r\n" + dbfilename + "\r\n").getBytes());
                    } else {
                        out.write("*0\r\n".getBytes()); // Empty array for unknown parameters
                    }
                } else {
                    out.write("-ERR Syntax error in CONFIG command\r\n".getBytes());
                }
                break;
            case"SAVE":
                saveRDB();
                out.write("+OK\r\n".getBytes());
                break;
            
            case "KEYS":
                if (commandParts.length == 2 && commandParts[1].equals("*")) {
                    if (dataStore.isEmpty()) {
                        out.write("*0\r\n".getBytes()); // Empty array
                    } else {
                        StringBuilder response = new StringBuilder();
                        response.append("*").append(dataStore.size()).append("\r\n"); 
                        for (String key : dataStore.keySet()) {
                            response.append("$").append(key.length()).append("\r\n").append(key).append("\r\n");
                        }
                        out.write(response.toString().getBytes());
                    }
                } else {
                    out.write("-ERR Wrong number of arguments or invalid pattern for 'KEYS'\r\n".getBytes());
                }
                break;
                
            
            
            
            
            default:
                out.write(("-ERR Unknown Command '" + command + "'\r\n").getBytes());
        }
    }
}
