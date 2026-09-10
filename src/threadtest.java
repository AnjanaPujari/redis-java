public class threadtest {

    public static void main(String[] args) {

        Thread thread = new Thread(() -> {
            System.out.println("Hello from another thread!");
        });

        thread.run();

        System.out.println("Hello from main thread!");
    }
}