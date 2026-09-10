import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

public class RedisServer {

    private final int port;
    private final CommandRegistry commandRegistry;
    private ServerSocket serverSocket;

    public RedisServer(int port, CommandRegistry commandRegistry) {
        this.port = port;
        this.commandRegistry = commandRegistry;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);

        System.out.println(
                "Redis server started on port " + port + "...");

        try {
            while (!serverSocket.isClosed()) {

                System.out.println(
                        "Waiting for a client...");

                Socket clientSocket = serverSocket.accept();

                System.out.println(
                        "Client connected!");

                Thread clientThread = new Thread(
                        () -> handleClient(clientSocket));

                clientThread.setName(
                        "redis-client-" + clientSocket.getPort());

                clientThread.start();
            }

        } catch (IOException e) {

            if (!serverSocket.isClosed()) {
                throw e;
            }
        }
    }

    public void stop() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.out.println(
                        "Could not stop server: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket clientSocket) {

        try (Socket socket = clientSocket;
                InputStream input = new BufferedInputStream(socket.getInputStream());
                OutputStream output = new BufferedOutputStream(socket.getOutputStream())) {

            RespParser parser = new RespParser(input);

            while (true) {

                List<String> command = parser.readCommand();

                if (command == null) {
                    break;
                }

                System.out.println(
                        "Command received: " + command);

                commandRegistry.execute(
                        command,
                        output);
            }

            System.out.println(
                    "Client disconnected.");

        } catch (Exception e) {

            System.out.println(
                    "Client error: " + e.getMessage());
        }
    }
}
