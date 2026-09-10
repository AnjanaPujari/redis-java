import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public interface CommandHandler {

    void execute(
            List<String> command,
            OutputStream output) throws IOException;
}
