package Server3;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.util.Hashtable;

public class Server3 {

    public static final StringBuffer webLogs = new StringBuffer();

    public static synchronized void log(String msg) {
        System.out.print(msg);
        webLogs.append(msg.replace("\n", "<br>"));
    }

    public static class ConnectionHandler implements Runnable {

        private final Socket client;
        private final int pos = 3;
        private final String serverName = "Server3";
        private final Hashtable<String, String> hash;
        private final RountingTable rount;
        private String MESSAGE = "";
        private String replyMessage = "";

        ConnectionHandler(Socket client, Hashtable<String, String> hash) {
            this.client = client;
            this.hash = hash;
            this.rount = new RountingTable();
        }

        @Override
        public void run() {
            try {
                runServer();
            } catch (Exception e) {
                Server3.log("LỖI ConnectionHandler: " + e.getMessage() + "\n");
            } finally {
                try { client.close(); } catch (Exception ignored) {}
            }
        }

        public void runServer() {
            try {
                String destName = client.getInetAddress().getHostName();
                int destPort = client.getPort();
                Server3.log("Chấp nhận kết nối từ " + destName + " tại cổng " + destPort + ".\n");

                BufferedReader inStream = new BufferedReader(
                        new InputStreamReader(client.getInputStream(), "UTF-8"));
                OutputStream outStream = client.getOutputStream();

                String inLine = inStream.readLine();
                if (inLine == null || inLine.trim().isEmpty()) {
                    Server3.log("Nhận được gói tin rỗng, bỏ qua.\n");
                    return;
                }
                Server3.log("Nhận raw: " + inLine + "\n");

                MessageProcess re;
                try {
                    re = new MessageProcess(inLine);
                } catch (Exception e) {
                    Server3.log("LỖI parse gói tin: " + e.getMessage() + " | Raw: " + inLine + "\n");
                    return;
                }

                String st      = re.getStart();
                String je      = re.getJeton();
                String lamport = re.getLamport();
                String type    = re.getType();
                String action  = re.getAction();
                String circle  = re.getNumCircle();
                String message = re.getMessage();
                MESSAGE = message;

                Server3.log("Thông điệp nhận được:\n"
                        + "  start=" + st + " | jeton=" + je + " | lamport=" + lamport + "\n"
                        + "  type=" + type + " | action=" + action + " | circle=" + circle + "\n"
                        + "  message=" + message + "\n");

                int start = 0;
                int act   = 1;
                try { start = Integer.parseInt(st); }   catch (Exception e) { Server3.log("LỖI parse start: " + st + "\n"); }
                try { act   = Integer.parseInt(action); } catch (Exception e) { Server3.log("LỖI parse action: " + action + "\n"); }

                String t = updateJeton(je, act, pos);
                int vt = pos;
                if (vt > rount.max - 1) vt = 0;

                if (start == 0) {
                    start++;
                    Database db1 = new Database();
                    ProcessData dt = new ProcessData(message);

                    if (message.endsWith("VIEW")) {
                        replyMessage = db1.getData();
                    } else if (message.endsWith("SET")) {
                        replyMessage = db1.isEmpty(dt.getPos())
                                ? "Lỗi: Ghế " + dt.getPos() + " đã có người đặt!"
                                : "Đặt vé thành công! Ghế: " + dt.getPos();
                    } else if (message.endsWith("DEL")) {
                        replyMessage = !db1.isEmpty(dt.getPos())
                                ? "Lỗi: Không tìm thấy vé tại ghế " + dt.getPos() + "!"
                                : "Hủy vé thành công! Ghế: " + dt.getPos();
                    } else {
                        replyMessage = "Lỗi: Hành động không hợp lệ.";
                    }

                    PrintWriter writer = new PrintWriter(
                            new OutputStreamWriter(outStream, "UTF-8"), true);
                    writer.println(replyMessage);
                    outStream.flush();
                    Server3.log("Reply client: " + replyMessage + "\n");

                    if (replyMessage.startsWith("Lỗi")) {
                        Server3.log("Dừng jeton do lỗi validate.\n\n");
                        return;
                    }

                    if (!message.endsWith("VIEW")) {
                        executeDatabaseAction(message);
                    }

                    Server3.log("Khởi động vòng tròn Jeton: gửi Locked → " + rount.table[vt].name + "\n\n");
                    sendToNext(vt, "@$" + start + "|" + t + "|" + lamport + "|"
                            + serverName + "|Locked|" + act + "|" + circle + "$$" + message + "$@");
                    return;
                }

                if (type.equals("Locked") && start != 4) {
                    Server3.log("Nhận Locked (start=" + start + "), chuyển tiếp đến " + rount.table[vt].name + "\n\n");
                    start++;
                    sendToNext(vt, "@$" + start + "|" + t + "|" + lamport + "|"
                            + serverName + "|Locked|" + action + "|" + circle + "$$" + message + "$@");
                    return;
                }

                if (type.equals("Locked") && start == 4) {
                    Server3.log("Locked hoàn tất vòng. Tạo bảng tạm. Gửi Temped đi ngược.\n\n");
                    int stt = 1; act++;
                    int tam = getPrevAlive(t, pos);
                    sendToNext(tam, "@$" + stt + "|" + t + "|" + lamport + "|"
                            + serverName + "|Temped|" + act + "|" + circle + "$$" + message + "$@");
                    return;
                }

                if (type.equals("Temped") && start != 4) {
                    Server3.log("Nhận Temped (start=" + start + "), chuyển ngược.\n\n");
                    start++;
                    int tam = getPrevAlive(t, pos);
                    sendToNext(tam, "@$" + start + "|" + t + "|" + lamport + "|"
                            + serverName + "|Temped|" + act + "|" + circle + "$$" + message + "$@");
                    return;
                }

                if (type.equals("Temped") && start == 4) {
                    Server3.log("Temped hoàn tất. Cập nhật DB chính. Gửi Updated đi xuôi.\n\n");
                    int stt = 1; act++;
                    executeDatabaseAction(message);
                    sendToNext(vt, "@$" + stt + "|" + t + "|" + lamport + "|"
                            + serverName + "|Updated|" + act + "|" + circle + "$$" + message + "$@");
                    return;
                }

                if (type.equals("Updated") && start != 4) {
                    Server3.log("Nhận Updated (start=" + start + "), chuyển tiếp đến " + rount.table[vt].name + "\n\n");
                    start++;
                    sendToNext(vt, "@$" + start + "|" + t + "|" + lamport + "|"
                            + serverName + "|Updated|" + action + "|" + circle + "$$" + message + "$@");
                    return;
                }

                if (type.equals("Updated") && start == 4) {
                    Server3.log("Updated hoàn tất. Gửi Synchronymed đi ngược.\n\n");
                    int stt = 1; act++;
                    int tam = getPrevAlive(t, pos);
                    sendToNext(tam, "@$" + stt + "|" + t + "|" + lamport + "|"
                            + serverName + "|Synchronymed|" + act + "|" + circle + "$$" + message + "$@");
                    return;
                }

                if (type.equals("Synchronymed") && start != 4) {
                    Server3.log("Nhận Synchronymed (start=" + start + "), chuyển ngược.\n\n");
                    start++;
                    int tam = getPrevAlive(t, pos);
                    sendToNext(tam, "@$" + start + "|" + t + "|" + lamport + "|"
                            + serverName + "|Synchronymed|" + action + "|" + circle + "$$" + message + "$@");
                    return;
                }

                if (type.equals("Synchronymed") && start == 4) {
                    Server3.log("✅ HOÀN TẤT GIAO DỊCH. Vòng tròn Jeton kết thúc.\n\n");
                }

            } catch (Exception e) {
                Server3.log("LỖI runServer: " + e.getClass().getSimpleName() + " - " + e.getMessage() + "\n");
            }
        }

