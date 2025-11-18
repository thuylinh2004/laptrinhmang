package ftpclient;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTP;
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
    }

    public boolean connect(String server, int port) {
        try {
            ftpClient.connect(server, port);
            ftpClient.enterLocalPassiveMode();
            boolean ok = FTPReply.isPositiveCompletion(ftpClient.getReplyCode());
            this.host = ok ? server : "";
            if (!ok) {
                lastError = ftpClient.getReplyString();
            } else {
                lastError = "";
            }
            return ok;
        } catch (IOException e) {
            e.printStackTrace();
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
            e.printStackTrace();
            lastError = e.getMessage();
            return false;
        }
    }

    public boolean register(String user, String pass) {
        try {
            ftpClient.sendCommand("REGISTER", user + " " + pass);
            int replyCode = ftpClient.getReplyCode();
            boolean ok = FTPReply.isPositiveCompletion(replyCode); // 200 cho thành công
            if (!ok) {
                lastError = ftpClient.getReplyString();
            } else {
                lastError = "";
            }
            return ok;
        } catch (IOException e) {
            e.printStackTrace();
            lastError = e.getMessage();
            return false;
        }
    }

    public boolean isConnected() {
        return ftpClient != null && ftpClient.isConnected();
    }

    public void disconnect() {
        try {
            if (ftpClient.isConnected()) {
                ftpClient.logout();
                ftpClient.disconnect();
            }
            lastError = "";
        } catch (IOException e) {
            e.printStackTrace();
            lastError = e.getMessage();
        }
    }

    public List<String> listFiles(String path) {
        List<String> list = new ArrayList<>();
        if (!isConnected()) return list;

        try {
            FTPFile[] files = (path == null || path.isEmpty()) ?
                              ftpClient.listFiles() : ftpClient.listFiles(path);
            if (files != null && files.length > 0) {
                for (FTPFile f : files) {
                    String name = f.getName();
                    if (name == null || name.equals(".") || name.equals("..")) continue;
                    String size = f.isFile() ? String.valueOf(f.getSize()) : "";
                    String type = f.isDirectory() ? "Thư mục" : "Tệp";
                    list.add(name + "|" + size + "|" + type);
                }
            }

            // Fallback: some custom servers don't provide parseable LIST output
            if (list.isEmpty()) {
                String[] names = (path == null || path.isEmpty()) ?
                                  ftpClient.listNames() : ftpClient.listNames(path);
                if (names != null) {
                    for (String name : names) {
                        if (name == null || name.equals(".") || name.equals("..")) continue;
                        list.add(name + "||");
                    }
                }
            }

            lastError = "";
        } catch (IOException e) {
            e.printStackTrace();
            lastError = e.getMessage();
        }
        return list;
    }

    public boolean uploadFile(File localFile, String remotePath) {
        if (!isConnected()) return false;
        try (FileInputStream fis = new FileInputStream(localFile)) {
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            return ftpClient.storeFile(remotePath, fis);
        } catch (IOException e) {
            e.printStackTrace();
            lastError = e.getMessage();
            return false;
        }
    }

    public boolean downloadFile(String remotePath, File localFile) {
        if (!isConnected()) return false;
        try (FileOutputStream fos = new FileOutputStream(localFile)) {
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            return ftpClient.retrieveFile(remotePath, fos);
        } catch (IOException e) {
            e.printStackTrace();
            lastError = e.getMessage();
            return false;
        }
    }

    public boolean deleteFile(String path) {
        if (!isConnected()) return false;
        try {
            boolean ok = ftpClient.deleteFile(path);
            if (!ok) lastError = ftpClient.getReplyString(); else lastError = "";
            return ok;
        } catch (IOException e) {
            e.printStackTrace();
            lastError = e.getMessage();
            return false;
        }
    }

    public boolean renameFile(String oldPath, String newPath) {
        try {
            boolean ok = ftpClient.rename(oldPath, newPath);
            if (!ok) lastError = ftpClient.getReplyString(); else lastError = "";
            return ok;
        } catch (IOException e) {
            e.printStackTrace();
            lastError = e.getMessage();
            return false;
        }
    }

    public boolean makeDirectory(String path) {
        if (!isConnected()) return false;
        try {
            boolean ok = ftpClient.makeDirectory(path);
            if (!ok) lastError = ftpClient.getReplyString(); else lastError = "";
            return ok;
        } catch (IOException e) {
            e.printStackTrace();
            lastError = e.getMessage();
            return false;
        }
    }

    public String getLastError() {
        return lastError;
    }

    public String getHost() {
        return host;
    }

    public String getUser() {
        return user;
    }

    public String getWorkingDirectory() {
        if (!isConnected()) return "";
        try {
            String pwd = ftpClient.printWorkingDirectory();
            return pwd == null ? "" : pwd;
        } catch (IOException e) {
            lastError = e.getMessage();
            return "";
        }
    }

    public boolean changeDirectory(String path) {
        if (!isConnected()) return false;
        try {
            boolean ok = ftpClient.changeWorkingDirectory(path);
            if (!ok) {
                lastError = ftpClient.getReplyString();
            } else {
                lastError = "";
            }
            return ok;
        } catch (IOException e) {
            lastError = e.getMessage();
            return false;
        }
    }
}