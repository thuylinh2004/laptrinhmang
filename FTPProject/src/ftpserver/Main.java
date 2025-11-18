package ftpserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    // Cấu hình Thread Pool: Cho phép tối đa 20 kết nối đồng thời
    private static final int MAX_THREADS = 20; 

    public static void main(String[] args) {
        int port = 2121;
        UserManager userManager = new UserManager("users.txt");
        String rootPath = "ftp_root";

        // Tạo thư mục gốc nếu chưa có
        java.io.File root = new java.io.File(rootPath);
        if (!root.exists()) root.mkdirs();

        ExecutorService threadPool = Executors.newFixedThreadPool(MAX_THREADS);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[SERVER] FTP Server đang chạy trên cổng " + port);
            System.out.println("[SERVER] Thư mục gốc: " + root.getAbsolutePath());

            while (true) {
                Socket client = serverSocket.accept();
                System.out.println("[CONNECTION] Client kết nối: " + client.getInetAddress());
                
                // Thay vì new Thread(...).start(), ta dùng threadPool
                threadPool.execute(new ClientHandler(client, userManager, rootPath));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }
}