        private String updateJeton(String je, int act, int pos) {
            String base = "00000";
            if (je != null && je.length() >= 5) base = je;
            else if (je != null && !je.isEmpty()) base = (je + "00000").substring(0, 5);
            if (act == 4) return base;
            char[] arr = base.toCharArray();
            arr[pos - 1] = '1';
            return new String(arr);
        }

        private int getPrevAlive(String jeton, int pos) {
            int tam = pos - 2;
            if (tam < 0) tam = rount.max - 1;
            try {
                if (jeton.charAt(tam) == '0') {
                    Server3.log("Server" + (tam + 1) + " bị sự cố (jeton=" + jeton + "), thử server trước.\n");
                    tam--;
                    if (tam < 0) tam = rount.max - 1;
                }
            } catch (Exception e) {}
            return tam;
        }

        private void sendToNext(int tableIndex, String msg) {
            int idx = tableIndex;
            for (int retry = 0; retry < rount.max; retry++) {
                try {
                    Connect co = new Connect(rount.table[idx].destination,
                            rount.table[idx].port, rount.table[idx].name);
                    co.connect();
                    co.requestServer(msg);
                    co.shutdown();
                    return;
                } catch (Exception ex) {
                    Server3.log("⚠ " + rount.table[idx].name + " không phản hồi, thử server tiếp theo.\n");
                    idx = (idx + 1) % rount.max;
                }
            }
            Server3.log("❌ Tất cả server đều không phản hồi. Hủy jeton.\n");
        }

