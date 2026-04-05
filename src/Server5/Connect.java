package Server5; // nhớ đổi lại khi copy sang Server khác

import java.io.*;
import java.net.Socket;

public class Connect extends Thread {

    String destination;
    int port;
    String serverName;
    Socket connection;
    OutputStream out;
    BufferedReader in;

    public Connect(String destination, int port, String serverName) {
        this.destination = destination;
        this.port = port;
        this.serverName = serverName;
    }

    public void connect() {
        try {
            connection = new Socket(destination, port);

            // ✅ FIX UTF-8
            in = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "UTF-8")
            );

            out = connection.getOutputStream();

            System.out.println("Đã kết nối tới " + serverName + " qua cổng " + port + ".");
        } catch (Exception e) {
            System.out.println("Lỗi kết nối: " + e.getMessage());
        }
    }

    public void requestServer(String message) {
        try {
            System.out.println("Đang gửi thông điệp tới: " + serverName);

            // tránh lỗi substring nếu không có %%
            if (message.contains("%%")) {
                System.out.println("Nội dung: " + message.substring(message.lastIndexOf("%%") + 2));
            } else {
                System.out.println("Nội dung: " + message);
            }

            // ✅ GỬI UTF-8 (QUAN TRỌNG NHẤT)
            out.write((message + "\n").getBytes("UTF-8"));
            out.flush();

            System.out.println();

        } catch (Exception e) {
            System.out.println("Lỗi gửi tin: " + e.getMessage());
        }
    }

    public void shutdown() {
        try {
            if (connection != null) connection.close();
        } catch (IOException ex) {
            System.out.println("Lỗi IO khi đóng socket!");
        }
    }

    // ⚠️ Thread phải override run()
    @Override
    public void run() {
        // không dùng run(String s) nữa
    }

    // 👉 gọi hàm này thay cho run(String)
    public void send(String message) {
        connect();
        requestServer(message);
        shutdown();
    }
}