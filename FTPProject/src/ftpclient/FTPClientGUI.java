package ftpclient;

import javax.swing.*;
import java.awt.*;

public class FTPClientGUI extends JFrame {
    private final FTPClientCore client;
    private ConnectPanel connectPanel;
    private LoginPanel loginPanel;
    private RegisterPanel registerPanel;
    private FileBrowserPanel fileBrowserPanel;

    public FTPClientGUI() {
        setTitle("FTP Client GUI");
        setSize(700, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        client = new FTPClientCore();

        connectPanel = new ConnectPanel(this, client);
        loginPanel = new LoginPanel(client, this);
        registerPanel = new RegisterPanel(this, client);
        fileBrowserPanel = new FileBrowserPanel(client, this);

        add(connectPanel, BorderLayout.CENTER);
    }

    public void showConnectPanel() {
        getContentPane().removeAll();
        add(connectPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    public void showLoginPanel() {
        getContentPane().removeAll();
        add(loginPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    public void showRegisterPanel() {
        getContentPane().removeAll();
        add(registerPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    public void showFileBrowser() {
        getContentPane().removeAll();
        add(fileBrowserPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
        fileBrowserPanel.refreshFileList();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FTPClientGUI gui = new FTPClientGUI();
            gui.setVisible(true);
        });
    }
}