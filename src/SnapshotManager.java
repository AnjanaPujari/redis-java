import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SnapshotManager {

    private static final String MAGIC = "REDISJAVA1";

    private final Path snapshotFile;
    private final Path backupFile;

    public SnapshotManager(String fileName) {
        this.snapshotFile = Paths.get(fileName);
        this.backupFile = Paths.get(fileName + ".bak");
    }

    public void save(
            ConcurrentHashMap<String, RedisValue> data,
            ConcurrentHashMap<String, Long> expirationTimes) throws IOException {

        Path tempFile = Paths.get(snapshotFile + ".tmp");

        try {

            try (DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(
                            Files.newOutputStream(tempFile)))) {

                writeString(out, MAGIC);

                out.writeInt(data.size());

                for (Map.Entry<String, RedisValue> entry : data.entrySet()) {

                    String key = entry.getKey();

                    RedisValue redisValue = entry.getValue();

                    writeString(out, key);

                    out.writeUTF(
                            redisValue.getType().name());

                    long expirationTime = expirationTimes.getOrDefault(
                            key,
                            -1L);

                    out.writeLong(
                            expirationTime);

                    switch (redisValue.getType()) {

                        case STRING:

                            writeString(
                                    out,
                                    (String) redisValue.getValue());

                            break;

                        case LIST:

                            LinkedList<String> list = (LinkedList<String>) redisValue.getValue();

                            synchronized (list) {

                                out.writeInt(
                                        list.size());

                                for (String value : list) {

                                    writeString(
                                            out,
                                            value);
                                }
                            }

                            break;

                        case SET:

                            Set<String> set = (Set<String>) redisValue.getValue();

                            out.writeInt(
                                    set.size());

                            for (String value : set) {

                                writeString(
                                        out,
                                        value);
                            }

                            break;

                        case HASH:

                            ConcurrentHashMap<String, String> hash = (ConcurrentHashMap<String, String>) redisValue
                                    .getValue();

                            out.writeInt(
                                    hash.size());

                            for (Map.Entry<String, String> hashEntry : hash.entrySet()) {

                                writeString(
                                        out,
                                        hashEntry.getKey());

                                writeString(
                                        out,
                                        hashEntry.getValue());
                            }

                            break;
                    }

                    out.flush();
                }
            }

            if (Files.exists(snapshotFile)) {

                Files.copy(
                        snapshotFile,
                        backupFile,
                        StandardCopyOption.REPLACE_EXISTING);

                System.out.println(
                        "Backup snapshot created: "
                                + backupFile);
            }

            try {

                Files.move(
                        tempFile,
                        snapshotFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);

            } catch (AtomicMoveNotSupportedException e) {

                Files.move(
                        tempFile,
                        snapshotFile,
                        StandardCopyOption.REPLACE_EXISTING);
            }

            System.out.println(
                    "Snapshot saved: "
                            + snapshotFile);

        } catch (IOException e) {

            try {

                Files.deleteIfExists(
                        tempFile);

            } catch (IOException cleanupError) {

                System.out.println(
                        "Could not remove temporary snapshot: "
                                + cleanupError.getMessage());
            }

            throw e;
        }
    }

    public void load(
            ConcurrentHashMap<String, RedisValue> data,
            ConcurrentHashMap<String, Long> expirationTimes) throws IOException {

        if (!Files.exists(snapshotFile)) {

            throw new IOException(
                    "Snapshot file does not exist.");
        }

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(
                        Files.newInputStream(
                                snapshotFile)))) {

            String magic = readString(in);

            if (!MAGIC.equals(magic)) {

                throw new IOException(
                        "Invalid snapshot header.");
            }

            int numberOfKeys = in.readInt();

            if (numberOfKeys < 0) {

                throw new IOException(
                        "Invalid key count.");
            }

            long fileSize = Files.size(snapshotFile);

            if (numberOfKeys > fileSize) {

                throw new IOException(
                        "Invalid snapshot structure.");
            }

            ConcurrentHashMap<String, RedisValue> loadedData = new ConcurrentHashMap<>();

            ConcurrentHashMap<String, Long> loadedExpirationTimes = new ConcurrentHashMap<>();

            long now = System.currentTimeMillis();

            for (int i = 0; i < numberOfKeys; i++) {

                String key = readString(in);

                if (key == null || key.isEmpty()) {

                    throw new IOException(
                            "Invalid key in snapshot.");
                }

                String typeName = in.readUTF();

                RedisValue.Type type;

                try {

                    type = RedisValue.Type.valueOf(
                            typeName);

                } catch (IllegalArgumentException e) {

                    throw new IOException(
                            "Invalid data type: "
                                    + typeName);
                }

                long expirationTime = in.readLong();

                boolean expired = expirationTime != -1
                        && expirationTime <= now;

                RedisValue value = null;

                switch (type) {

                    case STRING:

                        String stringValue = readString(in);

                        if (!expired) {

                            value = new RedisValue(
                                    RedisValue.Type.STRING,
                                    stringValue);
                        }

                        break;

                    case LIST:

                        int listSize = in.readInt();

                        if (listSize < 0) {

                            throw new IOException(
                                    "Invalid list size.");
                        }

                        LinkedList<String> list = new LinkedList<>();

                        for (int j = 0; j < listSize; j++) {

                            list.add(
                                    readString(in));
                        }

                        if (!expired) {

                            value = new RedisValue(
                                    RedisValue.Type.LIST,
                                    list);
                        }

                        break;

                    case SET:

                        int setSize = in.readInt();

                        if (setSize < 0) {

                            throw new IOException(
                                    "Invalid set size.");
                        }

                        Set<String> set = ConcurrentHashMap.newKeySet();

                        for (int j = 0; j < setSize; j++) {

                            set.add(
                                    readString(in));
                        }

                        if (!expired) {

                            value = new RedisValue(
                                    RedisValue.Type.SET,
                                    set);
                        }

                        break;

                    case HASH:

                        int hashSize = in.readInt();

                        if (hashSize < 0) {

                            throw new IOException(
                                    "Invalid hash size.");
                        }

                        ConcurrentHashMap<String, String> hash = new ConcurrentHashMap<>();

                        for (int j = 0; j < hashSize; j++) {

                            String field = readString(in);

                            String fieldValue = readString(in);

                            hash.put(
                                    field,
                                    fieldValue);
                        }

                        if (!expired) {

                            value = new RedisValue(
                                    RedisValue.Type.HASH,
                                    hash);
                        }

                        break;
                }

                if (!expired && value != null) {

                    loadedData.put(
                            key,
                            value);

                    if (expirationTime != -1) {

                        loadedExpirationTimes.put(
                                key,
                                expirationTime);
                    }
                }
            }

            if (in.read() != -1) {

                throw new IOException(
                        "Unexpected extra data in snapshot.");
            }

            data.clear();

            expirationTimes.clear();

            data.putAll(
                    loadedData);

            expirationTimes.putAll(
                    loadedExpirationTimes);
        }

        System.out.println(
                "Snapshot loaded: "
                        + snapshotFile);
    }

    private static void writeString(
            DataOutputStream out,
            String value) throws IOException {

        byte[] bytes = value.getBytes(
                StandardCharsets.UTF_8);

        out.writeInt(
                bytes.length);

        out.write(bytes);
    }

    private static String readString(
            DataInputStream in) throws IOException {

        int length = in.readInt();

        if (length < 0) {

            throw new IOException(
                    "Invalid string length.");
        }

        byte[] bytes = new byte[length];

        in.readFully(bytes);

        return new String(
                bytes,
                StandardCharsets.UTF_8);
    }
}