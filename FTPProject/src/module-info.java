module FTPProject {
    requires org.apache.commons.net;
    requires java.desktop;  // For AWT and Swing
    requires java.sql;      // For database operations
    exports ftpclient;
}
