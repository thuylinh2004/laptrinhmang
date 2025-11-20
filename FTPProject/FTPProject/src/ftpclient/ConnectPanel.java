package ftpclient;

import javax.swing.*;
import java.awt.*;

public class ConnectPanel extends JPanel {
    private final JTextField txtHost, txtPort;
    private final JButton btnConnect;
    private final FTPClientGUI parent;
    private final FTPClientCore client;

    public ConnectPanel(FTPClientGUI parent, FTPClientCore client) {
        this.parent = parent;
        this.client = client;

        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Tiêu đề
        JLabel lblTitle = new JLabel("Kết nối đến Server");
        lblTitle.setFont(new Font("Arial", Font.BOLD, 24));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        add(lblTitle, gbc);

        // Địa chỉ IP
        JLabel lblHost = new JLabel("Địa chỉ IP:");
        lblHost.setFont(new Font("Arial", Font.PLAIN, 16));
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.LINE_END;
        add(lblHost, gbc);

        txtHost = new JTextField(15);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.LINE_START;
        add(txtHost, gbc);

        // Port
        JLabel lblPort = new JLabel("Port:");
        lblPort.setFont(new Font("Arial", Font.PLAIN, 16));
        gbc.gridx = 0; gbc.gridy = 2; gbc.anchor = GridBagConstraints.LINE_END;
        add(lblPort, gbc);

        txtPort = new JTextField(15);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.LINE_START;
        add(txtPort, gbc);

        // Nút Kết nối
        btnConnect = new JButton("Kết nối");
        btnConnect.setFont(new Font("Arial", Font.BOLD, 14));
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        add(btnConnect, gbc);

        // Xử lý sự kiện
        btnConnect.addActionListener(e -> {
            String host = txtHost.getText().trim();
            if (host.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập địa chỉ IP!");
                return;
            }
            int port;
            try {
                port = Integer.parseInt(txtPort.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Port phải là số nguyên!");
                return;
            }
            btnConnect.setEnabled(false);
            boolean ok = client.connect(host, port);
            if (ok) {
                JOptionPane.showMessageDialog(this, "Kết nối thành công!");
                parent.showLoginPanel();
            } else {
                JOptionPane.showMessageDialog(this, "Kết nối thất bại!");
            }
            btnConnect.setEnabled(true);
        });
    }
}