        private void executeDatabaseAction(String message) {
            try {
                Database db = new Database();
                ProcessData data = new ProcessData(message);
                if (message.endsWith("SET")) {
                    db.insertData(data.getPos(), data.getNum(), data.getType(), data.getColor(), data.getTime());
                    Server3.log("DB: Đã thêm vé ghế " + data.getPos() + "\n");
                } else if (message.endsWith("DEL")) {
                    db.delData(data.getPos());
                    Server3.log("DB: Đã xóa vé ghế " + data.getPos() + "\n");
                }
            } catch (Exception e) {
                Server3.log("LỖI executeDatabaseAction: " + e.getMessage() + "\n");
            }
        }
    }

    public static class sv3 implements Runnable {

        sv3() {
            new Thread(this, "sv3-main").start();
        }

        @Override
        public void run() {
            Hashtable<String, String> hash = new Hashtable<>();

            try {
                GetState gs = new GetState("Server3");
                gs.getCurrentCircle();
            } catch (Exception e) {
                Server3.log("GetState khởi tạo thất bại (bỏ qua): " + e.getMessage() + "\n");
            }

            try (ServerSocket server = new ServerSocket(2003)) {
                Server3.log("=== Server 3 đang lắng nghe tại cổng 2003 ===\n");

                while (true) {
                    try {
                        Socket client = server.accept();
                        client.setSoTimeout(10000);
                        new Thread(new ConnectionHandler(client, hash),
                                "conn-" + client.getPort()).start();
                    } catch (Exception e) {
                        Server3.log("LỖI accept: " + e.getMessage() + "\n");
                    }
                }
            } catch (IOException e) {
                Server3.log("❌ Không thể mở cổng 2003: " + e.getMessage() + "\n");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(8080), 0);
        httpServer.createContext("/", exchange -> {
            String response = "<html><head><meta charset='UTF-8'>"
                    + "<meta http-equiv='refresh' content='2'></head>"
                    + "<body style='background:#1e1e1e;color:#00ff00;font-family:monospace;padding:20px;'>"
                    + "<h2>MÁY CHỦ 3 - RẠP PHIM (Google Cloud)</h2>"
                    + "<div style='border:1px solid #444;padding:15px;height:80vh;overflow-y:auto;'>"
                    + webLogs.toString() + "</div></body></html>";
            byte[] bytes = response.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        httpServer.start();

        Server3.log("=== Hệ thống Server 3 đã sẵn sàng ===\n");
        Server3.log("Web Monitor: http://localhost:8080\n");

        new sv3();
    }
}
