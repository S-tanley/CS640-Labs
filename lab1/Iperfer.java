import java.io.*;
import java.net.*;

/**
 * Iperfer - Network Throughput Measurement Tool
 * 
 * This is a simplified version of the iperf tool for measuring network bandwidth.
 * Supports two modes:
 * - Client mode (-c): Sends data to the server and measures throughput
 * - Server mode (-s): Receives data from client and measures throughput
 * 
 * Usage:
 *   Client: java Iperfer -c -h <server_hostname> -p <server_port> -t <time>
 *   Server: java Iperfer -s -p <listen_port>
 */
public class Iperfer {

    // Constant: chunk size for sending/receiving data is 1000 bytes (1 KB)
    private static final int CHUNK_SIZE = 1000;

    /**
     * Main function - program entry point
     * @param args command line arguments
     */
    public static void main(String[] args) {
        // Check if there are enough arguments (at least some arguments required)
        if (args.length < 1) {
            // If no arguments, print error message and exit
            System.out.println("Error: missing or additional arguments");
            System.exit(1);
        }

        // Determine client or server mode based on the first argument
        if (args[0].equals("-c")) {
            // Client mode requires 7 arguments: -c -h <host> -p <port> -t <time>
            runClient(args);
        } else if (args[0].equals("-s")) {
            // Server mode requires 3 arguments: -s -p <port>
            runServer(args);
        } else {
            // If first argument is neither -c nor -s, argument error
            System.out.println("Error: missing or additional arguments");
            System.exit(1);
        }
    }

    /**
     * Run client mode
     * Client connects to server, sends data continuously for specified time,
     * then calculates throughput
     * 
     * @param args command line arguments array
     */
    private static void runClient(String[] args) {
        // Validate that client mode has exactly 7 arguments
        // Format: -c -h <hostname> -p <port> -t <time>
        if (args.length != 7) {
            System.out.println("Error: missing or additional arguments");
            System.exit(1);
        }

        // Initialize variables to store parsed arguments
        String hostname = null;  // Server hostname
        int port = -1;           // Server port number
        int time = -1;           // Duration to send data (seconds)

        // Parse command line arguments
        // Loop through arguments, processing each pair (flag + value)
        for (int i = 1; i < args.length; i += 2) {
            // Check if there are enough arguments (flag must be followed by value)
            if (i + 1 >= args.length) {
                System.out.println("Error: missing or additional arguments");
                System.exit(1);
            }

            // Parse value based on flag type
            switch (args[i]) {
                case "-h":
                    // -h is followed by server hostname
                    hostname = args[i + 1];
                    break;
                case "-p":
                    // -p is followed by port number
                    try {
                        port = Integer.parseInt(args[i + 1]);
                    } catch (NumberFormatException e) {
                        // If port is not a valid integer, error and exit
                        System.out.println("Error: missing or additional arguments");
                        System.exit(1);
                    }
                    break;
                case "-t":
                    // -t is followed by time (seconds)
                    try {
                        time = Integer.parseInt(args[i + 1]);
                    } catch (NumberFormatException e) {
                        // If time is not a valid integer, error and exit
                        System.out.println("Error: missing or additional arguments");
                        System.exit(1);
                    }
                    break;
                default:
                    // Unknown flag encountered, error and exit
                    System.out.println("Error: missing or additional arguments");
                    System.exit(1);
            }
        }

        // Validate that all required arguments are provided
        if (hostname == null || port == -1 || time == -1) {
            System.out.println("Error: missing or additional arguments");
            System.exit(1);
        }

        // Validate port number range (valid range is 1024-65535)
        // Ports 0-1023 are reserved system ports requiring admin privileges
        if (port < 1024 || port > 65535) {
            System.out.println("Error: port number must be in the range 1024 to 65535");
            System.exit(1);
        }

        // Validate that time is positive
        if (time <= 0) {
            System.out.println("Error: time must be positive");
            System.exit(1);
        }

        // Create data buffer for sending, filled with zeros
        byte[] data = new byte[CHUNK_SIZE];

        // Variable to track total bytes sent
        long bytesSent = 0;

        // Use try-with-resources to automatically manage Socket and output stream resources
        try (
            // Create Socket connection to specified server and port
            Socket socket = new Socket(hostname, port);
            // Get output stream from Socket for sending data
            OutputStream out = socket.getOutputStream()
        ) {
            // Record start time (nanosecond precision)
            long startTime = System.nanoTime();
            // Calculate end time (convert seconds to nanoseconds)
            long endTime = startTime + (long) time * 1_000_000_000L;

            // Continue sending data until specified time is reached
            while (System.nanoTime() < endTime) {
                // Write data to output stream (send to server)
                out.write(data);
                // Accumulate bytes sent
                bytesSent += CHUNK_SIZE;
            }

            // Flush output stream to ensure all data is sent
            out.flush();

            // Record actual end time
            long actualEndTime = System.nanoTime();
            // Calculate actual elapsed time (seconds)
            double elapsedTime = (actualEndTime - startTime) / 1_000_000_000.0;

            // Calculate amount of data sent (in KB)
            // 1 KB = 1000 bytes (network standard)
            double sentKB = bytesSent / 1000.0;

            // Calculate throughput (Mbps = Megabits per second)
            // 1 byte = 8 bits
            // 1 Mb = 1,000,000 bits
            double rate = (bytesSent * 8.0) / (elapsedTime * 1_000_000.0);

            // Output result: amount of data sent and throughput
            System.out.printf("sent=%d KB rate=%.3f Mbps%n", (long) sentKB, rate);

        } catch (UnknownHostException e) {
            // If hostname cannot be resolved, print error and exit
            System.out.println("Error: could not resolve hostname");
            System.exit(1);
        } catch (IOException e) {
            // If I/O error occurs (e.g., connection failed), print error and exit
            System.out.println("Error: could not connect to server");
            System.exit(1);
        }
    }

