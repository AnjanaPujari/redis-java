import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CommandRegistry {

    private final SnapshotManager snapshotManager;
    private final RedisStore store;

    public CommandRegistry(RedisStore store) {
        this.store = store;
        this.snapshotManager = new SnapshotManager("redis.snapshot");
    }

    public void execute(
            List<String> command,
            OutputStream output) throws IOException {

        if (command == null || command.isEmpty()) {
            RespWriter.writeError(output, "ERR empty command");
            return;
        }

        String cmd = command.get(0).toUpperCase();

        switch (cmd) {

            // =========================
            // BASIC COMMANDS
            // =========================

            case "PING":

                if (command.size() == 1) {

                    RespWriter.writeSimpleString(
                            output,
                            "PONG");

                } else if (command.size() == 2) {

                    RespWriter.writeBulkString(
                            output,
                            command.get(1));

                } else {

                    RespWriter.writeError(
                            output,
                            "ERR wrong number of arguments");
                }

                break;

            case "SET": {

                if (command.size() != 3) {
                    RespWriter.writeError(
                            output,
                            "ERR wrong number of arguments");
                    return;
                }

                String key = command.get(1);

                synchronized (store.lock(key)) {

                    store.put(
                            key,
                            new RedisValue(
                                    RedisValue.Type.STRING,
                                    command.get(2)));

                    store.removeExpiration(key);
                }

                RespWriter.writeSimpleString(
                        output,
                        "OK");

                break;
            }

            case "GET": {

                if (command.size() != 2) {
                    RespWriter.writeError(
                            output,
                            "ERR wrong number of arguments");
                    return;
                }

                String key = command.get(1);

                synchronized (store.lock(key)) {

                    if (store.isExpired(key)) {

                        RespWriter.writeNull(output);
                        return;
                    }

                    RedisValue value = store.get(key);

                    if (value == null) {

                        RespWriter.writeNull(output);

                    } else if (value.getType() != RedisValue.Type.STRING) {

                        writeWrongType(output);

                    } else {

                        RespWriter.writeBulkString(
                                output,
                                (String) value.getValue());
                    }
                }

                break;
            }

            case "DEL": {

                if (command.size() < 2) {
                    RespWriter.writeError(
                            output,
                            "ERR wrong number of arguments");
                    return;
                }

                long deleted = 0;

                for (int i = 1; i < command.size(); i++) {

                    String key = command.get(i);

                    synchronized (store.lock(key)) {

                        if (store.isExpired(key)) {
                            continue;
                        }

                        if (store.remove(key) != null) {

                            store.removeExpiration(key);
                            deleted++;
                        }
                    }
                }

                RespWriter.writeInteger(
                        output,
                        deleted);

                break;
            }

            case "EXISTS": {

                if (command.size() < 2) {
                    RespWriter.writeError(
                            output,
                            "ERR wrong number of arguments");
                    return;
                }

                long count = 0;

                for (int i = 1; i < command.size(); i++) {

                    String key = command.get(i);

                    synchronized (store.lock(key)) {

                        if (!store.isExpired(key)
                                && store.containsKey(key)) {

                            count++;
                        }
                    }
                }

                RespWriter.writeInteger(
                        output,
                        count);

                break;
            }

            case "KEYS": {

                if (command.size() != 2
                        || !command.get(1).equals("*")) {

                    RespWriter.writeError(
                            output,
                            "ERR only KEYS * is supported");

                    return;
                }

                List<String> keys = new ArrayList<>();

                for (String key : store.keys()) {

                    synchronized (store.lock(key)) {

                        if (!store.isExpired(key)) {

                            keys.add(key);
                        }
                    }
                }

                RespWriter.writeArray(
                        output,
                        keys);

                break;
            }

            case "MGET": {

                if (command.size() < 2) {
                    RespWriter.writeError(
                            output,
                            "ERR wrong number of arguments");
                    return;
                }

                output.write(
                        ("*"
                                + (command.size() - 1)
                                + "\r\n").getBytes(
                                        StandardCharsets.UTF_8));

                for (int i = 1; i < command.size(); i++) {

                    String key = command.get(i);

                    synchronized (store.lock(key)) {

                        if (store.isExpired(key)) {

                            output.write(
                                    "$-1\r\n".getBytes());

                            continue;
                        }

                        RedisValue value = store.get(key);

                        if (value == null
                                || value.getType() != RedisValue.Type.STRING) {

                            output.write(
                                    "$-1\r\n".getBytes());

                        } else {

                            RespWriter.writeBulkStringWithoutFlush(
                                    output,
                                    (String) value.getValue());
                        }
                    }
                }

                output.flush();

                break;
            }

            // =========================
            // ATOMIC STRING COMMANDS
            // =========================

            case "SETNX": {

                if (command.size() != 3) {
                    RespWriter.writeError(
                            output,
                            "ERR wrong number of arguments");
                    return;
                }

                String key = command.get(1);

                synchronized (store.lock(key)) {

                    store.isExpired(key);

                    RedisValue old = store.putIfAbsent(
                            key,
                            new RedisValue(
                                    RedisValue.Type.STRING,
                                    command.get(2)));

                    if (old == null) {

                        store.removeExpiration(key);

                        RespWriter.writeInteger(
                                output,
                                1);

                    } else {

                        RespWriter.writeInteger(
                                output,
                                0);
                    }
                }

                break;
            }

            case "INCR": {

                if (command.size() != 2) {
                    RespWriter.writeError(
                            output,
                            "ERR wrong number of arguments");
                    return;
                }

                String key = command.get(1);

                synchronized (store.lock(key)) {

                    store.isExpired(key);

                    try {

                        RedisValue old = store.get(key);

                        long number;

                        if (old == null) {

                            number = 1;

                        } else {

                            if (old.getType() != RedisValue.Type.STRING) {

                                throw new IllegalArgumentException(
                                        "WRONGTYPE");
                            }

                            number = Long.parseLong(
                                    (String) old.getValue()) + 1;
                        }

                        RedisValue result = new RedisValue(
                                RedisValue.Type.STRING,
                                String.valueOf(number));

                        store.put(
                                key,
                                result);

                        store.removeExpiration(key);

                        RespWriter.writeInteger(
                                output,
                                number);

                    } catch (NumberFormatException e) {

                        RespWriter.writeError(
                                output,
                                "ERR value is not an integer");

                    } catch (IllegalArgumentException e) {

                        if ("WRONGTYPE".equals(
                                e.getMessage())) {

                            writeWrongType(output);

                        } else {

                            RespWriter.writeError(
                                    output,
                                    "ERR value is not an integer");
                        }
                    }
                }

                break;
            }

            // =========================
            // TTL
            // =========================

            case "EXPIRE": {

                if (command.size() != 3) {
                    RespWriter.writeError(
                            output,
                            "ERR wrong number of arguments");
                    return;
                }

                String key = command.get(1);

                long seconds;

                try {

                    seconds = Long.parseLong(
                            command.get(2));

                } catch (NumberFormatException e) {

                    RespWriter.writeError(
                            output,
                            "ERR invalid expire time");

                    return;
                }

                synchronized (store.lock(key)) {

                    if (store.isExpired(key)
                            || !store.containsKey(key)) {

                        RespWriter.writeInteger(
                                output,
                                0);

                        return;
                    }

                    store.setExpiration(
                            key,
                            System.currentTimeMillis()
                                    + seconds * 1000);

                    RespWriter.writeInteger(
                            output,
                            1);
                }

                break;
            }

            case "TTL": {

                if (command.size() != 2) {
                    RespWriter.writeError(
                            output,
                            "ERR wrong number of arguments");
                    return;
                }

                String key = command.get(1);

                synchronized (store.lock(key)) {

                    if (store.isExpired(key)
                            || !store.containsKey(key)) {

                        RespWriter.writeInteger(
                                output,
                                -2);

                        return;
                    }

                    Long expiry = store.getExpiration(key);

                    if (expiry == null) {

                        RespWriter.writeInteger(
                                output,
                                -1);

                        return;
                    }

                    long remaining = (expiry
                            - System.currentTimeMillis()) / 1000;

                    RespWriter.writeInteger(
                            output,
                            remaining);
                }

                break;
            }

            // =========================
            // LISTS
            // =========================

            case "LPUSH": {

                if (command.size() < 3) {
                    RespWriter.writeError(
                            output,
                            "ERR wrong number of arguments");
                    return;
                }

                String key = command.get(1);

                synchronized (store.lock(key)) {

                    store.isExpired(key);

                    RedisValue value = store.get(key);

                    if (value != null
                            && value.getType() != RedisValue.Type.LIST) {

                        writeWrongType(output);
                        return;
                    }

                    LinkedList<String> list;

                    if (value == null) {

                        list = new LinkedList<>();

                        store.put(
                                key,
                                new RedisValue(
                                        RedisValue.Type.LIST,
                                        list));

                    } else {

                        list = (LinkedList<String>) value.getValue();
                    }

                    synchronized (list) {

                        for (int i = 2; i < command.size(); i++) {

                            list.addFirst(
                                    command.get(i));
                        }

                        RespWriter.writeInteger(
                                output,
                                list.size());
                    }
                }

                break;
            }

            case "RPUSH": {

                if (command.size() < 3) {
                    RespWriter.writeError(
                            output,
                            "ERR wrong number of arguments");
                    return;
                }

                String key = command.get(1);

                synchronized (store.lock(key)) {

                    store.isExpired(key);

                    RedisValue value = store.get(key);

                    if (value != null
                            && value.getType() != RedisValue.Type.LIST) {

                        writeWrongType(output);
                        return;
                    }

                    LinkedList<String> list;

                    if (value == null) {

                        list = new LinkedList<>();

                        store.put(
                                key,
                                new RedisValue(
                                        RedisValue.Type.LIST,
                                        list));

                    } else {

                        list = (LinkedList<String>) value.getValue();
                    }

                    synchronized (list) {

                        for (int i = 2; i < command.size(); i++) {

                            list.addLast(
                                    command.get(i));
                        }

                        RespWriter.writeInteger(
                                output,
                                list.size());
                    }
                }

                break;
            }

            case "LPOP":
            case "RPOP": {

                if (command.size() != 2) {
                    RespWriter.writeError(
                            output,
                            "ERR wrong number of arguments");
                    return;
                }

                String key = command.get(1);

                synchronized (store.lock(key)) {

                    if (store.isExpired(key)) {

                        RespWriter.writeNull(output);
                        return;
                    }

                    RedisValue value = store.get(key);

                    if (value == null) {

                        RespWriter.writeNull(output);
                        return;
                    }

                    if (value.getType() != RedisValue.Type.LIST) {

                        writeWrongType(output);
                        return;
                    }

                    LinkedList<String> list = (LinkedList<String>) value.getValue();

                    synchronized (list) {

                        if (list.isEmpty()) {

                            RespWriter.writeNull(output);
                            return;
                        }

                        String result = cmd.equals("LPOP")
                                ? list.removeFirst()
                                : list.removeLast();

                        if (list.isEmpty()) {

                            store.remove(key);
                            store.removeExpiration(key);
                        }

                        RespWriter.writeBulkString(
                                output,
                                result);
                    }
                }

                break;
            }

            case "LRANGE": {

                if (command.size() != 4) {
                    RespWriter.writeError(
                            output,
                            "ERR wrong number of arguments");
                    return;
                }

                String key = command.get(1);

                int start;
                int stop;

                try {

                    start = Integer.parseInt(
                            command.get(2));

                    stop = Integer.parseInt(
                            command.get(3));

                } catch (Exception e) {

                    RespWriter.writeError(
                            output,
                            "ERR invalid range");

                    return;
                }

                synchronized (store.lock(key)) {

                    if (store.isExpired(key)) {

                        RespWriter.writeArray(
                                output,
                                new ArrayList<>());

                        return;
                    }

                    RedisValue value = store.get(key);

                    if (value == null) {

                        RespWriter.writeArray(
                                output,
                                new ArrayList<>());

                        return;
                    }

                    if (value.getType() != RedisValue.Type.LIST) {

                        writeWrongType(output);
                        return;
                    }

                    LinkedList<String> list = (LinkedList<String>) value.getValue();

                    synchronized (list) {

                        int size = list.size();

                        if (start < 0)
                            start = size + start;

                        if (stop < 0)
                            stop = size + stop;

                        if (start < 0)
                            start = 0;

                        if (stop >= size)
                            stop = size - 1;

                        if (start > stop
                                || start >= size) {

                            RespWriter.writeArray(
                                    output,
                                    new ArrayList<>());

                            return;
                        }

                        RespWriter.writeArray(
                                output,
                                new ArrayList<>(
                                        list.subList(
                                                start,
                                                stop + 1)));
                    }
                }

                break;
            }

            // =========================
            // SETS
            // =========================

            case "SADD": {

                if (command.size() < 3) {
                    RespWriter.writeError(
                            output,
                            "ERR wrong number of arguments");
                    return;
                }

                String key = command.get(1);

                synchronized (store.lock(key)) {

                    store.isExpired(key);

                    RedisValue value = store.get(key);

                    if (value != null
                            && value.getType() != RedisValue.Type.SET) {

                        writeWrongType(output);
                        return;
                    }

                    Set<String> set;

                    if (value == null) {

                        set = ConcurrentHashMap.newKeySet();

                        store.put(
                                key,
                                new RedisValue(
                                        RedisValue.Type.SET,
                                        set));

                    } else {

                        set = (Set<String>) value.getValue();
                    }

                    long added = 0;

                    for (int i = 2; i < command.size(); i++) {

                        if (set.add(
                                command.get(i))) {

                            added++;
                        }
                    }

                    RespWriter.writeInteger(
                            output,
                            added);
                }

                break;
            }

            case "SREM": {

                if (command.size() < 3) {
                    RespWriter.writeError(
                            output,
                            "ERR wrong number of arguments");
                    return;
                }

                String key = command.get(1);

                synchronized (store.lock(key)) {

                    if (store.isExpired(key)) {

                        RespWriter.writeInteger(
                                output,
                                0);

                        return;
                    }

                    RedisValue value = store.get(key);

                    if (value == null) {

                        RespWriter.writeInteger(
                                output,
                                0);

                        return;
                    }

                    if (value.getType() != RedisValue.Type.SET) {

                        writeWrongType(output);
                        return;
                    }

                    Set<String> set = (Set<String>) value.getValue();

                    long removed = 0;

                    for (int i = 2; i < command.size(); i++) {

                        if (set.remove(
                                command.get(i))) {

                            removed++;
                        }
                    }

                    if (set.isEmpty()) {

                        store.remove(key);
                        store.removeExpiration(key);
                    }

                    RespWriter.writeInteger(
                            output,
                            removed);
                }

                break;
            }

            case "SISMEMBER": {

                if (command.size() != 3) {
                    RespWriter.writeError(
                            output,
                            "ERR wrong number of arguments");
                    return;
                }

                String key = command.get(1);

                synchronized (store.lock(key)) {

                    if (store.isExpired(key)) {

                        RespWriter.writeInteger(
                                output,
                                0);

                        return;
                    }

                    RedisValue value = store.get(key);

                    if (value == null) {

                        RespWriter.writeInteger(
                                output,
                                0);

                        return;
                    }

                    if (value.getType() != RedisValue.Type.SET) {

                        writeWrongType(output);
                        return;
                    }

                    Set<String> set = (Set<String>) value.getValue();

                    RespWriter.writeInteger(
                            output,
                            set.contains(
                                    command.get(2)) ? 1 : 0);
                }

                break;
            }

            case "SMEMBERS": {

                if (command.size() != 2) {
                    RespWriter.writeError(
                            output,
                            "ERR wrong number of arguments");
                    return;
                }

                String key = command.get(1);

                synchronized (store.lock(key)) {

                    if (store.isExpired(key)) {

                        RespWriter.writeArray(
                                output,
                                new ArrayList<>());

                        return;
                    }

                    RedisValue value = store.get(key);

                    if (value == null) {

                        RespWriter.writeArray(
                                output,
                                new ArrayList<>());

                        return;
                    }

                    if (value.getType() != RedisValue.Type.SET) {

                        writeWrongType(output);
                        return;
                    }

                    Set<String> set = (Set<String>) value.getValue();

                    RespWriter.writeArray(
                            output,
                            set);
                }

                break;
            }

            // =========================
            // HASHES
            // =========================

            case "HSET": {

                if (command.size() < 4
                        || command.size() % 2 != 0) {

                    RespWriter.writeError(
                            output,
                            "ERR wrong number of arguments");

                    return;
                }

                String key = command.get(1);

                synchronized (store.lock(key)) {

                    store.isExpired(key);

                    RedisValue value = store.get(key);

                    if (value != null
                            && value.getType() != RedisValue.Type.HASH) {

                        writeWrongType(output);
                        return;
                    }

                    ConcurrentHashMap<String, String> hash;

                    if (value == null) {

                        hash = new ConcurrentHashMap<>();

                        store.put(
                                key,
                                new RedisValue(
                                        RedisValue.Type.HASH,
                                        hash));

                    } else {

                        hash = (ConcurrentHashMap<String, String>) value.getValue();
                    }

                    long added = 0;

                    for (int i = 2; i < command.size(); i += 2) {

                        String field = command.get(i);

                        String fieldValue = command.get(i + 1);

                        if (!hash.containsKey(field)) {

                            added++;
                        }

                        hash.put(
                                field,
                                fieldValue);
                    }

                    RespWriter.writeInteger(
                            output,
                            added);
                }

                break;
            }

            case "HGET": {

                if (command.size() != 3) {
                    RespWriter.writeError(
                            output,
                            "ERR wrong number of arguments");
                    return;
                }

                String key = command.get(1);

                synchronized (store.lock(key)) {

                    if (store.isExpired(key)) {

                        RespWriter.writeNull(output);
                        return;
                    }

                    RedisValue value = store.get(key);

                    if (value == null) {

                        RespWriter.writeNull(output);
                        return;
                    }

                    if (value.getType() != RedisValue.Type.HASH) {

                        writeWrongType(output);
                        return;
                    }

                    ConcurrentHashMap<String, String> hash = (ConcurrentHashMap<String, String>) value.getValue();

                    String fieldValue = hash.get(
                            command.get(2));

                    if (fieldValue == null) {

                        RespWriter.writeNull(output);

                    } else {

                        RespWriter.writeBulkString(
                                output,
                                fieldValue);
                    }
                }

                break;
            }

            case "HDEL": {

                if (command.size() < 3) {
                    RespWriter.writeError(
                            output,
                            "ERR wrong number of arguments");
                    return;
                }

                String key = command.get(1);

                synchronized (store.lock(key)) {

                    if (store.isExpired(key)) {

                        RespWriter.writeInteger(
                                output,
                                0);

                        return;
                    }

                    RedisValue value = store.get(key);

                    if (value == null) {

                        RespWriter.writeInteger(
                                output,
                                0);

                        return;
                    }

                    if (value.getType() != RedisValue.Type.HASH) {

                        writeWrongType(output);
                        return;
                    }

                    ConcurrentHashMap<String, String> hash = (ConcurrentHashMap<String, String>) value.getValue();

                    long removed = 0;

                    for (int i = 2; i < command.size(); i++) {

                        if (hash.remove(
                                command.get(i)) != null) {

                            removed++;
                        }
                    }

                    if (hash.isEmpty()) {

                        store.remove(key);
                        store.removeExpiration(key);
                    }

                    RespWriter.writeInteger(
                            output,
                            removed);
                }

                break;
            }

            case "HGETALL": {

                if (command.size() != 2) {
                    RespWriter.writeError(
                            output,
                            "ERR wrong number of arguments");
                    return;
                }

                String key = command.get(1);

                synchronized (store.lock(key)) {

                    if (store.isExpired(key)) {

                        RespWriter.writeArray(
                                output,
                                new ArrayList<>());

                        return;
                    }

                    RedisValue value = store.get(key);

                    if (value == null) {

                        RespWriter.writeArray(
                                output,
                                new ArrayList<>());

                        return;
                    }

                    if (value.getType() != RedisValue.Type.HASH) {

                        writeWrongType(output);
                        return;
                    }

                    ConcurrentHashMap<String, String> hash = (ConcurrentHashMap<String, String>) value.getValue();

                    List<String> result = new ArrayList<>();

                    for (Map.Entry<String, String> entry : hash.entrySet()) {

                        result.add(
                                entry.getKey());

                        result.add(
                                entry.getValue());
                    }

                    RespWriter.writeArray(
                            output,
                            result);
                }

                break;
            }

            // =========================
            // PERSISTENCE
            // =========================

            case "SAVE": {

                if (command.size() != 1) {

                    RespWriter.writeError(
                            output,
                            "ERR wrong number of arguments");

                    return;
                }

                try {

                    snapshotManager.save(
                            store.data(),
                            store.expirationTimes());

                    RespWriter.writeSimpleString(
                            output,
                            "OK");

                } catch (IOException e) {

                    RespWriter.writeError(
                            output,
                            "ERR snapshot failed: "
                                    + e.getMessage());
                }

                break;
            }

            default:

                RespWriter.writeError(
                        output,
                        "ERR unknown command");
        }
    }

    private void writeWrongType(
            OutputStream output) throws IOException {

        RespWriter.writeError(
                output,
                "WRONGTYPE Operation against a key holding the wrong kind of value");
    }

}
