package ftpclient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.List;

public class FileBrowserPanel extends JPanel {
    private final FTPClientCore ftpCore;
    private final FTPClientGUI parentGUI;
    private JList<String> listFiles;
    private DefaultListModel<String> listModel;
    private JTextArea textPreview;
    private String currentPath = "";
    private JPopupMenu contextMenu;

    public FileBrowserPanel(FTPClientCore ftpCore, FTPClientGUI parentGUI) {
        this.ftpCore = ftpCore;
        this.parentGUI = parentGUI;
        setLayout(new BorderLayout());
        
        // DEBUG: In ra dòng này để chắc chắn bạn đã chạy code mới
        System.out.println("=== ĐANG CHẠY CODE FILE BROWSER MỚI (FIXED) ===");

        // --- Header ---
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));
        JLabel title = new JLabel("  Quản lý tập tin (File Manager)");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        header.add(title, BorderLayout.WEST);
        
        JButton btnLogout = new JButton("Đăng xuất");
        btnLogout.addActionListener(e -> {
            ftpCore.disconnect();
            parentGUI.showLoginPanel();
        });
        header.add(btnLogout, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // --- Center: List + Preview ---
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(400);

        listModel = new DefaultListModel<>();
        listFiles = new JList<>(listModel);
        listFiles.setFont(new Font("Monospaced", Font.PLAIN, 14));
        listFiles.setCellRenderer(new FileListCellRenderer()); 
        
        createContextMenu();

        listFiles.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) { 
                    String selected = listFiles.getSelectedValue();
                    if (selected == null) return;
                    if (selected.equals("..")) navigateUp();
                    else if (selected.endsWith("/")) navigateTo(selected.substring(0, selected.length() - 1));
                }
                if (SwingUtilities.isRightMouseButton(e)) {
                    int index = listFiles.locationToIndex(e.getPoint());
                    listFiles.setSelectedIndex(index);
                    contextMenu.show(listFiles, e.getX(), e.getY());
                }
            }
        });
        
        listFiles.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) previewSelectedFile();
        });

        splitPane.setLeftComponent(new JScrollPane(listFiles));

        textPreview = new JTextArea();
        textPreview.setEditable(false);
        JScrollPane previewScroll = new JScrollPane(textPreview);
        previewScroll.setBorder(BorderFactory.createTitledBorder("Xem trước nội dung"));
        splitPane.setRightComponent(previewScroll);

        add(splitPane, BorderLayout.CENTER);

        // --- Footer: Toolbar ---
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnUpload = new JButton("Tải lên");
        JButton btnDownload = new JButton("Tải xuống");
        JButton btnMkdir = new JButton("Tạo thư mục");
        JButton btnRename = new JButton("Đổi tên");
        JButton btnDelete = new JButton("Xóa");
        JButton btnRefresh = new JButton("Làm mới");

        // SỬ DỤNG THREAD ĐỂ TRÁNH TREO GIAO DIỆN
        btnUpload.addActionListener(e -> actionUpload());
        btnDownload.addActionListener(e -> actionDownload());
        btnMkdir.addActionListener(e -> actionMkdir());
        btnRename.addActionListener(e -> actionRename());
        btnDelete.addActionListener(e -> actionDelete());
        btnRefresh.addActionListener(e -> refreshFileList());

        toolbar.add(btnUpload);
        toolbar.add(btnDownload);
        toolbar.add(btnMkdir);
        toolbar.add(btnRename);
        toolbar.add(btnDelete);
        toolbar.add(btnRefresh);
        add(toolbar, BorderLayout.SOUTH);
    }

    public void refreshFileList() {
        // Chạy trên thread riêng để không đơ khi mạng lag
        new Thread(() -> {
            currentPath = ftpCore.getWorkingDirectory();
            List<String> files = ftpCore.listFiles("");
            
            SwingUtilities.invokeLater(() -> {
                listModel.clear();
                textPreview.setText("");
                if (currentPath != null && !currentPath.equals("/") && !currentPath.isEmpty()) {
                    listModel.addElement("..");
                }
                for (String line : files) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 3) {
                        String name = parts[0];
                        String type = parts[2];
                        if (type.contains("Thư mục") || type.equalsIgnoreCase("d")) {
                            listModel.addElement(name + "/"); 
                        } else {
                            listModel.addElement(name);
                        }
                    } else {
                         listModel.addElement(line); 
                    }
                }
            });
        }).start();
    }
    
    private void previewSelectedFile() {
        String selected = listFiles.getSelectedValue();
        if (selected == null || selected.equals("..") || selected.endsWith("/")) {
            textPreview.setText("");
            return;
        }
        
        new Thread(() -> {
            try {
                File tmp = File.createTempFile("ftp_preview", ".txt");
                tmp.deleteOnExit();
                if (ftpCore.downloadFile(selected, tmp)) {
                    byte[] bytes = java.nio.file.Files.readAllBytes(tmp.toPath());
                    String content = new String(bytes, 0, Math.min(bytes.length, 5000)); 
                    SwingUtilities.invokeLater(() -> {
                        textPreview.setText(content);
                        textPreview.setCaretPosition(0);
                    });
                } else {
                    SwingUtilities.invokeLater(() -> textPreview.setText("[Không thể tải nội dung]"));
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> textPreview.setText("[Lỗi: " + e.getMessage() + "]"));
            }
        }).start();
    }

    private void actionUpload() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File localFile = chooser.getSelectedFile();
            // QUAN TRỌNG: Chạy upload trên luồng riêng (Thread)
            new Thread(() -> {
                boolean ok;
                try {
                    if (localFile.isDirectory()) {
                        ok = ftpCore.uploadDirectory(localFile, localFile.getName());
                    } else {
                        ok = ftpCore.uploadFile(localFile, localFile.getName());
                    }
                    SwingUtilities.invokeLater(() -> {
                        if(ok) { 
                            JOptionPane.showMessageDialog(this, "Tải lên thành công!"); 
                            refreshFileList(); 
                        } else {
                            JOptionPane.showMessageDialog(this, "Tải lên thất bại: " + ftpCore.getLastError());
                        }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }).start();
        }
    }

    private void actionDownload() {
        String selected = listFiles.getSelectedValue();
        if (selected == null || selected.equals("..")) return;
        boolean isDir = selected.endsWith("/");
        String remoteName = isDir ? selected.substring(0, selected.length()-1) : selected;

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File saveDir = chooser.getSelectedFile();
            new Thread(() -> {
                boolean ok;
                try {
                    if (isDir) ok = ftpCore.downloadDirectory(remoteName, new File(saveDir, remoteName));
                    else ok = ftpCore.downloadFile(remoteName, new File(saveDir, remoteName));
                    
                    SwingUtilities.invokeLater(() -> {
                         if(ok) JOptionPane.showMessageDialog(this, "Tải xuống hoàn tất!");
                         else JOptionPane.showMessageDialog(this, "Tải xuống lỗi: " + ftpCore.getLastError());
                    });
                } catch (Exception ex) { ex.printStackTrace(); }
            }).start();
        }
    }

    private void actionDelete() {
        String selected = listFiles.getSelectedValue();
        if (selected == null || selected.equals("..")) return;
        int confirm = JOptionPane.showConfirmDialog(this, "Xóa " + selected + "?");
        if (confirm != JOptionPane.YES_OPTION) return;

        boolean isDir = selected.endsWith("/");
        String name = isDir ? selected.substring(0, selected.length()-1) : selected;

        new Thread(() -> {
            if (ftpCore.deleteFile(name)) {
                SwingUtilities.invokeLater(() -> {
                    refreshFileList();
                    JOptionPane.showMessageDialog(this, "Đã xóa thành công!");
                });
            } else {
                SwingUtilities.invokeLater(() -> 
                    JOptionPane.showMessageDialog(this, "Xóa thất bại (Folder phải rỗng): " + ftpCore.getLastError()));
            }
        }).start();
    }
    
    private void actionMkdir() {
        String name = JOptionPane.showInputDialog(this, "Tên thư mục mới:");
        if (name != null && !name.trim().isEmpty()) {
            if (ftpCore.makeDirectory(name)) refreshFileList();
            else JOptionPane.showMessageDialog(this, "Lỗi: " + ftpCore.getLastError());
        }
    }
    
    private void actionRename() {
        String selected = listFiles.getSelectedValue();
        if (selected == null || selected.equals("..")) return;
        boolean isDir = selected.endsWith("/");
        String oldName = isDir ? selected.substring(0, selected.length()-1) : selected;
        String newName = JOptionPane.showInputDialog(this, "Tên mới:", oldName);
        if (newName != null) {
            if (ftpCore.renameFile(oldName, newName)) refreshFileList();
            else JOptionPane.showMessageDialog(this, "Lỗi: " + ftpCore.getLastError());
        }
    }

    private void navigateTo(String dir) {
        if (ftpCore.changeDirectory(dir)) refreshFileList();
    }

    private void navigateUp() {
        if (ftpCore.changeDirectory("..")) refreshFileList();
    }

    private void createContextMenu() {
        contextMenu = new JPopupMenu();
        JMenuItem itemDown = new JMenuItem("Tải xuống");
        JMenuItem itemRen = new JMenuItem("Đổi tên");
        JMenuItem itemDel = new JMenuItem("Xóa");
        itemDown.addActionListener(e -> actionDownload());
        itemRen.addActionListener(e -> actionRename());
        itemDel.addActionListener(e -> actionDelete());
        contextMenu.add(itemDown);
        contextMenu.add(itemRen);
        contextMenu.addSeparator();
        contextMenu.add(itemDel);
    }

    class FileListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String text = (String) value;
            if (text.equals("..")) {
                label.setIcon(UIManager.getIcon("FileChooser.upFolderIcon"));
                label.setText(".. (Quay lại)");
            } else if (text.endsWith("/")) {
                label.setIcon(UIManager.getIcon("FileView.directoryIcon"));
                label.setText(text.substring(0, text.length()-1));
                label.setForeground(Color.BLUE);
            } else {
                label.setIcon(UIManager.getIcon("FileView.fileIcon"));
            }
            return label;
        }
    }
}