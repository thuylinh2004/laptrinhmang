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
        ftpClient.setControlEncoding("UTF-8");
    }

    public boolean connect(String server, int port) {
        try {
            ftpClient.connect(server, port);
            ftpClient.enterLocalPassiveMode(); // Quan trọng
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
            return ftpClient.login(user, pass);
        } catch (IOException e) {
            lastError = e.getMessage();
            return false;
        }
    }

    public boolean register(String user, String pass) {
        try {
            return FTPReply.isPositiveCompletion(ftpClient.sendCommand("REGISTER", user + " " + pass));
        } catch (IOException e) {
            lastError = e.getMessage();
            return false;
        }
    }

    // Trả về danh sách file đã được định dạng: Name|Size|Type
    public List<String> listFiles(String path) {
        List<String> list = new ArrayList<>();
        try {
            String p = (path == null) ? "" : path;
            // Server giờ trả về Unix format nên hàm chuẩn này sẽ hoạt động tốt
            FTPFile[] files = ftpClient.listFiles(p);
            for (FTPFile f : files) {
                if (f.getName().equals(".") || f.getName().equals("..")) continue;
                String type = f.isDirectory() ? "Thư mục" : "Tệp";
                list.add(f.getName() + "|" + f.getSize() + "|" + type);
            }
        } catch (IOException e) {
            lastError = e.getMessage();
        }
        return list;
    }

    public boolean uploadFile(File localFile, String remoteName) {
        try (InputStream input = new FileInputStream(localFile)) {
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            return ftpClient.storeFile(remoteName, input);
        } catch (IOException e) {
            lastError = e.getMessage();
            return false;
        }
    }

    public boolean downloadFile(String remoteName, File localFile) {
        try (OutputStream output = new FileOutputStream(localFile)) {
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            return ftpClient.retrieveFile(remoteName, output);
        } catch (IOException e) {
            lastError = e.getMessage();
            return false;
        }
    }

    public boolean uploadDirectory(File localDir, String remotePath) throws IOException {
        ftpClient.makeDirectory(remotePath);
        ftpClient.changeWorkingDirectory(remotePath);
        File[] files = localDir.listFiles();
        if (files != null) {
            for (File item : files) {
                if (item.isFile()) {
                    try (InputStream input = new FileInputStream(item)) {
                        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
                        ftpClient.storeFile(item.getName(), input);
                    }
                } else if (item.isDirectory()) {
                    uploadDirectory(item, item.getName());
                    ftpClient.changeToParentDirectory();
                }
            }
        }
        return true;
    }

    public boolean downloadDirectory(String remotePath, File localDir) throws IOException {
        if (!localDir.exists()) localDir.mkdirs();
        String current = ftpClient.printWorkingDirectory();
        ftpClient.changeWorkingDirectory(remotePath);
        
        FTPFile[] files = ftpClient.listFiles();
        for (FTPFile f : files) {
            if (f.getName().equals(".") || f.getName().equals("..")) continue;
            if (f.isDirectory()) {
                downloadDirectory(f.getName(), new File(localDir, f.getName()));
                ftpClient.changeToParentDirectory();
            } else {
                try (OutputStream output = new FileOutputStream(new File(localDir, f.getName()))) {
                    ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
                    ftpClient.retrieveFile(f.getName(), output);
                }
            }
        }
        ftpClient.changeWorkingDirectory(current);
        return true;
    }

    public boolean makeDirectory(String path) { try { return ftpClient.makeDirectory(path); } catch(IOException e){ return false; } }
    public boolean deleteFile(String path) { try { return ftpClient.deleteFile(path); } catch(IOException e){ lastError=e.getMessage(); return false; } }
    public boolean renameFile(String f, String t) { try { return ftpClient.rename(f, t); } catch(IOException e){ return false; } }
    public boolean changeDirectory(String path) { try { return ftpClient.changeWorkingDirectory(path); } catch(IOException e){ return false; } }
    public String getWorkingDirectory() { try { return ftpClient.printWorkingDirectory(); } catch(Exception e){ return ""; } }
    public void disconnect() { try { ftpClient.disconnect(); } catch(Exception e){} }
    public String getLastError() { return lastError; }
    public String getHost() { return host; }
    public String getUser() { return user; }
}