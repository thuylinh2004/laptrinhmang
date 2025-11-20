package ftpclient;

import javax.swing.*;
import java.awt.*;

public class RegisterPanel extends JPanel {
    private final JTextField txtUser;
    private final JPasswordField txtPass;
    private final JButton btnRegister;
    private final FTPClientCore client;
    private final FTPClientGUI parent;

    public RegisterPanel(FTPClientGUI parent, FTPClientCore client) {
        this.parent = parent;
        this.client = client;

        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Tiêu đề
        JLabel lblTitle = new JLabel("Đăng ký");
        lblTitle.setFont(new Font("Arial", Font.BOLD, 22));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        add(lblTitle, gbc);

        // Tên người dùng
        JLabel lblUser = new JLabel("Tên người dùng:");
        lblUser.setFont(new Font("Arial", Font.PLAIN, 16));
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.LINE_END;
        add(lblUser, gbc);

        txtUser = new JTextField(15);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.LINE_START;
        add(txtUser, gbc);

        // Mật khẩu
        JLabel lblPass = new JLabel("Mật khẩu:");
        lblPass.setFont(new Font("Arial", Font.PLAIN, 16));
        gbc.gridx = 0; gbc.gridy = 2; gbc.anchor = GridBagConstraints.LINE_END;
        add(lblPass, gbc);

        txtPass = new JPasswordField(15);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.LINE_START;
        add(txtPass, gbc);

        // Nút Đăng ký
        btnRegister = new JButton("Đăng ký");
        btnRegister.setFont(new Font("Arial", Font.BOLD, 14));
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        add(btnRegister, gbc);

        // Nút Hủy | Quay lại (gộp 1 nút, 2 màu)
        JPanel linkPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        linkPanel.setOpaque(false);

        JButton btnCancelBack = new JButton(
                "<html><span style='color:#d32f2f'>Hủy</span>  |  <span style='color:#1565c0'>Quay lại</span></html>");
        btnCancelBack.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnCancelBack.setContentAreaFilled(false);
        btnCancelBack.setBorderPainted(false);
        btnCancelBack.setFocusPainted(false);
        btnCancelBack.setOpaque(false);

        linkPanel.add(btnCancelBack);

        gbc.gridy = 4;
        add(linkPanel, gbc);

        // Xử lý sự kiện Đăng ký
        btnRegister.addActionListener(e -> {
            String user = txtUser.getText().trim();
            String pass = new String(txtPass.getPassword()).trim();
            if (user.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ Tên người dùng và Mật khẩu!");
                return;
            }
            boolean ok = client.register(user, pass);
            if (ok) {
                JOptionPane.showMessageDialog(this, "Đăng ký thành công! Vui lòng đăng nhập.");
                parent.showLoginPanel();
            } else {
                JOptionPane.showMessageDialog(this, "Đăng ký thất bại! Tên người dùng có thể đã tồn tại.");
            }
        });

        // Sự kiện cho nút Hủy | Quay lại (xóa nhập và quay lại màn đăng nhập)
        btnCancelBack.addActionListener(e -> {
            txtUser.setText("");
            txtPass.setText("");
            parent.showLoginPanel();
        });
    }
}
