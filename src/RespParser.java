import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class RespParser {

    private final InputStream input;

    public RespParser(InputStream input) {
        this.input = input;
    }

    public List<String> readCommand() throws IOException {

        String firstLine = readLine();

        if (firstLine == null) {
            return null;
        }

        if (!firstLine.startsWith("*")) {
            throw new IOException(
                    "Protocol error: expected array");
        }

        int numberOfElements;

        try {
            numberOfElements = Integer.parseInt(
                    firstLine.substring(1));

        } catch (NumberFormatException e) {

            throw new IOException(
                    "Protocol error: invalid array length");
        }

        if (numberOfElements <= 0) {
            throw new IOException(
                    "Protocol error: invalid array length");
        }

        List<String> command = new ArrayList<>(
                numberOfElements);

        for (int i = 0; i < numberOfElements; i++) {

            String lengthLine = readLine();

            if (lengthLine == null ||
                    !lengthLine.startsWith("$")) {

                throw new IOException(
                        "Protocol error: expected bulk string");
            }

            int length;

            try {

                length = Integer.parseInt(
                        lengthLine.substring(1));

            } catch (NumberFormatException e) {

                throw new IOException(
                        "Protocol error: invalid bulk string length");
            }

            if (length < 0) {

                throw new IOException(
                        "Protocol error: null bulk strings are not supported");
            }

            byte[] data = input.readNBytes(length);

            if (data.length != length) {

                throw new IOException(
                        "Protocol error: incomplete data");
            }

            int first = input.read();

            int second = input.read();

            if (first != '\r' ||
                    second != '\n') {

                throw new IOException(
                        "Protocol error: expected CRLF");
            }

            command.add(
                    new String(
                            data,
                            StandardCharsets.UTF_8));
        }

        return command;
    }

    private String readLine()
            throws IOException {

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        while (true) {

            int current = input.read();

            if (current == -1) {

                if (buffer.size() == 0) {
                    return null;
                }

                throw new IOException(
                        "Protocol error: unexpected end");
            }

            if (current == '\r') {

                int next = input.read();

                if (next != '\n') {

                    throw new IOException(
                            "Protocol error: expected LF");
                }

                return buffer.toString(
                        StandardCharsets.UTF_8);
            }

            buffer.write(current);
        }
    }
}