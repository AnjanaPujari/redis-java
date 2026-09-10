import java.io.IOException;

public class Main {

        public static void main(String[] args) {

                RedisStore store = new RedisStore();
                SnapshotManager snapshotManager = new SnapshotManager("redis.snapshot");

                try {
                        snapshotManager.load(
                                        store.data(),
                                        store.expirationTimes());

                } catch (IOException e) {

                        System.out.println(
                                        "Primary snapshot failed: " + e.getMessage());

                        System.out.println(
                                        "Trying backup snapshot...");

                        try {
                                SnapshotManager backupManager = new SnapshotManager("redis.snapshot.bak");

                                backupManager.load(
                                                store.data(),
                                                store.expirationTimes());

                                System.out.println(
                                                "Recovery successful using backup snapshot.");

                        } catch (IOException backupError) {

                                System.out.println(
                                                "Backup recovery failed: "
                                                                + backupError.getMessage());

                                System.out.println(
                                                "Starting with empty data.");

                                store.clear();
                        }
                }

                CommandRegistry commandRegistry = new CommandRegistry(store);

                startCleanupThread(store);

                RedisServer server = new RedisServer(6379, commandRegistry);

                Runtime.getRuntime().addShutdownHook(
                                new Thread(() -> {

                                        System.out.println(
                                                        "Server shutting down...");

                                        try {
                                                snapshotManager.save(
                                                                store.data(),
                                                                store.expirationTimes());

                                                System.out.println(
                                                                "Final snapshot saved.");

                                        } catch (IOException e) {

                                                System.out.println(
                                                                "Could not save final snapshot: "
                                                                                + e.getMessage());
                                        }

                                        server.stop();
                                }));

                try {
                        server.start();

                } catch (IOException e) {

                        System.out.println(
                                        "Server error: " + e.getMessage());
                }
        }

        private static void startCleanupThread(
                        RedisStore store) {

                Thread cleanupThread = new Thread(() -> {

                        while (true) {

                                try {
                                        store.cleanupExpiredKeys();
                                        Thread.sleep(1000);

                                } catch (InterruptedException e) {

                                        System.out.println(
                                                        "Cleanup thread interrupted.");

                                        Thread.currentThread().interrupt();
                                        break;
                                }
                        }
                });

                cleanupThread.setDaemon(true);
                cleanupThread.setName("expiration-cleanup");
                cleanupThread.start();
        }
}
