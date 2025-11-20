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
        System.out.println("=== GIAO DIỆN FILE BROWSER V3.0 ===");

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));
        JLabel title = new JLabel("  Quản lý tập tin");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        header.add(title, BorderLayout.WEST);
        
        // Panel chứa các nút chức năng thoát
        JPanel pnlActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // Nút 1: Đăng xuất (Về màn hình Login, giữ kết nối)
        JButton btnLogout = new JButton("Đăng xuất");
        btnLogout.addActionListener(e -> { 
            ftpCore.logout(); 
            parentGUI.showLoginPanel(); 
        });
        
        // Nút 2: Ngắt kết nối (Về màn hình Connect, đóng socket)
        JButton btnDisconnect = new JButton("Ngắt kết nối");
        btnDisconnect.setBackground(new Color(255, 200, 200));
        btnDisconnect.addActionListener(e -> {
            ftpCore.disconnect();
            parentGUI.showConnectPanel();
        });
        
        pnlActions.add(btnLogout);
        pnlActions.add(btnDisconnect);
        header.add(pnlActions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

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
                    String selected = getSelectedFileName();
                    if (selected == null) return;
                    if (selected.equals("..")) navigateUp();
                    else if (isDirectory(listFiles.getSelectedValue())) navigateTo(selected);
                }
                if (SwingUtilities.isRightMouseButton(e)) {
                    int index = listFiles.locationToIndex(e.getPoint());
                    listFiles.setSelectedIndex(index);
                    contextMenu.show(listFiles, e.getX(), e.getY());
                }
            }
        });
        listFiles.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) previewSelectedFile(); });

        splitPane.setLeftComponent(new JScrollPane(listFiles));
        textPreview = new JTextArea();
        textPreview.setEditable(false);
        JScrollPane previewScroll = new JScrollPane(textPreview);
        previewScroll.setBorder(BorderFactory.createTitledBorder("Xem trước"));
        splitPane.setRightComponent(previewScroll);
        add(splitPane, BorderLayout.CENTER);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnUpload = new JButton("Tải lên");
        JButton btnDownload = new JButton("Tải xuống");
        JButton btnMkdir = new JButton("Tạo thư mục");
        JButton btnRename = new JButton("Đổi tên");
        JButton btnDelete = new JButton("Xóa");
        JButton btnRefresh = new JButton("Làm mới");

        btnUpload.addActionListener(e -> actionUpload());
        btnDownload.addActionListener(e -> actionDownload());
        btnMkdir.addActionListener(e -> actionMkdir());
        btnRename.addActionListener(e -> actionRename());
        btnDelete.addActionListener(e -> actionDelete());
        btnRefresh.addActionListener(e -> refreshFileList());

        toolbar.add(btnUpload); toolbar.add(btnDownload); toolbar.add(btnMkdir);
        toolbar.add(btnRename); toolbar.add(btnDelete); toolbar.add(btnRefresh);
        add(toolbar, BorderLayout.SOUTH);
    }

    public void refreshFileList() {
        new Thread(() -> {
            currentPath = ftpCore.getWorkingDirectory();
            List<String> files = ftpCore.listFiles("");
            SwingUtilities.invokeLater(() -> {
                listModel.clear();
                textPreview.setText("");
                if (currentPath != null && !currentPath.equals("/") && !currentPath.isEmpty()) listModel.addElement("..");
                for (String line : files) listModel.addElement(line);
            });
        }).start();
    }
    
    private String getSelectedFileName() {
        String raw = listFiles.getSelectedValue();
        if (raw == null) return null;
        if (raw.equals("..")) return "..";
        return raw.split("\\|")[0];
    }

    private boolean isDirectory(String raw) {
        return raw != null && (raw.contains("Thư mục") || raw.equals(".."));
    }

    private void previewSelectedFile() {
        String name = getSelectedFileName();
        if (name == null || name.equals("..") || isDirectory(listFiles.getSelectedValue())) {
            textPreview.setText(""); return;
        }
        new Thread(() -> {
            try {
                File tmp = File.createTempFile("ftp", ".txt");
                if (ftpCore.downloadFile(name, tmp)) {
                    byte[] b = java.nio.file.Files.readAllBytes(tmp.toPath());
                    String c = new String(b, 0, Math.min(b.length, 2000));
                    SwingUtilities.invokeLater(() -> { textPreview.setText(c); textPreview.setCaretPosition(0); });
                } else SwingUtilities.invokeLater(() -> textPreview.setText("[Không thể xem]"));
            } catch (Exception e) { }
        }).start();
    }

    private void actionUpload() {
        JFileChooser c = new JFileChooser();
        c.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        if (c.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = c.getSelectedFile();
            new Thread(() -> {
                try {
                    boolean ok = f.isDirectory() ? ftpCore.uploadDirectory(f, f.getName()) : ftpCore.uploadFile(f, f.getName());
                    SwingUtilities.invokeLater(() -> { if(ok) { JOptionPane.showMessageDialog(this, "Xong!"); refreshFileList(); } else JOptionPane.showMessageDialog(this, "Lỗi!"); });
                } catch(Exception e) {}
            }).start();
        }
    }

    private void actionDownload() {
        String name = getSelectedFileName();
        if (name == null || name.equals("..")) return;
        boolean isDir = isDirectory(listFiles.getSelectedValue());
        
        JFileChooser c = new JFileChooser();
        c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (c.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File dir = c.getSelectedFile();
            new Thread(() -> {
                try {
                    boolean ok = isDir ? ftpCore.downloadDirectory(name, new File(dir, name)) : ftpCore.downloadFile(name, new File(dir, name));
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, ok ? "Tải xong!" : "Lỗi tải!"));
                } catch(Exception e) {}
            }).start();
        }
    }

    private void actionDelete() {
        String name = getSelectedFileName();
        if (name == null || name.equals("..")) return;
        if (JOptionPane.showConfirmDialog(this, "Xóa " + name + "?") == JOptionPane.YES_OPTION) {
            new Thread(() -> {
                if(ftpCore.deleteFile(name)) SwingUtilities.invokeLater(() -> { refreshFileList(); JOptionPane.showMessageDialog(this, "Đã xóa"); });
                else SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Lỗi xóa (Thư mục có thể không rỗng)"));
            }).start();
        }
    }
    
    private void actionMkdir() {
        String n = JOptionPane.showInputDialog(this, "Tên:");
        if (n!=null) { if(ftpCore.makeDirectory(n)) refreshFileList(); else JOptionPane.showMessageDialog(this, "Lỗi tạo folder"); }
    }
    
    private void actionRename() {
        String old = getSelectedFileName();
        if (old==null || old.equals("..")) return;
        String n = JOptionPane.showInputDialog(this, "Tên mới:", old);
        if (n!=null) { if(ftpCore.renameFile(old, n)) refreshFileList(); else JOptionPane.showMessageDialog(this, "Lỗi đổi tên"); }
    }

    private void navigateTo(String d) { if(ftpCore.changeDirectory(d)) refreshFileList(); }
    private void navigateUp() { if(ftpCore.changeDirectory("..")) refreshFileList(); }
    private void createContextMenu() {
        contextMenu = new JPopupMenu();
        JMenuItem down = new JMenuItem("Tải xuống");
        down.addActionListener(e -> actionDownload());
        contextMenu.add(down);
    }

    class FileListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String text = (String) value;
            if (text.equals("..")) {
                label.setIcon(UIManager.getIcon("FileChooser.upFolderIcon"));
                label.setText(".. (Quay lại)");
            } else {
                String[] parts = text.split("\\|");
                if (parts.length >= 1) {
                    label.setText(parts[0]); 
                    if (text.contains("Thư mục")) {
                        label.setIcon(UIManager.getIcon("FileView.directoryIcon"));
                        label.setForeground(Color.BLUE);
                    } else {
                        label.setIcon(UIManager.getIcon("FileView.fileIcon"));
                        if (parts.length >= 2) label.setText(parts[0] + " (" + parts[1] + " bytes)");
                    }
                }
            }
            return label;
        }
    }
}