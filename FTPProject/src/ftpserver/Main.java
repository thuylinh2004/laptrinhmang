package ftpserver;

     import java.io.IOException;
     import java.net.ServerSocket;
     import java.net.Socket;

     public class Main {
         public static void main(String[] args) {
             int port = 2121; // Dùng cổng 2121
             UserManager userManager = new UserManager("users.txt");
             String rootPath = "ftp_root";

             try (ServerSocket serverSocket = new ServerSocket(port)) {
                 System.out.println("Server FTP đang chạy trên cổng " + port);
                 while (true) {
                     Socket client = serverSocket.accept();
                     System.out.println("Client kết nối: " + client.getInetAddress());
                     new Thread(new ClientHandler(client, userManager, rootPath)).start();
                 }
             } catch (IOException e) {
                 e.printStackTrace();
             }
         }
     }