    /**
     * Run server mode
     * Server listens on specified port, receives client data,
     * then calculates throughput
     * 
     * @param args command line arguments array
     */
    private static void runServer(String[] args) {
        // Validate that server mode has exactly 3 arguments
        // Format: -s -p <port>
        if (args.length != 3) {
            System.out.println("Error: missing or additional arguments");
            System.exit(1);
        }

        // Validate that second argument is -p (port flag)
        if (!args[1].equals("-p")) {
            System.out.println("Error: missing or additional arguments");
            System.exit(1);
        }

        // Parse port number
        int port;
        try {
            port = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            // If port is not a valid integer, error and exit
            System.out.println("Error: missing or additional arguments");
            System.exit(1);
            return;  // Prevent compiler warning
        }

        // Validate port number range
        if (port < 1024 || port > 65535) {
            System.out.println("Error: port number must be in the range 1024 to 65535");
            System.exit(1);
        }

        // Variable to track total bytes received
        long bytesReceived = 0;

        // Use try-with-resources to manage ServerSocket resource
        try (
            // Create ServerSocket to listen on specified port
            ServerSocket serverSocket = new ServerSocket(port)
        ) {
            // Accept a client connection (blocks until client connects)
            // accept() method blocks until a client connects
            Socket clientSocket = serverSocket.accept();

            // Use try-with-resources to manage client Socket and input stream
            try (
                // Get input stream for receiving data
                InputStream in = clientSocket.getInputStream()
            ) {
                // Create buffer for receiving data
                byte[] buffer = new byte[CHUNK_SIZE];
                // Variable to store bytes read each time
                int bytesRead;

                // Record start time of data reception
                long startTime = System.nanoTime();

                // Loop reading data until connection closes (read returns -1)
                while ((bytesRead = in.read(buffer)) != -1) {
                    // Accumulate bytes received
                    bytesReceived += bytesRead;
                }

                // Record end time
                long endTime = System.nanoTime();
                // Calculate elapsed time (seconds)
                double elapsedTime = (endTime - startTime) / 1_000_000_000.0;

                // Calculate amount of data received (KB)
                double receivedKB = bytesReceived / 1000.0;

                // Calculate throughput (Mbps)
                double rate = (bytesReceived * 8.0) / (elapsedTime * 1_000_000.0);

                // Output result: amount of data received and throughput
                System.out.printf("received=%d KB rate=%.3f Mbps%n", (long) receivedKB, rate);
            }

            // Close client connection
            clientSocket.close();

        } catch (IOException e) {
            // If I/O error occurs, print error and exit
            System.out.println("Error: could not start server");
            System.exit(1);
        }
    }
}
