package Server2;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class Connect extends Thread {

    String destination;
    int port;
    String serverName;
    Socket connection;
    DataOutputStream out;
    BufferedReader in;

    public Connect(String destination, int port, String serverName) {
        this.destination = destination;
        this.port = port;
        this.serverName = serverName;
    }

    public void connect() {
        try {
            connection = new Socket(destination, port);

            // 🔥 FIX UTF-8
            in = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "UTF-8")
            );
            out = new DataOutputStream(connection.getOutputStream());

            System.out.println("Đã kết nối tới " + serverName + " qua cổng " + port + ".");

        } catch (Exception e) {
            System.out.println("Lỗi kết nối: " + e);
        }
    }

    public void requestServer(String message) {
        try {
            System.out.println("Đang gửi thông điệp tới: " + serverName);

            // tránh lỗi substring
            if (message.contains("%%")) {
                System.out.println("Nội dung: " + message.substring(message.lastIndexOf("%%") + 2));
            } else {
                System.out.println("Nội dung: " + message);
            }

            System.out.flush();

            // 🔥 FIX UTF-8 + newline chuẩn
            out.write((message + "\n").getBytes("UTF-8"));
            out.flush();

            System.out.println();

        } catch (Exception e) {
            System.out.println("Lỗi gửi tin: " + e);
        }
    }

    public void shutdown() {
        try {
            if (connection != null) connection.close();
        } catch (IOException ex) {
            System.out.println("Lỗi IO khi đóng socket!");
        }
    }

    public void run(String s) {
        requestServer(s);
        shutdown();
    }
}