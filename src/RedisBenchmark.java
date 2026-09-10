import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class RedisBenchmark {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 6379;

    public static void main(String[] args) throws Exception {

        System.out.println("=================================");
        System.out.println(" REDIS JAVA PERFORMANCE BENCHMARK");
        System.out.println("=================================");
        System.out.println();

        benchmarkSet();
        benchmarkGet();
        benchmarkIncr();
        benchmarkConcurrentIncr();

        System.out.println();
        System.out.println("=================================");
        System.out.println("       BENCHMARK COMPLETE");
        System.out.println("=================================");
    }

    private static void benchmarkSet() throws Exception {

        int operations = 1000;

        try (RedisConnection connection = new RedisConnection()) {

            long start = System.nanoTime();

            for (int i = 0; i < operations; i++) {
                connection.sendCommand(
                        "SET",
                        "bench_set_" + i,
                        "value");
            }

            long end = System.nanoTime();

            printResult("SET", operations, start, end);
        }
    }

    private static void benchmarkGet() throws Exception {

        int operations = 1000;

        try (RedisConnection connection = new RedisConnection()) {

            for (int i = 0; i < operations; i++) {
                connection.sendCommand(
                        "SET",
                        "bench_get_" + i,
                        "value");
            }

            long start = System.nanoTime();

            for (int i = 0; i < operations; i++) {
                connection.sendCommand(
                        "GET",
                        "bench_get_" + i);
            }

            long end = System.nanoTime();

            printResult("GET", operations, start, end);
        }
    }

    private static void benchmarkIncr() throws Exception {

        int operations = 1000;

        try (RedisConnection connection = new RedisConnection()) {

            connection.sendCommand(
                    "SET",
                    "bench_counter",
                    "0");

            long start = System.nanoTime();

            for (int i = 0; i < operations; i++) {
                connection.sendCommand(
                        "INCR",
                        "bench_counter");
            }

            long end = System.nanoTime();

            printResult("INCR", operations, start, end);

            String result = connection.sendCommand(
                    "GET",
                    "bench_counter");

            System.out.println(
                    "Final INCR value: "
                            + result.trim());

            System.out.println();
        }
    }

    private static void benchmarkConcurrentIncr()
            throws Exception {

        int clients = 10;
        int operationsPerClient = 100;

        try (RedisConnection setup = new RedisConnection()) {

            setup.sendCommand(
                    "SET",
                    "bench_concurrent",
                    "0");
        }

        Thread[] threads = new Thread[clients];

        long start = System.nanoTime();

        for (int i = 0; i < clients; i++) {

            threads[i] = new Thread(() -> {

                try {

                    try (RedisConnection connection = new RedisConnection()) {

                        for (int j = 0; j < operationsPerClient; j++) {

                            connection.sendCommand(
                                    "INCR",
                                    "bench_concurrent");
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        long end = System.nanoTime();

        int totalOperations = clients * operationsPerClient;

        printResult(
                "CONCURRENT INCR",
                totalOperations,
                start,
                end);

        try (RedisConnection connection = new RedisConnection()) {

            String result = connection.sendCommand(
                    "GET",
                    "bench_concurrent");

            System.out.println(
                    "Expected final value: "
                            + totalOperations);

            System.out.println(
                    "Actual final value: "
                            + result.trim());
        }

        System.out.println();
    }

    private static void printResult(
            String operation,
            int operations,
            long start,
            long end) {

        double milliseconds = (end - start) / 1_000_000.0;

        double operationsPerSecond = operations /
                (milliseconds / 1000.0);

        System.out.println(
                operation
                        + " | Operations: "
                        + operations);

        System.out.printf(
                "Time: %.2f ms%n",
                milliseconds);

        System.out.printf(
                "Throughput: %.2f operations/sec%n",
                operationsPerSecond);

        System.out.println();
    }

    private static class RedisConnection
            implements AutoCloseable {

        private final Socket socket;
        private final InputStream input;
        private final OutputStream output;

        RedisConnection() throws IOException {

            socket = new Socket(HOST, PORT);

            input = socket.getInputStream();

            output = socket.getOutputStream();
        }

        String sendCommand(
                String... parts) throws IOException {

            StringBuilder request = new StringBuilder();

            request.append("*")
                    .append(parts.length)
                    .append("\r\n");

            for (String part : parts) {

                byte[] data = part.getBytes(
                        StandardCharsets.UTF_8);

                request.append("$")
                        .append(data.length)
                        .append("\r\n");

                request.append(part)
                        .append("\r\n");
            }

            output.write(
                    request.toString()
                            .getBytes(
                                    StandardCharsets.UTF_8));

            output.flush();

            return readResponse();
        }

        private String readResponse()
                throws IOException {

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            int first = input.read();

            if (first == -1) {
                return "";
            }

            buffer.write(first);

            if (first == '$') {

                readUntilCRLF(buffer);

                String header = buffer.toString(
                        StandardCharsets.UTF_8);

                int length = Integer.parseInt(
                        header.substring(
                                1,
                                header.length() - 2));

                if (length >= 0) {

                    byte[] data = input.readNBytes(length);

                    buffer.write(data);

                    input.read();
                    input.read();
                }

            } else {

                readUntilCRLF(buffer);
            }

            return buffer.toString(
                    StandardCharsets.UTF_8);
        }

        private void readUntilCRLF(
                ByteArrayOutputStream buffer) throws IOException {

            int previous = -1;

            while (true) {

                int current = input.read();

                if (current == -1) {
                    break;
                }

                buffer.write(current);

                if (previous == '\r' &&
                        current == '\n') {

                    break;
                }

                previous = current;
            }
        }

        @Override
        public void close()
                throws IOException {

            socket.close();
        }
    }
}