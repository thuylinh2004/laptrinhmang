package ftpserver;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final UserManager userManager;
    private BufferedReader reader;
    private PrintWriter writer;
    private Path rootDir;
    private Path currentDir;
    private String username;
    private String rnfrName;
    private ServerSocket dataSocket; // Cổng PASV
    private boolean isLoggedIn;

    public ClientHandler(Socket clientSocket, UserManager userManager, String rootPath) {
        this.clientSocket = clientSocket;
        this.userManager = userManager;
        // Sử dụng toAbsolutePath để tránh lỗi đường dẫn tương đối
        this.rootDir = Paths.get(rootPath).toAbsolutePath().normalize();
        this.currentDir = rootDir;
        this.isLoggedIn = false;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));
            writer = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"), true);
            writer.println("220 FTP Server Ready");

            while (true) {
                String line = reader.readLine();
                if (line == null) break;
                
                System.out.println("[CMD] " + line);
                
                String[] parts = line.split(" ", 2);
                String cmd = parts[0].toUpperCase();
                String arg = parts.length > 1 ? parts[1] : "";

                try {
                    switch (cmd) {
                        case "USER": username = arg; writer.println("331 Password required"); break;
                        case "PASS": 
                            if (userManager.authenticate(username, arg)) {
                                isLoggedIn = true; writer.println("230 Logged in");
                            } else { writer.println("530 Failed"); }
                            break;
                        case "LOGOUT": // Xử lý đăng xuất nhưng không ngắt kết nối socket
                            isLoggedIn = false;
                            username = null;
                            writer.println("220 Logged out");
                            break;
                        case "REGISTER":
                             String[] reg = arg.split(" ", 2);
                             if (reg.length == 2 && userManager.registerUser(reg[0], reg[1])) writer.println("200 OK");
                             else writer.println("503 Failed");
                             break;
                        case "PWD":  checkLogin(() -> handlePwd()); break;
                        case "CWD":  checkLogin(() -> handleCwd(arg)); break;
                        case "PASV": checkLogin(() -> handlePasv()); break;
                        case "LIST": checkLogin(() -> handleList()); break;
                        case "RETR": checkLogin(() -> handleRetr(arg)); break;
                        case "STOR": checkLogin(() -> handleStor(arg)); break;
                        case "DELE": checkLogin(() -> handleDele(arg)); break;
                        case "MKD":  checkLogin(() -> handleMkd(arg)); break;
                        case "RNFR": checkLogin(() -> handleRnfr(arg)); break;
                        case "RNTO": checkLogin(() -> handleRnto(arg)); break;
                        case "QUIT": writer.println("221 Bye"); return;
                        case "SYST": writer.println("215 UNIX Type: L8"); break;
                        case "TYPE": writer.println("200 Type set to " + arg); break;
                        default: writer.println("502 Not implemented");
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    writer.println("500 Internal Server Error");
                    closeDataSocket(); 
                }
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + e.getMessage());
        } finally {
            closeDataSocket();
            try { clientSocket.close(); } catch (IOException e) {}
        }
    }
    
    private void checkLogin(Runnable action) {
        if (isLoggedIn) action.run(); else writer.println("530 Login first");
    }

    private void handlePasv() {
        closeDataSocket(); 
        try {
            dataSocket = new ServerSocket(0);
            byte[] ip = clientSocket.getLocalAddress().getAddress();
            if (clientSocket.getLocalAddress().isAnyLocalAddress()) {
                 ip = InetAddress.getLocalHost().getAddress();
            }
            
            int port = dataSocket.getLocalPort();
            writer.println(String.format("227 Passive Mode (%d,%d,%d,%d,%d,%d)",
                    ip[0]&0xff, ip[1]&0xff, ip[2]&0xff, ip[3]&0xff, port/256, port%256));
        } catch (IOException e) { 
            writer.println("500 Error entering Passive Mode"); 
        }
    }

    private void handleList() {
        if (dataSocket == null || dataSocket.isClosed()) {
            writer.println("425 Use PASV first");
            return;
        }

        try (Socket data = dataSocket.accept(); 
             PrintWriter out = new PrintWriter(new OutputStreamWriter(data.getOutputStream(), "UTF-8"), true)) {
            
            writer.println("150 Sending list"); 

            File[] files = currentDir.toFile().listFiles();
            if (files != null) {
                for (File f : files) {
                    String perm = f.isDirectory() ? "drwxr-xr-x" : "-rw-r--r--";
                    out.printf("%s 1 owner group %d Jan 01 00:00 %s%n", perm, f.length(), f.getName());
                }
            }
            out.flush(); 
            
        } catch (Exception e) {
            writer.println("550 List failed");
            return;
        } finally {
            closeDataSocket(); 
        }
        
        writer.println("226 Done"); 
    }

    private void handleRetr(String filename) {
        Path file = resolvePath(filename);
        if (file != null && Files.exists(file) && !Files.isDirectory(file)) {
            try (Socket data = dataSocket.accept();
                 FileInputStream fis = new FileInputStream(file.toFile());
                 OutputStream os = data.getOutputStream()) {
                 
                writer.println("150 Sending file");
                byte[] buf = new byte[8192];
                int read;
                while ((read = fis.read(buf)) != -1) {
                    os.write(buf, 0, read);
                }
                os.flush();
            } catch (Exception e) { 
                writer.println("550 Transfer failed"); 
                return;
            } finally {
                closeDataSocket();
            }
            writer.println("226 Done"); 
        } else { 
            writer.println("550 Not found"); 
            closeDataSocket();
        }
    }

    private void handleStor(String filename) {
        Path file = resolvePath(filename);
        if (file != null) {
            try (Socket data = dataSocket.accept();
                 FileOutputStream fos = new FileOutputStream(file.toFile());
                 InputStream is = data.getInputStream()) {
                 
                writer.println("150 Receiving file");
                byte[] buf = new byte[8192];
                int read;
                while ((read = is.read(buf)) != -1) {
                    fos.write(buf, 0, read);
                }
                fos.flush();
            } catch (Exception e) { 
                writer.println("550 Transfer failed"); 
                return;
            } finally {
                closeDataSocket();
            }
            writer.println("226 Done"); 
        } else { 
            writer.println("553 Invalid name"); 
            closeDataSocket();
        }
    }
    
    private void handleDele(String name) {
        Path p = resolvePath(name);
        try {
            if (p != null && Files.deleteIfExists(p)) writer.println("250 Deleted");
            else writer.println("550 Delete failed");
        } catch (IOException e) { writer.println("550 Failed (Not empty?)"); }
    }
    
    private void handleMkd(String name) {
        Path p = resolvePath(name);
        try {
            Files.createDirectory(p); writer.println("257 Created");
        } catch (Exception e) { writer.println("550 Failed"); }
    }

    private void handlePwd() {
        String relativePath = rootDir.relativize(currentDir).toString();
        String displayPath = "/" + relativePath.replace('\\', '/');
        if (displayPath.equals("/.")) displayPath = "/"; 
        writer.println("257 \"" + displayPath + "\"");
    }
    
    private void handleCwd(String dir) {
        if (dir.equals("..")) {
            if (!currentDir.equals(rootDir)) {
                currentDir = currentDir.getParent();
            }
            writer.println("250 OK");
        } else {
            Path p = resolvePath(dir);
            if (p != null && Files.isDirectory(p)) {
                currentDir = p; 
                writer.println("250 OK");
            } else { 
                writer.println("550 Failed"); 
            }
        }
    }
    
    private void handleRnfr(String old) {
        Path p = resolvePath(old);
        if (p != null && Files.exists(p)) { rnfrName = old; writer.println("350 Ready"); }
        else writer.println("550 Not found");
    }
    
    private void handleRnto(String newName) {
        if (rnfrName == null) { writer.println("503 Sequence error"); return; }
        try {
            Files.move(resolvePath(rnfrName), resolvePath(newName));
            writer.println("250 Renamed");
        } catch (Exception e) { writer.println("550 Rename failed"); }
        rnfrName = null;
    }

    private Path resolvePath(String input) {
        if (input == null || input.isEmpty()) return null;
        try {
            Path p = currentDir.resolve(input).normalize();
            if (p.startsWith(rootDir)) return p;
        } catch (Exception e) {}
        return null;
    }
    
    private void closeDataSocket() {
        try { 
            if(dataSocket != null && !dataSocket.isClosed()) {
                dataSocket.close();
            }
        } catch(Exception e){}
        dataSocket = null; 
    }
}