/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Server3;

import java.net.*;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.util.Hashtable;

public class Server3 extends JFrame {

    private JFrame mainFrm;
    private JPanel jCPane;
    private JScrollPane scroll;
    static JTextArea display;
    int counter;
    ObjectOutputStream output;
    ObjectInputStream input;
    ServerSocket server;
    Socket client, connection;
    String serverName;
    String type;
    int pos;
    RountingTable rount;
    int currentCircle;
    static String MESSAGE, replyMessage;
    Hashtable hash;
    DataOutputStream out;
    BufferedReader in;
    Database db1, db;
    ProcessData data, dt;

    public Server3() {
        JFrame mainFrm = new JFrame("Hệ Thống Rạp Phim - Server 3");
        mainFrm.setSize(420, 420);

        jCPane = new JPanel();
        jCPane.setLayout(null);

        scroll = new JScrollPane();
        scroll.setBounds(new Rectangle());
        display = new JTextArea();
        display.setBounds(new Rectangle(10, 10, 390, 345));
        scroll.setViewportView(display);
        scroll.setBounds(new Rectangle(10, 10, 390, 345));
        // Đổi tên nhãn thành Rạp Phim
        scroll.setBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.blue, 2), "MÁY CHỦ 3 - RẠP PHIM",
                        TitledBorder.CENTER, TitledBorder.CENTER, new Font("Dialog", Font.BOLD, 12), Color.blue));

        jCPane.add(scroll);
        mainFrm.add(jCPane);
        mainFrm.setVisible(true);
        mainFrm.setResizable(false);
    }

    public static class sv3 implements Runnable {

        int counter;
        ObjectOutputStream output;
        ObjectInputStream input;
        ServerSocket server;
        Socket client, connection;
        String serverName;
        String type;
        int pos;
        RountingTable rount;
        int currentCircle;
        static String MESSAGE, replyMessage;
        Hashtable hash;
        DataOutputStream out;
        BufferedReader in;
        Database db1, db;
        ProcessData data, dt;
        int lamportSave;

        sv3() {
            new Thread(this, "sv3").start();
        }

        public void handler(Socket newSocket, String serverName, int pos, int curr, Hashtable hash) {
            client = newSocket;
            this.serverName = serverName;
            rount = new RountingTable();
            this.pos = pos;
            this.currentCircle = curr;
            MESSAGE = "";
            this.hash = hash;
        }

        public void runServer() {
            try {
                String destName = client.getInetAddress().getHostName();
                int destPort = client.getPort();
                display.append("Chấp nhận kết nối từ " + destName + " tại cổng " + destPort + ".\n");
                BufferedReader inStream = new BufferedReader(new InputStreamReader(client.getInputStream()));
                // 👉 thêm dòng này để đọc dữ liệu client gửi
                // String request = inStream.readLine();

                // 👉 tạo reply (bạn có thể đổi nội dung tùy ý)
                String reply = "Đã thực hiện thành công.";

                OutputStream outStream = client.getOutputStream();
                // outStream.write((reply + "\n").getBytes("UTF-8"));
                outStream.flush();

                boolean finished = false;
                {
                    // lay goi tin nhan duoc
                    String inLine = inStream.readLine();
                    if (inLine != null) {
                        display.append("Nhận raw: " + inLine + "\n");
                    }
                    MessageProcess re = new MessageProcess(inLine);

                    String st = re.getStart();
                    String je = re.getJeton();
                    String lamport = re.getLamport();
                    String name = re.getServerName();
                    String type = re.getType();
                    String action = re.getAction();
                    String circle = re.getNumCircle();
                    String message = re.getMessage();
                    MESSAGE = message;
                    String jeton;
                    display.append("Thông tin nhận được :" + "\n" + "start: " + st + "\n" + "jeton: " + je + "\n"
                            + "lamport: " + lamport + "\n" + "servername: " + name + "\n"
                            + "type: " + type + "\n" + "action: " + action + "\n" + "vòng đk: " + circle + "\n"
                            + "thông điệp: " + message + "\n");
                    int start = Integer.parseInt(st);
                    int act = Integer.parseInt(action);
                    String t = "", rev;

                    if (act == 4) {
                        rev = je;
                        int po = pos + 9;
                        try {
                            rev = je.substring(1, po);
                        } catch (Exception ex) {
                        }
                        t = rev;
                    } else if (act == 3) {
                        try {
                            t = je.substring(0, pos - 1);
                        } catch (Exception ex) {
                        }

                        jeton = je;
                        t += "1";
                        try {
                            t += jeton.substring(pos);
                        } catch (Exception ex) {
                        }
                    } else if (act == 2) {
                        try {
                            t = je.substring(0, pos - 1);
                        } catch (Exception ex) {
                        }

                        jeton = je;
                        t += "1";
                        try {
                            t += jeton.substring(pos);
                        } catch (Exception ex) {
                        }
                    } else if (act == 1) {
                        try {
                            t = je.substring(0, pos - 1);
                        } catch (Exception ex) {
                        }

                        jeton = je;
                        t += "1";
                        try {
                            t += jeton.substring(pos);
                        } catch (Exception ex) {
                        }
                    }
                    int vt = pos;
                    if (vt > rount.max - 1) {
                        vt = 0;
                    }

                    // xu ly thong tin Synchronymed va ket thuc vong tron ao

                    if (type.equals("Synchronymed") && (start == 4)) {
                        display.append("Hoàn tất giao dịch đặt vé. Kết thúc vòng tròn ảo.\n\n");
                    }

                    // xu ly thong tin updated va quay vong

                    if (type.equals("Updated") && (start == 4)) {
                        int stt = start;
                        display.append("Kết thúc quá trình cập nhật, kiểm tra đồng bộ hóa TT và Quay vòng ngược.\n\n");
                        stt = 1;
                        act += 1;
                        try {
                            int tam = pos - 2;
                            if (tam < 0) {
                                tam = 2;
                            }
                            if (t.charAt(tam) == '0') {
                                display.append(
                                        "\nServer" + (tam + 1) + " bị sự cố do jeton nhận được là: " + t + ".\n");
                                tam--;
                            }
                            if (tam < 0) {
                                tam = 2;
                            }
                            Connect co = new Connect(rount.table[tam].destination, rount.table[tam].port,
                                    rount.table[tam].name);
                            co.connect();
                            String replyServerMessage = "@$" + stt + "|" + t + "|" + lamport + "|"
                                    + rount.table[pos - 1].name + "|" + "Synchronymed" + "|" + act + "|" + circle + "$$"
                                    + message + "$@";
                            co.requestServer(replyServerMessage);
                            co.shutdown();
                        } catch (Exception Ex) {
                        }

                    }

                    // xu ly thong tin temped va quay vong

                    if (type.equals("Temped") && (start == 4)) {
                        int stt = start;
                        display.append("Kết thúc tạo bảng tạm, cập nhật CSDL chính Quay vòng ngược.\n\n");
                        stt = 1;
                        act += 1;
                        try {
                            Connect co = new Connect(rount.table[vt].destination, rount.table[vt].port,
                                    rount.table[vt].name);
                            co.connect();
                            co.requestServer("@$" + stt + "|" + t + "|" + lamport + "|" + rount.table[pos - 1].name
                                    + "|" + "Updated" + "|" + act + "|" + circle + "$$" + message + "$@");
                            co.shutdown();
                        } // bi su co
                        catch (Exception ex) {
                            display.append("\n" + rount.table[vt].name + ": bị sự cố, hiện không liên lạc được.\n\n");
                            vt++;
                            if (vt > rount.max - 1) {
                                vt = 0;
                            }

                            Connect con = new Connect(rount.table[vt].destination, rount.table[vt].port,
                                    rount.table[vt].name);
                            con.connect();
                            con.requestServer("@$" + stt + "|" + t + "|" + lamport + "|" + rount.table[pos - 1].name
                                    + "|" + "Updated" + "|" + act + "|" + circle + "$$" + message + "$@");
                            con.shutdown();
                        }
                    }

                    // quay vong nguoc lai cua thong diep locked
                    if (type.equals("Locked") && (start == 4)) {
                        int stt = start;
                        display.append("Kết thúc khóa trường dữ liệu, tạo bảng tạm và Quay vòng ngược.\n\n");
                        stt = 1;
                        act += 1;
                        try {
                            int tam = pos - 2;
                            if (tam < 0) {
                                tam = 2;
                            }
                            if (t.charAt(tam) == '0') {
                                display.append(
                                        "\nServer" + (tam + 1) + " bị sự cố do jeton nhận được là: " + t + ".\n\n");
                                tam--;
                            }
                            if (tam < 0) {
                                tam = 2;
                            }
                            Connect co = new Connect(rount.table[tam].destination, rount.table[tam].port,
                                    rount.table[tam].name);
                            co.connect();
                            String replyServerMessage = "@$" + stt + "|" + t + "|" + lamport + "|"
                                    + rount.table[pos - 1].name + "|" + "Temped" + "|" + act + "|" + circle + "$$"
                                    + message + "$@";
                            co.requestServer(replyServerMessage);
                            co.shutdown();
                        } catch (Exception Ex) {
                        }
                    }

                    // xu ly thong tin tu client
                    if (start == 0) {
                        start++;
                        replyMessage = "Đã thực hiện thành công.";
                        db1 = new Database();
                        dt = new ProcessData(message);

                        if (message.endsWith("VIEW")) {
                            db1 = new Database();
                            replyMessage = db1.getData();
                        }

                        // Đã thay đổi thông báo lỗi cho phù hợp với Rạp phim
                        if ((message.endsWith("SET")) && (!db1.isEmpty(dt.getPos()))) {
                            replyMessage = "Lỗi: Ghế này đã có người đặt!";
                        }
                        if ((message.endsWith("DEL")) && (db1.isEmpty(dt.getPos()))) {
                            replyMessage = "Lỗi: Không tìm thấy vé tại ghế này!";
                        }

                        PrintWriter writer = new PrintWriter(new OutputStreamWriter(outStream, "UTF-8"), true);
                        writer.println(replyMessage);
                        display.append("Reply: " + replyMessage + "\n");
                        display.append("Thực hiện khóa trường DL. Chuyển thông điệp.\n\n");
                        try {
                            Connect co = new Connect(rount.table[vt].destination, rount.table[vt].port,
                                    rount.table[vt].name);
                            co.connect();
                            co.requestServer("@$" + start + "|" + t + "|" + lamport + "|" + rount.table[pos - 1].name
                                    + "|" + "Locked" + "|" + act + "|" + circle + "$$" + message + "$@");
                            co.shutdown();
                        } // bi su co
                        catch (Exception ex) {
                            display.append("\n" + rount.table[vt].name + ": bị sự cố, hiện không liên lạc được.\n\n");
                            vt++;
                            if (vt > rount.max - 1) {
                                vt = 0;
                            }

                            Connect con = new Connect(rount.table[vt].destination, rount.table[vt].port,
                                    rount.table[vt].name);
                            con.connect();
                            con.requestServer("@$" + start + "|" + t + "|" + lamport + "|" + rount.table[pos - 1].name
                                    + "|" + "Locked" + "|" + act + "|" + circle + "$$" + message + "$@");
                            con.shutdown();
                        }
                    }

                    // xu ly thong tin locked
                    if (type.equals("Locked") && (start != 4)) {
                        display.append("Chuyển thông điệp, thực hiện khóa trường DL.\n\n");
                        start++;
                        try {
                            Connect co = new Connect(rount.table[vt].destination, rount.table[vt].port,
                                    rount.table[vt].name);
                            co.connect();
                            co.requestServer("@$" + start + "|" + t + "|" + lamport + "|" + rount.table[pos - 1].name
                                    + "|" + "Locked" + "|" + action + "|" + circle + "$$" + message + "$@");
                            co.shutdown();
                        } // bi su co
                        catch (Exception ex) {
                            display.append("\n" + rount.table[vt].name + ": bị sự cố, hiện không liên lạc được.\n\n");
                            vt++;
                            if (vt > rount.max - 1) {
                                vt = 0;
                            }

                            Connect con = new Connect(rount.table[vt].destination, rount.table[vt].port,
                                    rount.table[vt].name);
                            con.connect();
                            con.requestServer("@$" + start + "|" + t + "|" + lamport + "|" + rount.table[pos - 1].name
                                    + "|" + type + "|" + action + "|" + circle + "$$" + message + "$@");
                            con.shutdown();
                        }
                    }

                    // Xu ly thong diep temp
                    if (type.equals("Temped") && (start != 4)) {
                        display.append("Chuyển thông điệp, thực hiện tạo bảng tạm CSDL.\n\n");
                        start++;
                        try {
                            int tam = pos - 2;
                            if (tam < 0) {
                                tam = 2;
                            }
                            if (t.charAt(tam) == '0') {
                                display.append(
                                        "\nServer" + (tam + 1) + " bị sự cố do jeton nhận được là: " + t + ".\n\n");
                                tam--;
                            }
                            if (tam < 0) {
                                tam = 2;
                            }
                            Connect co = new Connect(rount.table[tam].destination, rount.table[tam].port,
                                    rount.table[tam].name);
                            co.connect();
                            String replyServerMessage = "@$" + start + "|" + t + "|" + lamport + "|"
                                    + rount.table[pos - 1].name + "|" + type + "|" + act + "|" + circle + "$$" + message
                                    + "$@";
                            co.requestServer(replyServerMessage);
                            co.shutdown();
                        } catch (Exception Ex) {
                        }
                    }

                    // xu ly thong tin update
                    if (type.equals("Updated") && (start != 4)) {
                        display.append("Chuyển thông điệp, thực hiện cập nhật bảng chính CSDL.\n\n");
                        start++;
                        try {
                            Connect co = new Connect(rount.table[vt].destination, rount.table[vt].port,
                                    rount.table[vt].name);
                            co.connect();
                            co.requestServer("@$" + start + "|" + t + "|" + lamport + "|" + rount.table[pos - 1].name
                                    + "|" + type + "|" + action + "|" + circle + "$$" + message + "$@");
                            co.shutdown();
                        } // bi su co
                        catch (Exception ex) {
                            display.append("\n" + rount.table[vt].name + ": bị sự cố, hiện không liên lạc được.\n\n");
                            vt++;
                            if (vt > rount.max - 1) {
                                vt = 0;
                            }

                            Connect con = new Connect(rount.table[vt].destination, rount.table[vt].port,
                                    rount.table[vt].name);
                            con.connect();
                            con.requestServer("@$" + start + "|" + t + "|" + lamport + "|" + rount.table[pos - 1].name
                                    + "|" + type + "|" + action + "|" + circle + "$$" + message + "$@");
                            con.shutdown();
                        }
                    } // dong if

                    // Xu ly thong diep synchronym
                    if (type.equals("Synchronymed") && (start != 4)) {
                        display.append("Chuyển thông điệp, kiểm tra quá trình đồng bộ hóa các tiến trình.\n\n");
                        start++;
                        try {
                            int tam = pos - 2;
                            if (tam < 0) {
                                tam = 2;
                            }
                            Connect co = new Connect(rount.table[tam].destination, rount.table[tam].port,
                                    rount.table[tam].name);
                            co.connect();
                            String replyServerMessage = "@$" + start + "|" + t + "|" + lamport + "|"
                                    + rount.table[pos - 1].name + "|" + type + "|" + action + "|" + circle + "$$"
                                    + message + "$@";
                            co.requestServer(replyServerMessage);
                            co.shutdown();
                        } catch (Exception Ex) {
                        }
                    } // dong if

                    outStream.write(13);
                    outStream.write(10);
                    outStream.flush();
                }
            } catch (Exception e) {
            }
        }

        @Override
        public void run() {
            int currentCircle = 0;
            int loop = 1;
            sv3 apps = new sv3();
            Server3 app;
            Hashtable hash = new Hashtable();

            try {
                // Tên Server 3
                GetState gs = new GetState("Server3");
                gs.getCurrentCircle();
                // Gửi TCP tới Server 4 (cổng 2004)
                gs.sendUpdate("127.0.0.1", 2003, "Server3");

                // Lắng nghe TCP ở cổng 2003
                ServerSocket server = new ServerSocket(2003);
                while (true) {
                    int localPort = server.getLocalPort();
                    display.append("Server 3 đang lắng nghe tại cổng " + localPort + ".\n");
                    Socket client = server.accept();
                    apps.handler(client, "Server3", 3, currentCircle, hash); // pos = 3
                    apps.runServer();
                    ProcessData data = new ProcessData(MESSAGE);
                    Database db = new Database();
                    boolean ktradb = db.querySQL(data.getPos(), data.getNum(), data.getType(), data.getColor());
                    if (ktradb == true) {
                        if (data.getAct().equalsIgnoreCase("SET")) {
                            db.insertData(data.getPos(), data.getNum(), data.getType(), data.getColor(),
                                    data.getTime());
                        } else if (data.getAct().equalsIgnoreCase("DEL")) {
                            db.delData(data.getPos());
                        }
                    }
                    currentCircle++;
                    hash.put(String.valueOf(currentCircle), MESSAGE);

                }
            } catch (IOException e) {
            }
        }
    }

    public static class getLamports implements Runnable {

        /*
         * static variable, dealocate one time and it exist during a program
         */
        getLamports() {
            // lamport = new UDPMulticastServer();
            // lamport.start();
            new Thread(this, "getLamports").start();
        }

        @Override
        public void run() {
            int lp = 0;
            try {
                int i;
                byte[] buffer = new byte[65535];
                byte[] buffer1 = new byte[65535];
                int portM = 5432;
                String lamportS;
                String[] ch = new String[10];
                String[] diachiSV;
                MulticastSocket socketU;
                Server3 app;
                boolean bl;
                int max;
                while (true) {
                    // IP Multicast của Server 3
                    String address = "235.255.0.1";
                    socketU = new MulticastSocket(portM);
                    InetAddress add = InetAddress.getByName(address);
                    socketU.joinGroup(add);

                    display.append("Đang chờ kết nối lấy Lamport...\n");

                    // Receive request from client
                    DatagramPacket packet = new DatagramPacket(buffer1, buffer1.length);
                    socketU.receive(packet);
                    InetAddress client = packet.getAddress();
                    String dataReceive = new String(packet.getData(), 0, packet.getLength());
                    String temp = dataReceive;
                    if (temp.startsWith("#")) {
                        try {
                            // Send Server - Danh sách IP của các Server còn lại
                            String address1 = "235.0.0.1";
                            String address2 = "224.0.0.0";
                            String address3 = "225.4.5.6";
                            String address4 = "224.0.255.1";

                            String addresStr[] = { address, address1, address2, address3, address4 };

                            InetAddress add1 = InetAddress.getByName(address1);
                            socketU.joinGroup(add1);
                            InetAddress add2 = InetAddress.getByName(address2);
                            socketU.joinGroup(add2);
                            InetAddress add3 = InetAddress.getByName(address3);
                            socketU.joinGroup(add3);
                            InetAddress add4 = InetAddress.getByName(address4);
                            socketU.joinGroup(add4);
                            InetAddress str[] = { add, add1, add2, add3, add4 };

                            // Send information to multiServer
                            for (int j = 1; j < str.length; j++) {
                                String mes = "!RequestLamport-" + address;
                                byte messages[] = mes.getBytes();
                                DatagramPacket packetRS = new DatagramPacket(messages, messages.length, str[j], portM);
                                socketU.send(packetRS);
                                bl = true;
                                while (bl) {
                                    // Receive request from server
                                    DatagramPacket packetReS = new DatagramPacket(buffer1, buffer1.length);
                                    socketU.receive(packetReS);
                                    String messagesS = new String(packetReS.getData(), 0, packetReS.getLength());
                                    ch[j] = messagesS;
                                    if (messagesS.startsWith("!")) {
                                        bl = true;
                                    } else {
                                        bl = false;
                                    }
                                }
                            }

                            max = lp;
                            for (int j = 1; j < str.length; j++) {
                                if (Integer.parseInt(ch[j]) > max) {
                                    max = Integer.parseInt(ch[j]);
                                }
                            }

                            int lamportSendC = max + 1;
                            lp = lamportSendC;

                            // Send Lamport MultiServer
                            for (int j = 1; j < str.length; j++) {
                                String mesLP = Integer.toString(lp);
                                byte messagesLP[] = mesLP.getBytes();
                                DatagramPacket packetSS = new DatagramPacket(messagesLP, messagesLP.length, str[j],
                                        portM);
                                socketU.send(packetSS);
                            }

                            // send Client
                            String addressClient = "224.1.2.3";
                            InetAddress addC = InetAddress.getByName(addressClient);

                            String m = Integer.toString(lamportSendC);
                            byte ms[] = m.getBytes();
                            DatagramPacket pkC = new DatagramPacket(ms, ms.length, addC, portM);
                            socketU.send(pkC);
                        } catch (IOException e) {
                            display.append("Lỗi: No connect to multiServer");

                        }
                    } else if (temp.startsWith("!")) {
                        diachiSV = temp.split("-");
                        InetAddress addSV = InetAddress.getByName(diachiSV[1]);

                        String m = Integer.toString(lp);
                        byte ms[] = m.getBytes();
                        DatagramPacket pkSV = new DatagramPacket(ms, ms.length, addSV, portM);
                        socketU.send(pkSV);
                    } else {
                        lp = Integer.parseInt(temp);
                    }
                    display.append(temp + "\n");
                }
            } catch (IOException e) {
                display.append("Lỗi: No connect");
            }
        }
    }

    public static void main(String args[]) {

        Hashtable hash = new Hashtable();

        Server3 app = new Server3();
        app.addWindowListener(new WindowAdapter() {

            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });

        sv3 sv3s = new sv3();
        // getLamports lamports = new getLamports();
    }
}