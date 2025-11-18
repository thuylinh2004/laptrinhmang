package ftpclient;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class FileBrowserPanel extends JPanel {
    private final FTPClientCore ftpCore;
    private final FTPClientGUI parentGUI;
    private JTable tableFiles;
    private DefaultTableModel tableModel;
    private JList<String> folderList; // sẽ dùng làm danh sách tệp
    private DefaultListModel<String> folderModel;
    private String currentPath = "";

    private JLabel lblIP, lblUser;
    private JTextArea textPreview;

    public FileBrowserPanel(FTPClientCore ftpCore, FTPClientGUI parentGUI) {
        this.ftpCore = ftpCore;
        this.parentGUI = parentGUI;
        setLayout(new BorderLayout());

        // ===== Header =====
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

        JLabel title = new JLabel("Truyền Tải Dữ Liệu");
        title.setFont(new Font("Arial", Font.BOLD, 22));
        header.add(title, BorderLayout.WEST);

        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 5));
        lblIP = new JLabel("Địa chỉ IP: " + ftpCore.getHost());
        lblUser = new JLabel("Tên đăng nhập: " + ftpCore.getUser());
        JLabel lblDisconnect = new JLabel("[Ngắt kết nối]");
        lblDisconnect.setForeground(Color.RED);
        lblDisconnect.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        lblDisconnect.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                ftpCore.disconnect();
                parentGUI.showConnectPanel();
            }
        });
        infoPanel.add(lblIP);
        infoPanel.add(lblUser);
        infoPanel.add(lblDisconnect);
        header.add(infoPanel, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
        // cập nhật thông tin IP/User ban đầu
        updateInfoLabels();

        // ===== Center (Folder + Files) =====
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(200);

        // Thư mục
        folderModel = new DefaultListModel<>();
        folderList = new JList<>(folderModel);
        folderList.setBorder(BorderFactory.createTitledBorder("Tệp và Thư mục"));
        splitPane.setLeftComponent(new JScrollPane(folderList));

        // Khu vực xem nội dung tệp
        textPreview = new JTextArea();
        textPreview.setEditable(false);
        textPreview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane previewScroll = new JScrollPane(textPreview);
        previewScroll.setBorder(BorderFactory.createTitledBorder("Nội dung tệp"));
        splitPane.setRightComponent(previewScroll);

        add(splitPane, BorderLayout.CENTER);

        // ===== Footer (Buttons) =====
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));

        JButton btnUpload = new JButton("Tải lên");
        JButton btnDownload = new JButton("Tải xuống");
        JButton btnRename = new JButton("Đổi tên");
        JButton btnMkdir = new JButton("Tạo thư mục");
        JButton btnDelete = new JButton("Xóa");
        btnDelete.setBackground(Color.RED);
        btnDelete.setForeground(Color.WHITE);

        buttonPanel.add(btnUpload);
        buttonPanel.add(btnDownload);
        buttonPanel.add(btnRename);
        buttonPanel.add(btnMkdir);
        buttonPanel.add(btnDelete);

        add(buttonPanel, BorderLayout.SOUTH);

        // ===== Sự kiện =====
        btnUpload.addActionListener(e -> handleUpload());
        btnDownload.addActionListener(e -> handleDownload());
        btnRename.addActionListener(e -> handleRename());
        btnMkdir.addActionListener(e -> handleMkdir());
        btnDelete.addActionListener(e -> handleDelete());

        // Double-click vào thư mục để điều hướng
        folderList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String sel = folderList.getSelectedValue();
                    if (sel == null) return;
                    if ("..".equals(sel)) {
                        String parent = currentPath;
                        if (parent == null || parent.isEmpty() || "/".equals(parent)) {
                            parent = "/";
                        } else {
                            parent = parent.replace('\\', '/');
                            int idx = parent.lastIndexOf('/');
                            if (idx > 0) parent = parent.substring(0, idx); else parent = "/";
                        }
                        if (!ftpCore.changeDirectory(parent)) {
                            JOptionPane.showMessageDialog(FileBrowserPanel.this, "Không thể quay lại thư mục cha\n" + ftpCore.getLastError());
                        }
                    } else if (sel.endsWith("/")) {
                        String dirName = sel.substring(0, sel.length()-1);
                        if (!ftpCore.changeDirectory(dirName)) {
                            JOptionPane.showMessageDialog(FileBrowserPanel.this, "Không thể vào thư mục: " + sel + "\n" + ftpCore.getLastError());
                        }
                    } else {
                        // là tệp -> xem trước (đã có listener selection), không đổi thư mục
                    }
                    refreshFileList();
                }
            }
        });

        // Xem trước nội dung khi chọn tệp
        folderList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String sel = folderList.getSelectedValue();
                if (sel == null) {
                    textPreview.setText("");
                } else if (sel.equals("..")) {
                    textPreview.setText("");
                } else if (sel.endsWith("/")) {
                    // xem trước nội dung thư mục mà không đổi thư mục làm việc
                    String dirName = sel.substring(0, sel.length()-1);
                    previewFolderContents(dirName);
                } else {
                    previewSelectedFile();
                }
            }
        });

        refreshFileList();
    }

    public void refreshFileList() {
        // cập nhật lại thông tin hiển thị IP/User
        updateInfoLabels();
        // đồng bộ thư mục hiện tại với server (PWD)
        String wd = ftpCore.getWorkingDirectory();
        if (wd != null && !wd.isEmpty()) {
            currentPath = wd;
        }
        // reset danh sách tệp và nội dung xem trước
        folderModel.clear();
        textPreview.setText("");

        // load folder/file từ FTP core (liệt kê theo thư mục làm việc hiện tại của server)
        List<String> files = ftpCore.listFiles("");

        // Mục quay lại nếu không ở root
        if (currentPath != null && !currentPath.isEmpty() && !"/".equals(currentPath)) {
            folderModel.addElement("..");
        }

        for (String f : files) {
            // giả sử FTP core trả về "name|size|type"
            String[] parts = f.split("\\|");
            if (parts.length == 3) {
                String name = parts[0];
                String type = parts[2];
                if ("Thư mục".equalsIgnoreCase(type)) {
                    folderModel.addElement(name + "/");
                } else {
                    folderModel.addElement(name);
                }
            } else {
                // Không rõ loại -> coi như file
                folderModel.addElement(f);
            }
        }
    }

    // Xem nội dung tệp đơn giản (văn bản). Với nhị phân sẽ hiển thị thông báo.
    private void previewSelectedFile() {
        String name = folderList.getSelectedValue();
        if (name == null || name.isEmpty()) {
            textPreview.setText("");
            return;
        }
        // Tải về tạm thời và hiển thị
        try {
            File tmp = File.createTempFile("ftp_preview_", ".tmp");
            tmp.deleteOnExit();
            if (ftpCore.downloadFile(name, tmp)) {
                // đọc tối đa ~200KB để hiển thị
                byte[] buf = java.nio.file.Files.readAllBytes(tmp.toPath());
                int max = Math.min(buf.length, 200 * 1024);
                boolean looksBinary = false;
                for (int i = 0; i < max; i++) {
                    int b = buf[i] & 0xFF;
                    if (b == 0) { looksBinary = true; break; }
                }
                if (looksBinary) {
                    textPreview.setText("[Tệp nhị phân - không thể hiển thị nội dung]");
                } else {
                    String content = new String(buf, 0, max, java.nio.charset.StandardCharsets.UTF_8);
                    if (buf.length > max) content += "\n\n...[đã rút gọn xem trước]";
                    textPreview.setText(content);
                    textPreview.setCaretPosition(0);
                }
            } else {
                textPreview.setText("[Không thể tải tệp để xem trước]\n" + ftpCore.getLastError());
            }
        } catch (IOException ex) {
            textPreview.setText("[Lỗi xem trước]\n" + ex.getMessage());
        }
    }

    // Xem trước nội dung thư mục mà không thay đổi thư mục làm việc hiện tại
    private void previewFolderContents(String dirName) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Thư mục] ").append(dirName).append("\n\n");

        List<String> entries = ftpCore.listFiles(dirName);
        if (entries == null || entries.isEmpty()) {
            // Fallback: tạm CWD sang thư mục rồi liệt kê, sau đó quay về
            String pwd = ftpCore.getWorkingDirectory();
            if (ftpCore.changeDirectory(dirName)) {
                entries = ftpCore.listFiles("");
                if (pwd != null && !pwd.isEmpty()) {
                    ftpCore.changeDirectory(pwd);
                }
            }
        }

        if (entries == null || entries.isEmpty()) {
            sb.append("(Thư mục trống hoặc không thể liệt kê)\n");
        } else {
            for (String f : entries) {
                String[] parts = f.split("\\|");
                String name = parts.length > 0 ? parts[0] : f;
                String type = parts.length > 2 ? parts[2] : "";
                if ("Thư mục".equalsIgnoreCase(type)) {
                    sb.append("/ ");
                } else {
                    sb.append("- ");
                }
                sb.append(name);
                if (parts.length > 1 && parts[1] != null && !parts[1].isEmpty()) {
                    sb.append("\t[").append(parts[1]).append(" bytes]");
                }
                sb.append("\n");
            }
        }

        textPreview.setText(sb.toString());
        textPreview.setCaretPosition(0);
    }

    private void updateInfoLabels() {
        if (lblIP != null) {
            lblIP.setText("Địa chỉ IP: " + (ftpCore.getHost() == null ? "" : ftpCore.getHost()));
        }
        if (lblUser != null) {
            lblUser.setText("Tên đăng nhập: " + (ftpCore.getUser() == null ? "" : ftpCore.getUser()));
        }
    }

    private void handleUpload() {
        if (!ftpCore.isConnected()) {
            JOptionPane.showMessageDialog(this, "Chưa kết nối tới máy chủ FTP. Vui lòng kết nối/đăng nhập trước.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File localFile = chooser.getSelectedFile();
            // Nếu người dùng đang chọn một thư mục ở danh sách trái, tải vào thư mục đó; còn không thì tải vào PWD
            String sel = folderList.getSelectedValue();
            String remoteName = localFile.getName();
            String remotePath = remoteName;
            if (sel != null && sel.endsWith("/")) {
                String dirName = sel.substring(0, sel.length() - 1);
                remotePath = dirName + "/" + remoteName;
            }
            if (ftpCore.uploadFile(localFile, remotePath)) {
                JOptionPane.showMessageDialog(this, "Tải lên thành công!");
                refreshFileList();
            } else {
                JOptionPane.showMessageDialog(this, "Tải lên thất bại!\nChi tiết: " + ftpCore.getLastError());
            }
        }
    }

    private void handleDownload() {
        if (!ftpCore.isConnected()) {
            JOptionPane.showMessageDialog(this, "Chưa kết nối tới máy chủ FTP. Vui lòng kết nối/đăng nhập trước.");
            return;
        }
        String fileName = folderList.getSelectedValue();
        if (fileName == null || fileName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Chọn file để tải xuống!");
            return;
        }
        if (fileName.endsWith("/")) {
            JOptionPane.showMessageDialog(this, "Không thể tải xuống một thư mục. Hãy chọn tệp.");
            return;
        }
        String remotePath = fileName; // thao tác theo thư mục làm việc
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(fileName));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File saveFile = chooser.getSelectedFile();
            if (ftpCore.downloadFile(remotePath, saveFile)) {
                JOptionPane.showMessageDialog(this, "Tải xuống thành công!");
            } else {
                JOptionPane.showMessageDialog(this, "Tải xuống thất bại!\nChi tiết: " + ftpCore.getLastError());
            }
        }
    }

    private void handleDelete() {
        if (!ftpCore.isConnected()) {
            JOptionPane.showMessageDialog(this, "Chưa kết nối tới máy chủ FTP. Vui lòng kết nối/đăng nhập trước.");
            return;
        }
        String fileName = folderList.getSelectedValue();
        if (fileName == null || fileName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Chọn file để xóa!");
            return;
        }
        String path = fileName; // thao tác theo thư mục làm việc
        int confirm = JOptionPane.showConfirmDialog(this,
                "Xóa " + fileName + " ?", "Xác nhận", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            if (ftpCore.deleteFile(path)) {
                JOptionPane.showMessageDialog(this, "Xóa thành công!");
                refreshFileList();
            } else {
                JOptionPane.showMessageDialog(this, "Xóa thất bại!\nChi tiết: " + ftpCore.getLastError());
            }
        }
    }

    private void handleRename() {
        if (!ftpCore.isConnected()) {
            JOptionPane.showMessageDialog(this, "Chưa kết nối tới máy chủ FTP. Vui lòng kết nối/đăng nhập trước.");
            return;
        }
        String oldName = folderList.getSelectedValue();
        if (oldName == null || oldName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Chọn file để đổi tên!");
            return;
        }
        String oldPath = oldName; // theo thư mục làm việc
        String newName = JOptionPane.showInputDialog(this, "Nhập tên mới:", oldName);
        if (newName != null && !newName.trim().isEmpty()) {
            String newPath = newName; // theo thư mục làm việc
            if (ftpCore.renameFile(oldPath, newPath)) {
                JOptionPane.showMessageDialog(this, "Đổi tên thành công!");
                refreshFileList();
            } else {
                JOptionPane.showMessageDialog(this, "Đổi tên thất bại!\nChi tiết: " + ftpCore.getLastError());
            }
        }
    }

    private void handleMkdir() {
        if (!ftpCore.isConnected()) {
            JOptionPane.showMessageDialog(this, "Chưa kết nối tới máy chủ FTP. Vui lòng kết nối/đăng nhập trước.");
            return;
        }
        String folderName = JOptionPane.showInputDialog(this, "Tên thư mục mới:");
        if (folderName != null && !folderName.trim().isEmpty()) {
            String path = folderName; // theo thư mục làm việc
            if (ftpCore.makeDirectory(path)) {
                JOptionPane.showMessageDialog(this, "Tạo thư mục thành công!");
                refreshFileList();
            } else {
                JOptionPane.showMessageDialog(this, "Tạo thư mục thất bại!\nChi tiết: " + ftpCore.getLastError());
            }
        }
    }
}
