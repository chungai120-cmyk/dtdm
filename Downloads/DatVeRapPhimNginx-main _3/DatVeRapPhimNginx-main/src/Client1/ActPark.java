package Client1;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.Date;
import javax.swing.*;

public class ActPark extends JPanel {

    private JTextField txt_esp, txt_num, txt_time, txt_type;
    private JTextArea txa_info;

    private Choice opt_stg, opt_clr, opt_sv;

    private Socket client;
    private DataOutputStream out;
    private BufferedReader in;

    private String act, lamportS = "";

    public ActPark() {

        setLayout(null);

        JLabel lb_sv = new JLabel("Server:");
        lb_sv.setBounds(10, 20, 50, 25);

        JLabel lb_stg = new JLabel("Phòng");
        lb_stg.setBounds(60, 50, 50, 25);

        JLabel lb_esp = new JLabel("Số ghế");
        lb_esp.setBounds(210, 50, 60, 25);

        JLabel lb_num = new JLabel("Tên Khách");
        lb_num.setBounds(10, 80, 80, 25);

        JLabel lb_type = new JLabel("Loại vé");
        lb_type.setBounds(10, 110, 80, 25);

        JLabel lb_clr = new JLabel("Thanh toán");
        lb_clr.setBounds(210, 110, 80, 25);

        JLabel lb_time = new JLabel("Giờ đặt");
        lb_time.setBounds(10, 170, 70, 25);

        JLabel lb_info = new JLabel("Thông tin");
        lb_info.setBounds(165, 200, 70, 25);

        // ===== CHOICE =====
        opt_stg = new Choice();
        opt_stg.setBounds(110, 50, 90, 30);
        opt_stg.add("A");
        opt_stg.add("B");
        opt_stg.add("C");

        opt_sv = new Choice();
        opt_sv.setBounds(120, 20, 120, 25);
        opt_sv.add("Server 1");
        opt_sv.add("Server 2");
        opt_sv.add("Server 3");
        opt_sv.add("Server 4");
        opt_sv.add("Server 5");

        opt_clr = new Choice();
        opt_clr.setBounds(210, 140, 160, 25);
        opt_clr.add("Tiền mặt");
        opt_clr.add("Momo");

        // ===== TEXT =====
        txt_esp = new JTextField();
        txt_esp.setBounds(270, 50, 100, 25);

        txt_num = new JTextField();
        txt_num.setBounds(120, 80, 160, 25);

        txt_type = new JTextField();
        txt_type.setBounds(10, 140, 170, 25);

        txt_time = new JTextField();
        txt_time.setBounds(120, 170, 160, 25);

        txa_info = new JTextArea();
        JScrollPane jsp = new JScrollPane(txa_info);
        jsp.setBounds(10, 230, 370, 165);

        // ===== TIME AUTO =====
        new Thread(() -> {
            while (true) {
                try {
                    String time = new Date().toString(); // 🔥 bỏ toLocaleString lỗi thời
                    SwingUtilities.invokeLater(() -> txt_time.setText(time));
                    Thread.sleep(1000);
                } catch (Exception ignored) {
                }
            }
        }).start();

        // ===== BUTTON =====
        JButton bt_in = new JButton("Đặt vé");
        bt_in.setBounds(75, 400, 80, 25);
        bt_in.addActionListener(e -> {
            act = "SET";
            new Thread(this::runClient).start();
        });

        JButton bt_out = new JButton("Hủy vé");
        bt_out.setBounds(165, 400, 80, 25);
        bt_out.addActionListener(e -> {
            act = "DEL";
            new Thread(this::runClient).start();
        });

        JButton bt_reset = new JButton("Làm mới");
        bt_reset.setBounds(255, 400, 90, 25);
        bt_reset.addActionListener(e -> {
            txt_esp.setText("");
            txt_num.setText("");
            txt_type.setText("");
            txa_info.setText("");
        });

        add(lb_sv);
        add(opt_sv);
        add(lb_stg);
        add(lb_esp);
        add(lb_type);
        add(lb_num);
        add(lb_clr);
        add(lb_time);
        add(lb_info);
        add(opt_stg);
        add(txt_esp);
        add(txt_type);
        add(txt_num);
        add(txt_time);
        add(opt_clr);
        add(jsp);
        add(bt_in);
        add(bt_out);
        add(bt_reset);
    }

    public String getMessage() {
        return opt_stg.getSelectedItem()
                + txt_esp.getText() + "|"
                + txt_num.getText() + "|"
                + txt_type.getText() + "|"
                + opt_clr.getSelectedItem() + "|"
                + txt_time.getText() + "|"
                + act;
    }

    public void runClient() {
        String sv = opt_sv.getSelectedItem();
        String ip = "127.0.0.1"; // Mặc định
        int port = 2001;

        // Chỉnh IP theo đúng list Google Cloud
        if (sv.equals("Server 1")) {
            ip = "136.110.4.186";
            port = 2001;
        } else if (sv.equals("Server 2")) {
            ip = "34.28.238.63";
            port = 2002;
        } else if (sv.equals("Server 3")) {
            ip = "34.55.107.207";
            port = 2003;
        } else if (sv.equals("Server 4")) {
            ip = "34.71.156.133";
            port = 2004;
        } else if (sv.equals("Server 5")) {
            ip = "104.154.64.145";
            port = 2005;
        }
        if (!sv.equals(" ")) {
            connect2Server(ip, port); // Gọi IP thật thay vì 127.0.0.1
            shutdown();
        } else {
            txa_info.append("Vui lòng chọn Server!\n");
        }
    }

    public void connect2Server(String host, int port) {
        try {
            client = new Socket(host, port);
            client.setSoTimeout(3000);

            // ✅ NHẬN UTF-8
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(client.getInputStream(), "UTF-8"));

            // ✅ GỬI UTF-8 (QUAN TRỌNG)
            BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(client.getOutputStream(), "UTF-8"));

            SwingUtilities.invokeLater(() -> txa_info.append("Đã kết nối server " + port + "\n"));

            // ❗ KHÔNG thêm \n ở đây
            String fullMsg = "@$0|00000|" + lamportS +
                    "|Client|Send|1|123$$" + getMessage() + "$@";

            // ✅ gửi đúng chuẩn
            out.write(fullMsg);
            out.newLine(); // xuống dòng đúng 1 lần
            out.flush();

            // ✅ nhận phản hồi
            String res = in.readLine();

            SwingUtilities.invokeLater(() -> txa_info.append("Server: " + res + "\n\n"));

        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> txa_info.append("Lỗi kết nối server!\n"));
        }
    }

    public void shutdown() {
        try {
            if (client != null)
                client.close();
        } catch (Exception ignored) {
        }
    }
}
