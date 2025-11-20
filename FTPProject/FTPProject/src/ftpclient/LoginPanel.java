package ftpclient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class LoginPanel extends JPanel {
    private final JTextField txtUser;
    private final JPasswordField txtPass;
    private final JButton btnLogin;
    private final FTPClientCore client;
    private final FTPClientGUI parent;

    public LoginPanel(FTPClientCore client, FTPClientGUI parent) {
        this.client = client;
        this.parent = parent;

        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Tiêu đề
        JLabel lblTitle = new JLabel("Đăng nhập");
        lblTitle.setFont(new Font("Arial", Font.BOLD, 24));
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

        // Nút đăng nhập
        btnLogin = new JButton("Đăng nhập");
        btnLogin.setFont(new Font("Arial", Font.BOLD, 14));
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        add(btnLogin, gbc);

        // Link Đăng ký
        JLabel lblRegister = new JLabel("Chưa có tài khoản? Đăng ký");
        lblRegister.setForeground(Color.BLUE.darker());
        lblRegister.setCursor(new Cursor(Cursor.HAND_CURSOR));
        lblRegister.setFont(new Font("Arial", Font.PLAIN, 13));
        gbc.gridy = 4;
        add(lblRegister, gbc);

        // Sự kiện đăng nhập
        btnLogin.addActionListener(e -> {
            String user = txtUser.getText().trim();
            String pass = new String(txtPass.getPassword()).trim();
            if (user.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ Tên người dùng và Mật khẩu!");
                return;
            }
            boolean ok = client.login(user, pass);
            if (ok) {
                JOptionPane.showMessageDialog(this, "Đăng nhập thành công!");
                parent.showFileBrowser();
            } else {
                JOptionPane.showMessageDialog(this, "Đăng nhập thất bại!");
            }
        });

        // Sự kiện click Đăng ký
        lblRegister.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                parent.showRegisterPanel();
            }
        });
    }
}
