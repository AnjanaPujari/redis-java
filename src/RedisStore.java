import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RedisStore {

    private final ConcurrentHashMap<String, RedisValue> data = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Long> expirationTimes = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();

    // SnapshotManager still needs access to the complete state.
    public ConcurrentHashMap<String, RedisValue> data() {
        return data;
    }

    public ConcurrentHashMap<String, Long> expirationTimes() {
        return expirationTimes;
    }

    // Command logic uses these methods instead of manipulating the maps directly.
    public RedisValue get(String key) {
        return data.get(key);
    }

    public void put(String key, RedisValue value) {
        data.put(key, value);
    }

    public RedisValue putIfAbsent(String key, RedisValue value) {
        return data.putIfAbsent(key, value);
    }

    public RedisValue remove(String key) {
        return data.remove(key);
    }

    public boolean containsKey(String key) {
        return data.containsKey(key);
    }

    public Set<String> keys() {
        return data.keySet();
    }

    public Long getExpiration(String key) {
        return expirationTimes.get(key);
    }

    public void setExpiration(String key, long expirationTime) {
        expirationTimes.put(key, expirationTime);
    }

    public void removeExpiration(String key) {
        expirationTimes.remove(key);
    }

    public boolean removeExpiration(String key, long expirationTime) {
        return expirationTimes.remove(key, expirationTime);
    }

    public Object lock(String key) {
        return keyLocks.computeIfAbsent(key, k -> new Object());
    }

    public boolean isExpired(String key) {
        synchronized (lock(key)) {
            Long expirationTime = expirationTimes.get(key);

            if (expirationTime == null) {
                return false;
            }

            if (System.currentTimeMillis() < expirationTime) {
                return false;
            }

            expirationTimes.remove(key, expirationTime);
            data.remove(key);
            return true;
        }
    }

    public void cleanupExpiredKeys() {
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<String, Long> entry : expirationTimes.entrySet()) {
            String key = entry.getKey();
            Long expirationTime = entry.getValue();

            if (currentTime >= expirationTime) {
                synchronized (lock(key)) {
                    if (expirationTimes.remove(key, expirationTime)) {
                        data.remove(key);
                        System.out.println(
                                "Background cleanup removed: " + key);
                    }
                }
            }
        }
    }

    public void clear() {
        data.clear();
        expirationTimes.clear();
    }
}
