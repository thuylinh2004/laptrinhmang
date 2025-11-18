package ftpserver;

import java.io.*;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

public class UserManager {
    private final String userFile;
    private final Map<String, String> users;

    public UserManager() {
        this("users.txt");
    }

    public UserManager(String filePath) {
        this.userFile = filePath;
        users = new HashMap<>();
        loadUsers();
    }

    private void loadUsers() {
        try {
            File file = new File(userFile);
            if (!file.exists()) {
                System.out.println("[INFO] File userFile không tồn tại, tạo mới: " + file.getAbsolutePath());
                file.createNewFile();
            }

            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split("\\|");
                    if (parts.length == 2) users.put(parts[0], parts[1]);
                }
            }

            System.out.println("[INFO] Đọc users.txt thành công, tổng users: " + users.size());

        } catch (IOException e) {
            System.err.println("[ERROR] Lỗi đọc file users.txt: " + e.getMessage());
        }
    }

    private void saveUsers() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(userFile))) {
            for (Map.Entry<String, String> entry : users.entrySet()) {
                bw.write(entry.getKey() + "|" + entry.getValue());
                bw.newLine();
            }
            System.out.println("[INFO] Lưu users.txt thành công, tổng users: " + users.size());
        } catch (IOException e) {
            System.err.println("[ERROR] Lỗi ghi file users.txt: " + e.getMessage());
        }
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            System.err.println("[ERROR] Lỗi hash password: " + e.getMessage());
            return password;
        }
    }

    public synchronized boolean registerUser(String username, String password) {
        if (users.containsKey(username)) return false;
        users.put(username, hashPassword(password));
        saveUsers();
        return true;
    }

    public synchronized boolean authenticate(String username, String password) {
        if (!users.containsKey(username)) return false;
        return users.get(username).equals(hashPassword(password));
    }
}