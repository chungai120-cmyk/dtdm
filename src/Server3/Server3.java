package Server3; // Khai báo đúng gói Server3

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.util.Hashtable;

public class Server3 {

    // Thay thế JTextArea bằng StringBuffer để chứa Log đưa lên Web
    public static StringBuffer webLogs = new StringBuffer();

    // Hàm tiện ích để ghi log: Vừa in ra Terminal, vừa lưu lên Web
    public static void log(String msg) {
        System.out.print(msg); // In ra terminal (để dễ debug trên SSH)
        webLogs.append(msg.replace("\n", "<br>")); // Lưu vào biến web để HTML đọc được
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
                Server3.log("Chấp nhận kết nối từ " + destName + " tại cổng " + destPort + ".\n");
                
                BufferedReader inStream = new BufferedReader(new InputStreamReader(client.getInputStream()));
                OutputStream outStream = client.getOutputStream();
                outStream.flush();

                boolean finished = false;
                {
                    // lay goi tin nhan duoc
                    String inLine = inStream.readLine();
                    if (inLine != null) {
                        Server3.log("Nhận raw: " + inLine + "\n");
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
                    
                    Server3.log("Thông tin nhận được :\n" + "start: " + st + "\n" + "jeton: " + je + "\n"
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
                        Server3.log("Hoàn tất giao dịch đặt vé. Kết thúc vòng tròn ảo.\n\n");
                    }

                    // xu ly thong tin updated va quay vong
                    if (type.equals("Updated") && (start == 4)) {
                        int stt = start;
                        Server3.log("Kết thúc quá trình cập nhật, kiểm tra đồng bộ hóa TT và Quay vòng ngược.\n\n");
                        stt = 1;
                        act += 1;
                        try {
                            int tam = pos - 2;
                            if (tam < 0) {
                                tam = 2;
                            }
                            if (t.charAt(tam) == '0') {
                                Server3.log("\nServer" + (tam + 1) + " bị sự cố do jeton nhận được là: " + t + ".\n");
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
                        Server3.log("Kết thúc tạo bảng tạm, cập nhật CSDL chính Quay vòng ngược.\n\n");
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
                            Server3.log("\n" + rount.table[vt].name + ": bị sự cố, hiện không liên lạc được.\n\n");
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
                        Server3.log("Kết thúc khóa trường dữ liệu, tạo bảng tạm và Quay vòng ngược.\n\n");
                        stt = 1;
                        act += 1;
                        try {
                            int tam = pos - 2;
                            if (tam < 0) {
                                tam = 2;
                            }
                            if (t.charAt(tam) == '0') {
                                Server3.log("\nServer" + (tam + 1) + " bị sự cố do jeton nhận được là: " + t + ".\n\n");
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

                        if ((message.endsWith("SET")) && (!db1.isEmpty(dt.getPos()))) {
                            replyMessage = "Lỗi: Ghế này đã có người đặt!";
                        }
                        if ((message.endsWith("DEL")) && (db1.isEmpty(dt.getPos()))) {
                            replyMessage = "Lỗi: Không tìm thấy vé tại ghế này!";
                        }

                        PrintWriter writer = new PrintWriter(new OutputStreamWriter(outStream, "UTF-8"), true);
                        writer.println(replyMessage);
                        Server3.log("Reply: " + replyMessage + "\n");
                        Server3.log("Thực hiện khóa trường DL. Chuyển thông điệp.\n\n");
                        try {
                            Connect co = new Connect(rount.table[vt].destination, rount.table[vt].port,
                                    rount.table[vt].name);
                            co.connect();
                            co.requestServer("@$" + start + "|" + t + "|" + lamport + "|" + rount.table[pos - 1].name
                                    + "|" + "Locked" + "|" + act + "|" + circle + "$$" + message + "$@");
                            co.shutdown();
                        } // bi su co
                        catch (Exception ex) {
                            Server3.log("\n" + rount.table[vt].name + ": bị sự cố, hiện không liên lạc được.\n\n");
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
                        Server3.log("Chuyển thông điệp, thực hiện khóa trường DL.\n\n");
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
                            Server3.log("\n" + rount.table[vt].name + ": bị sự cố, hiện không liên lạc được.\n\n");
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
                        Server3.log("Chuyển thông điệp, thực hiện tạo bảng tạm CSDL.\n\n");
                        start++;
                        try {
                            int tam = pos - 2;
                            if (tam < 0) {
                                tam = 2;
                            }
                            if (t.charAt(tam) == '0') {
                                Server3.log("\nServer" + (tam + 1) + " bị sự cố do jeton nhận được là: " + t + ".\n\n");
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
                        Server3.log("Chuyển thông điệp, thực hiện cập nhật bảng chính CSDL.\n\n");
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
                            Server3.log("\n" + rount.table[vt].name + ": bị sự cố, hiện không liên lạc được.\n\n");
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
                        Server3.log("Chuyển thông điệp, kiểm tra quá trình đồng bộ hóa các tiến trình.\n\n");
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
            sv3 apps = new sv3();
            Hashtable hash = new Hashtable();

            try {
                // Đổi thành Server3 và Port 2003
                GetState gs = new GetState("Server3");
                gs.getCurrentCircle();
                gs.sendUpdate("127.0.0.1", 2003, "Server3");

                ServerSocket server = new ServerSocket(2003);
                while (true) {
                    int localPort = server.getLocalPort();
                    Server3.log("Server 3 đang lắng nghe tại cổng " + localPort + ".\n");
                    Socket client = server.accept();
                    
                    // Gắn pos = 3 cho Server 3
                    apps.handler(client, "Server3", 3, currentCircle, hash);
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

        getLamports() {
            new Thread(this, "getLamports").start();
        }

        @Override
        public void run() {
            int lp = 0;
            try {
                byte[] buffer1 = new byte[65535];
                int portM = 5432;
                String[] ch = new String[10];
                String[] diachiSV;
                MulticastSocket socketU;
                boolean bl;
                int max;
                while (true) {
                    String address = "224.0.0.0";
                    socketU = new MulticastSocket(portM);
                    InetAddress add = InetAddress.getByName(address);
                    socketU.joinGroup(add);

                    Server3.log("Đang chờ kết nối lấy Lamport...\n");

                    DatagramPacket packet = new DatagramPacket(buffer1, buffer1.length);
                    socketU.receive(packet);
                    String dataReceive = new String(packet.getData(), 0, packet.getLength());
                    String temp = dataReceive;
                    if (temp.startsWith("#")) {
                        try {
                            String address1 = "235.0.0.1";
                            String address2 = "235.255.0.1";
                            String address3 = "225.4.5.6";
                            String address4 = "224.0.255.1";

                            InetAddress add1 = InetAddress.getByName(address1);
                            socketU.joinGroup(add1);
                            InetAddress add2 = InetAddress.getByName(address2);
                            socketU.joinGroup(add2);
                            InetAddress add3 = InetAddress.getByName(address3);
                            socketU.joinGroup(add3);
                            InetAddress add4 = InetAddress.getByName(address4);
                            socketU.joinGroup(add4);
                            InetAddress str[] = { add, add1, add2, add3, add4 };

                            for (int j = 1; j < str.length; j++) {
                                String mes = "!RequestLamport-" + address;
                                byte messages[] = mes.getBytes();
                                DatagramPacket packetRS = new DatagramPacket(messages, messages.length, str[j], portM);
                                socketU.send(packetRS);
                                bl = true;
                                while (bl) {
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

                            for (int j = 1; j < str.length; j++) {
                                String mesLP = Integer.toString(lp);
                                byte messagesLP[] = mesLP.getBytes();
                                DatagramPacket packetSS = new DatagramPacket(messagesLP, messagesLP.length, str[j],
                                        portM);
                                socketU.send(packetSS);
                            }

                            String addressClient = "224.1.2.3";
                            InetAddress addC = InetAddress.getByName(addressClient);

                            String m = Integer.toString(lamportSendC);
                            byte ms[] = m.getBytes();
                            DatagramPacket pkC = new DatagramPacket(ms, ms.length, addC, portM);
                            socketU.send(pkC);
                        } catch (IOException e) {
                            Server3.log("Lỗi: No connect to multiServer\n");
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
                    Server3.log(temp + "\n");
                }
            } catch (IOException e) {
                Server3.log("Lỗi: No connect\n");
            }
        }
    }

    public static void main(String args[]) throws Exception {
        // --- CHÈN WEB SERVER MINI (HIỂN THỊ LOG RA NGINX) ---
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", exchange -> {
            // Giao diện web hiển thị log, tự động làm mới sau mỗi 2 giây
            String response = "<html><head><meta charset='UTF-8'><meta http-equiv='refresh' content='2'></head>"
                    + "<body style='background:#1e1e1e; color:#00ff00; font-family:monospace; padding:20px;'>"
                    + "<h2>MÁY CHỦ 3 - RẠP PHIM (Chạy trên Google Cloud)</h2>"
                    + "<div style='border:1px solid #444; padding:15px; height:80vh; overflow-y:auto;'>" 
                    + webLogs.toString() + "</div>"
                    + "</body></html>";
            
            byte[] bytes = response.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        });
        server.start();
        
        Server3.log("--- Hệ thống Server 3 đã sẵn sàng ---\n");
        Server3.log("Web Monitor đang chạy tại cổng 8080...\n");

        // --- KHỞI CHẠY LOGIC SOCKET (Jeton) ---
        sv3 sv3s = new sv3();
        // getLamports lamports = new getLamports();
    }
}