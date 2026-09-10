import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class RespWriter {

        private RespWriter() {
        }

        public static void writeSimpleString(
                        OutputStream output,
                        String value) throws IOException {

                writeRaw(
                                output,
                                "+" + value + "\r\n");
        }

        public static void writeError(
                        OutputStream output,
                        String message) throws IOException {

                writeRaw(
                                output,
                                "-" + message + "\r\n");
        }

        public static void writeInteger(
                        OutputStream output,
                        long value) throws IOException {

                writeRaw(
                                output,
                                ":" + value + "\r\n");
        }

        public static void writeNull(
                        OutputStream output) throws IOException {

                writeRaw(
                                output,
                                "$-1\r\n");
        }

        public static void writeBulkString(
                        OutputStream output,
                        String value) throws IOException {

                writeBulkStringWithoutFlush(
                                output,
                                value);

                output.flush();
        }

        public static void writeArray(
                        OutputStream output,
                        Iterable<String> values) throws IOException {

                int count = 0;

                for (String ignored : values) {
                        count++;
                }

                writeRawWithoutFlush(
                                output,
                                "*" + count + "\r\n");

                for (String value : values) {

                        writeBulkStringWithoutFlush(
                                        output,
                                        value);
                }

                output.flush();
        }

        public static void writeBulkStringWithoutFlush(
                        OutputStream output,
                        String value) throws IOException {

                byte[] data = value.getBytes(
                                StandardCharsets.UTF_8);

                writeRawWithoutFlush(
                                output,
                                "$" + data.length + "\r\n");

                output.write(data);

                output.write('\r');
                output.write('\n');
        }

        private static void writeRaw(
                        OutputStream output,
                        String value) throws IOException {

                writeRawWithoutFlush(
                                output,
                                value);

                output.flush();
        }

        private static void writeRawWithoutFlush(
                        OutputStream output,
                        String value) throws IOException {

                output.write(
                                value.getBytes(
                                                StandardCharsets.UTF_8));
        }
}