public class RedisStoreTest {

    public static void main(String[] args) {

        RedisStore store = new RedisStore();

        // TEST 1: PUT + GET
        store.put(
                "name",
                new RedisValue(
                        RedisValue.Type.STRING,
                        "Anju"));

        RedisValue value = store.get("name");

        if (value == null ||
                !value.getValue().equals("Anju")) {

            throw new RuntimeException(
                    "TEST 1 FAILED: PUT/GET");
        }

        System.out.println(
                "TEST 1 PASSED: PUT/GET");

        // TEST 2: CONTAINS
        if (!store.containsKey("name")) {

            throw new RuntimeException(
                    "TEST 2 FAILED: containsKey");
        }

        System.out.println(
                "TEST 2 PASSED: containsKey");

        // TEST 3: REMOVE
        store.remove("name");

        if (store.containsKey("name")) {

            throw new RuntimeException(
                    "TEST 3 FAILED: remove");
        }

        System.out.println(
                "TEST 3 PASSED: remove");

        // TEST 4: EXPIRATION
        store.put(
                "temporary",
                new RedisValue(
                        RedisValue.Type.STRING,
                        "hello"));

        store.setExpiration(
                "temporary",
                System.currentTimeMillis() + 100);

        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (!store.isExpired("temporary")) {

            throw new RuntimeException(
                    "TEST 4 FAILED: expiration");
        }

        System.out.println(
                "TEST 4 PASSED: expiration");

        // TEST 5: KEY LOCK
        Object lock1 = store.lock("locktest");

        Object lock2 = store.lock("locktest");

        if (lock1 != lock2) {

            throw new RuntimeException(
                    "TEST 5 FAILED: key lock");
        }

        System.out.println(
                "TEST 5 PASSED: key lock");

        System.out.println();
        System.out.println(
                "ALL REDIS STORE TESTS PASSED!");
    }
}