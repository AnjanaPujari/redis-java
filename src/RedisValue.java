public class RedisValue {

    public enum Type {
        STRING,
        LIST,
        SET,
        HASH
    }

    private final Type type;
    private final Object value;

    public RedisValue(
            Type type,
            Object value) {
        this.type = type;
        this.value = value;
    }

    public Type getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }
}