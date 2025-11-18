package ftpserver;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.InetAddress;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final UserManager userManager;
    private BufferedReader reader;
    private PrintWriter writer;
    private DataInputStream dataIn;
    private DataOutputStream dataOut;
    private Path rootDir;
    private Path currentDir;
    private String username;
    private String rnfrName; // lưu tên file nhận từ RNFR
    private ServerSocket dataSocket;
    private Socket dataConnection;
    private boolean isLoggedIn;

    public ClientHandler(Socket clientSocket, UserManager userManager, String rootPath) {
        this.clientSocket = clientSocket;
        this.userManager = userManager;
        this.rootDir = Paths.get(rootPath).toAbsolutePath();
        this.currentDir = rootDir;
        this.isLoggedIn = false;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));
            writer = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"), true);
            dataIn = new DataInputStream(clientSocket.getInputStream());
            dataOut = new DataOutputStream(clientSocket.getOutputStream());

            // Gửi thông báo server sẵn sàng
            writer.println("220 FTP Server sẵn sàng");

            // Xử lý các lệnh FTP
            while (true) {
                String line = reader.readLine();
                if (line == null) break;
                String[] cmdParts = line.split(" ", 2);
                if (cmdParts.length < 1) continue;
                String command = cmdParts[0].toUpperCase();
                String arg = cmdParts.length > 1 ? cmdParts[1] : "";

                switch (command) {
                    case "SYST":
                        handleSyst();
                        break;
                    case "USER":
                        handleUser(arg);
                        break;
                    case "PASS":
                        handlePass(arg);
                        break;
                    case "LIST":
                        if (isLoggedIn) handleList();
                        else writer.println("530 Vui lòng đăng nhập");
                        break;
                    case "NLST":
                        if (isLoggedIn) handleNlst();
                        else writer.println("530 Vui lòng đăng nhập");
                        break;
                    case "STOR":
                        if (isLoggedIn) handleStor(arg);
                        else writer.println("530 Vui lòng đăng nhập");
                        break;
                    case "RETR":
                        if (isLoggedIn) handleRetr(arg);
                        else writer.println("530 Vui lòng đăng nhập");
                        break;
                    case "DELE":
                        if (isLoggedIn) handleDele(arg);
                        else writer.println("530 Vui lòng đăng nhập");
                        break;
                    case "RNFR":
                        if (isLoggedIn) handleRnfr(arg);
                        else writer.println("530 Vui lòng đăng nhập");
                        break;
                    case "RNTO":
                        if (isLoggedIn) handleRnto(arg);
                        else writer.println("530 Vui lòng đăng nhập");
                        break;
                    case "MKD":
                        if (isLoggedIn) handleMkd(arg);
                        else writer.println("530 Vui lòng đăng nhập");
                        break;
                    case "QUIT":
                        writer.println("221 Tạm biệt");
                        clientSocket.close();
                        return;
                    case "PASV":
                        if (isLoggedIn) handlePasv();
                        else writer.println("530 Vui lòng đăng nhập");
                        break;
                    case "REGISTER": // Lệnh tùy chỉnh
                        handleRegister(arg);
                        break;
                    case "PWD":
                        if (isLoggedIn) handlePwd();
                        else writer.println("530 Vui lòng đăng nhập");
                        break;
                    default:
                        writer.println("502 Lệnh không được hỗ trợ");
                }
            }
        } catch (IOException e) {
            System.out.println("[INFO] Client ngắt kết nối: " + clientSocket.getInetAddress());
        } finally {
            closeDataConnection();
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleSyst() {
        writer.println("215 UNIX Type: L8");
    }

    private void handleUser(String username) {
        this.username = username;
        writer.println("331 Vui lòng nhập mật khẩu");
    }

    private void handlePass(String password) {
        try {
            boolean ok = userManager.authenticate(username, password);
            if (ok) {
                isLoggedIn = true;
                writer.println("230 Đăng nhập thành công");
                System.out.println("[INFO] User '" + username + "' đăng nhập thành công");
            } else {
                isLoggedIn = false;
                writer.println("530 Đăng nhập thất bại");
            }
        } catch (Exception e) {
            isLoggedIn = false;
            writer.println("530 Đăng nhập thất bại");
        }
    }

    private void handleRegister(String args) {
        try {
            String[] parts = args.split(" ", 2);
            if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
                writer.println("501 Cú pháp không hợp lệ");
                return;
            }
            boolean ok = userManager.registerUser(parts[0], parts[1]);
            writer.println(ok ? "200 Đăng ký thành công" : "503 Đăng ký thất bại");
        } catch (Exception e) {
            writer.println("503 Đăng ký thất bại");
        }
    }

    private void handlePasv() throws IOException {
        // Tạo socket dữ liệu cho chế độ passive
        closeDataConnection();
        dataSocket = new ServerSocket(0); // Chọn cổng ngẫu nhiên
        int port = dataSocket.getLocalPort();
        String host = clientSocket.getLocalAddress().getHostAddress().replace(".", ",");
        int p1 = port / 256;
        int p2 = port % 256;
        writer.println(String.format("227 Entering Passive Mode (%s,%d,%d)", host, p1, p2));
    }

    private void handleList() {
        try {
            if (!openDataConnection()) {
                writer.println("425 Không thể mở kết nối dữ liệu");
                return;
            }
            writer.println("150 Đang gửi danh sách file");
            try (PrintWriter dataWriter = new PrintWriter(dataConnection.getOutputStream(), true)) {
                File[] files = currentDir.toFile().listFiles();
                if (files != null) {
                    for (File f : files) {
                        String type = f.isDirectory() ? "Thư mục" : "Tệp";
                        String size = f.isFile() ? String.valueOf(f.length()) : "";
                        dataWriter.println(f.getName() + "|" + size + "|" + type);
                    }
                }
            }
            writer.println("226 Truyền danh sách hoàn tất");
        } catch (Exception e) {
            writer.println("550 Lỗi khi liệt kê file");
        } finally {
            closeDataConnection();
        }
    }

    private void handleNlst() {
        try {
            if (!openDataConnection()) {
                writer.println("425 Không thể mở kết nối dữ liệu");
                return;
            }
            writer.println("150 Đang gửi danh sách tên file");
            try (PrintWriter dataWriter = new PrintWriter(dataConnection.getOutputStream(), true)) {
                File[] files = currentDir.toFile().listFiles();
                if (files != null) {
                    for (File f : files) {
                        dataWriter.println(f.getName());
                    }
                }
            }
            writer.println("226 Truyền danh sách tên hoàn tất");
        } catch (Exception e) {
            writer.println("550 Lỗi khi liệt kê tên file");
        } finally {
            closeDataConnection();
        }
    }

    private void handlePwd() {
        // Trả về thư mục hiện tại theo chuẩn 257
        String pwd = currentDir.toAbsolutePath().toString().replace('\\', '/');
        writer.println("257 \"" + pwd + "\" là thư mục hiện tại");
    }

    private void handleStor(String filename) {
        try {
            if (!openDataConnection()) {
                writer.println("425 Không thể mở kết nối dữ liệu");
                return;
            }
            writer.println("150 Đang nhận file");
            Path filePath = currentDir.resolve(filename);
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = dataConnection.getInputStream().read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
            }
            writer.println("226 Tải lên hoàn tất");
        } catch (Exception e) {
            writer.println("550 Lỗi khi tải lên file");
        } finally {
            closeDataConnection();
        }
    }

    private void handleRetr(String filename) {
        try {
            Path filePath = currentDir.resolve(filename);
            if (!Files.exists(filePath)) {
                writer.println("550 File không tồn tại");
                return;
            }
            if (!openDataConnection()) {
                writer.println("425 Không thể mở kết nối dữ liệu");
                return;
            }
            writer.println("150 Đang gửi file");
            try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    dataConnection.getOutputStream().write(buffer, 0, read);
                }
            }
            writer.println("226 Truyền file hoàn tất");
        } catch (Exception e) {
            writer.println("550 Lỗi khi tải file");
        } finally {
            closeDataConnection();
        }
    }

    private void handleDele(String filename) {
        try {
            Path path = currentDir.resolve(filename);
            if (Files.exists(path) && Files.deleteIfExists(path)) {
                writer.println("250 Xóa file thành công");
            } else {
                writer.println("550 Xóa file thất bại");
            }
        } catch (Exception e) {
            writer.println("550 Lỗi khi xóa file");
        }
    }

    private void handleRnfr(String oldName) {
        try {
            Path oldPath = currentDir.resolve(oldName);
            if (Files.exists(oldPath)) {
                rnfrName = oldName;
                writer.println("350 Sẵn sàng để đổi tên, gửi RNTO");
            } else {
                rnfrName = null;
                writer.println("550 File không tồn tại");
            }
        } catch (Exception e) {
            rnfrName = null;
            writer.println("550 Lỗi khi xử lý RNFR");
        }
    }

    private void handleRnto(String newName) {
        try {
            if (rnfrName == null || rnfrName.isBlank()) {
                writer.println("503 Thiếu RNFR trước RNTO");
                return;
            }
            Path oldPath = currentDir.resolve(rnfrName);
            Path newPath = currentDir.resolve(newName);
            rnfrName = null; // reset sau khi dùng
            if (Files.exists(oldPath) && ( !Files.exists(newPath) ) && Files.move(oldPath, newPath) != null) {
                writer.println("250 Đổi tên thành công");
            } else {
                writer.println("550 Đổi tên thất bại");
            }
        } catch (Exception e) {
            rnfrName = null;
            writer.println("550 Lỗi khi đổi tên");
        }
    }

    private void handleMkd(String dirname) {
        try {
            Path dirPath = currentDir.resolve(dirname);
            if (!Files.exists(dirPath) && Files.createDirectory(dirPath) != null) {
                writer.println("257 Thư mục được tạo");
            } else {
                writer.println("550 Tạo thư mục thất bại");
            }
        } catch (Exception e) {
            writer.println("550 Lỗi khi tạo thư mục");
        }
    }

    private boolean openDataConnection() throws IOException {
        if (dataSocket == null) {
            writer.println("425 Chưa ở chế độ passive");
            return false;
        }
        dataConnection = dataSocket.accept();
        return true;
    }

    private void closeDataConnection() {
        try {
            if (dataConnection != null) {
                dataConnection.close();
                dataConnection = null;
            }
            if (dataSocket != null) {
                dataSocket.close();
                dataSocket = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}