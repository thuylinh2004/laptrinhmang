package ftpclient;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class FTPClientCore {
    private FTPClient ftpClient;
    private String host = "";
    private String user = "";
    private String lastError = "";

    public FTPClientCore() {
        ftpClient = new FTPClient();
        ftpClient.setControlEncoding("UTF-8"); // Hỗ trợ tiếng Việt
    }

    public boolean connect(String server, int port) {
        try {
            ftpClient.connect(server, port);
            ftpClient.enterLocalPassiveMode(); // BẮT BUỘC: Chế độ Passive
            boolean ok = FTPReply.isPositiveCompletion(ftpClient.getReplyCode());
            this.host = ok ? server : "";
            lastError = ok ? "" : ftpClient.getReplyString();
            return ok;
        } catch (IOException e) {
            lastError = e.getMessage();
            return false;
        }
    }

    public boolean login(String user, String pass) {
        try {
            boolean ok = ftpClient.login(user, pass);
            if (ok) {
                this.user = user;
                lastError = "";
            } else {
                lastError = ftpClient.getReplyString();
            }
            return ok;
        } catch (IOException e) {
            lastError = e.getMessage();
            return false;
        }
    }

    public boolean register(String user, String pass) {
        try {
            int code = ftpClient.sendCommand("REGISTER", user + " " + pass);
            return FTPReply.isPositiveCompletion(code);
        } catch (IOException e) {
            lastError = e.getMessage();
            return false;
        }
    }

    // Liệt kê file (Đọc raw line vì Server trả format custom)
    public List<String> listFiles(String path) {
        List<String> list = new ArrayList<>();
        try {
            String p = (path == null) ? "" : path;
            org.apache.commons.net.ftp.FTPListParseEngine engine = ftpClient.initiateListParsing(p);
            while (engine.hasNext()) {
                FTPFile[] files = engine.getNext(25);
                for (FTPFile f : files) {
                    // Server trả về: name|size|type. Commons-net sẽ đưa vào RawListing
                    if (f.getRawListing() != null) list.add(f.getRawListing());
                }
            }
        } catch (IOException e) {
            lastError = e.getMessage();
        }
        return list;
    }

    // Upload file lẻ
    public boolean uploadFile(File localFile, String remoteName) {
        try (InputStream input = new FileInputStream(localFile)) {
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            boolean done = ftpClient.storeFile(remoteName, input);
            if (!done) lastError = ftpClient.getReplyString();
            return done;
        } catch (IOException e) {
            lastError = e.getMessage();
            return false;
        }
    }

    // Download file lẻ
    public boolean downloadFile(String remoteName, File localFile) {
        try (OutputStream output = new FileOutputStream(localFile)) {
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            boolean done = ftpClient.retrieveFile(remoteName, output);
            if (!done) lastError = ftpClient.getReplyString();
            return done;
        } catch (IOException e) {
            lastError = e.getMessage();
            return false;
        }
    }

    // Upload cả thư mục (Đệ quy)
    public boolean uploadDirectory(File localDir, String remotePath) throws IOException {
        ftpClient.makeDirectory(remotePath); // Tạo folder trên server
        ftpClient.changeWorkingDirectory(remotePath); // Vào folder đó

        File[] files = localDir.listFiles();
        if (files != null) {
            for (File item : files) {
                if (item.isFile()) {
                    try (InputStream input = new FileInputStream(item)) {
                        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
                        ftpClient.storeFile(item.getName(), input);
                    }
                } else if (item.isDirectory()) {
                    // Đệ quy: upload thư mục con, sau đó quay lại
                    uploadDirectory(item, item.getName());
                    ftpClient.changeToParentDirectory();
                }
            }
        }
        return true;
    }

    // Download cả thư mục (Đệ quy)
    public boolean downloadDirectory(String remotePath, File localDir) throws IOException {
        if (!localDir.exists()) localDir.mkdirs();
        
        // Vào thư mục remote để liệt kê
        String current = ftpClient.printWorkingDirectory();
        ftpClient.changeWorkingDirectory(remotePath);
        
        List<String> entries = listFiles(""); // Liệt kê file trong thư mục hiện tại
        
        for (String entry : entries) {
            String[] parts = entry.split("\\|");
            if (parts.length < 3) continue;
            
            String name = parts[0];
            String type = parts[2];
            
            if (type.contains("Thư mục") || type.equalsIgnoreCase("d")) {
                downloadDirectory(name, new File(localDir, name));
                ftpClient.changeToParentDirectory(); // Quay lại sau khi xong folder con
            } else {
                try (OutputStream output = new FileOutputStream(new File(localDir, name))) {
                    ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
                    ftpClient.retrieveFile(name, output);
                }
            }
        }
        // Khôi phục vị trí cũ
        ftpClient.changeWorkingDirectory(current);
        return true;
    }

    public boolean makeDirectory(String path) {
        try { return ftpClient.makeDirectory(path); } catch (IOException e) { lastError=e.getMessage(); return false; }
    }
    public boolean deleteFile(String path) {
        try { return ftpClient.deleteFile(path); } catch (IOException e) { lastError=e.getMessage(); return false; }
    }
    public boolean renameFile(String from, String to) {
        try { return ftpClient.rename(from, to); } catch (IOException e) { lastError=e.getMessage(); return false; }
    }
    public boolean changeDirectory(String path) {
        try { return ftpClient.changeWorkingDirectory(path); } catch (IOException e) { lastError=e.getMessage(); return false; }
    }
    public String getWorkingDirectory() {
        try { return ftpClient.printWorkingDirectory(); } catch(Exception e) { return ""; }
    }
    public void disconnect() { try { ftpClient.disconnect(); } catch(Exception e){} }
    public boolean isConnected() { return ftpClient.isConnected(); }
    public String getLastError() { return lastError; }
    public String getHost() { return host; }
    public String getUser() { return user; }
}