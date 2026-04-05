/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Client1;

import java.io.*;
import java.net.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.AbstractTableModel;

public class View extends JPanel {

    private boolean DEBUG = false;
    private JTable table;
    private JButton bt_view;
    private JTextArea display;
    private JScrollPane pane;
    Socket client, connection;
    ServerSocket server;
    DataOutputStream out;
    BufferedReader in;

    public View() {
        setLayout(null);

        display = new JTextArea();
        display.setBounds(new Rectangle(5, 5, 390, 360));
        pane = new JScrollPane();
        pane.setViewportView(display);
        pane.setBounds(new Rectangle(5, 5, 390, 360));

        // Đổi tên nút thành Xem Sơ Đồ
        bt_view = new JButton("Xem Sơ Đồ");
        bt_view.setBounds(new Rectangle(155, 370, 110, 25));
        bt_view.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(java.awt.event.ActionEvent e) {
                display.setText("");
                runClient();
            }
        });
        add(pane);
        add(bt_view);

    }

    public void runClient() {
        connect2Server("127.0.0.1", 2001);
        shutdown();
    }

    public void connect2Server(String destination, int port) {
        try {
            String message = "|||||VIEW";
            client = new Socket(destination, port);
            in = new BufferedReader(
                    new InputStreamReader(client.getInputStream(), "UTF-8"));

            OutputStream out = client.getOutputStream();

            out.write(("@$0|000|145|Client|Send|1|123$$" + message + "$@\n").getBytes("UTF-8"));
            out.flush();
            String inLine = in.readLine();
            int k = 0, j = 0;

            // Đã đổi Tiêu đề cột thành Rạp Phim
            display.append("Phòng+Ghế  |   Tên Khách   |   Loại vé   |  Thanh toán  |    Giờ đặt \n");
            display.append(
                    "------------------------------------------------------------------------------------------------\n");

            while (!inLine.equalsIgnoreCase("")) {
                for (int i = 0; i < 5; i++) {
                    try {
                        k = inLine.indexOf("|");
                    } catch (Exception ex) {
                        k = 0;
                    }
                    String vt = inLine.substring(0, k);
                    display.append(vt + "      |   ");
                    k += 1;
                    inLine = inLine.substring(k);
                }
                display.append("\n");
                j++;
            }
            // Đã xóa dòng table.setValueAt bị lỗi ngầm ở code cũ để ứng dụng chạy mượt hơn
            System.out.print(inLine);

        } catch (Exception e) {
            display.append("Lỗi: Không kết nối được với Server để lấy dữ liệu.\n");
        }
    }

    public void shutdown() {
        try {
            client.close();
        } catch (IOException ex) {
        }
    }
}