package Server1;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.util.Hashtable;

public class Server1 {

    // =======================================================================
    // SỬA LỖI 1: Dùng synchronized StringBuffer để tránh race condition
    //            khi nhiều thread cùng ghi log
    // =======================================================================
    public static final StringBuffer webLogs = new StringBuffer();

    public static synchronized void log(String msg) {
        System.out.print(msg);
        webLogs.append(msg.replace("\n", "<br>"));
    }

    // =======================================================================
    // CLASS XỬ LÝ MỖI KẾT NỐI TRONG THREAD RIÊNG
    // SỬA LỖI 2: Tách handler thành Runnable độc lập — mỗi kết nối 1 thread
    //            → Server không bị block, nhận kết nối liên tục
    // =======================================================================
    public static class ConnectionHandler implements Runnable {

        private final Socket client;
        private final int pos = 1;                  // Server 1 ở vị trí 1 trong vòng tròn
        private final String serverName = "Server1";
        private final Hashtable<String, String> hash;
        private final RountingTable rount;

        // SỬA LỖI 3: Bỏ static MESSAGE/replyMessage — dùng biến instance
        //            để tránh các thread ghi đè lên nhau
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
                Server1.log("LỖI ConnectionHandler: " + e.getMessage() + "\n");
            } finally {
                try { client.close(); } catch (Exception ignored) {}
            }
        }

        public void runServer() {
            try {
                String destName = client.getInetAddress().getHostName();
                int destPort = client.getPort();
                Server1.log("Chấp nhận kết nối từ " + destName + " tại cổng " + destPort + ".\n");

                // SỬA LỖI 4: Thêm "UTF-8" vào InputStreamReader để đọc đúng tiếng Việt
                BufferedReader inStream = new BufferedReader(
                        new InputStreamReader(client.getInputStream(), "UTF-8"));
                OutputStream outStream = client.getOutputStream();

                String inLine = inStream.readLine();
                if (inLine == null || inLine.trim().isEmpty()) {
                    Server1.log("Nhận được gói tin rỗng, bỏ qua.\n");
                    return;
                }
                Server1.log("Nhận raw: " + inLine + "\n");

                // SỬA LỖI 5: Bọc parse trong try-catch riêng để log rõ lỗi
                MessageProcess re;
                try {
                    re = new MessageProcess(inLine);
                } catch (Exception e) {
                    Server1.log("LỖI parse gói tin: " + e.getMessage() + " | Raw: " + inLine + "\n");
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

                Server1.log("Thông điệp nhận được:\n"
                        + "  start=" + st + " | jeton=" + je + " | lamport=" + lamport + "\n"
                        + "  type=" + type + " | action=" + action + " | circle=" + circle + "\n"
                        + "  message=" + message + "\n");

                int start = 0;
                int act   = 1;
                try { start = Integer.parseInt(st); }   catch (Exception e) { Server1.log("LỖI parse start: " + st + "\n"); }
                try { act   = Integer.parseInt(action); } catch (Exception e) { Server1.log("LỖI parse action: " + action + "\n"); }

                // --- CẬP NHẬT JETON ---
                // SỬA LỖI 6: RountingTable có 5 server → jeton dài 5 ký tự
                String t = updateJeton(je, act, pos);

                // --- XÁC ĐỊNH SERVER TIẾP THEO TRONG VÒNG TRÒN (ĐI XUÔI) ---
                int vt = pos; // pos=1 → vt=1 → rount.table[1] = Server2
                if (vt > rount.max - 1) vt = 0;

                // =============================================================
                // NHẬN TỪ CLIENT (start == 0): Validate → Reply client ngay →
                //   Khởi động vòng tròn Jeton
                // =============================================================
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

                    // SỬA LỖI 7: Reply client NGAY, trước khi chạy jeton
                    PrintWriter writer = new PrintWriter(
                            new OutputStreamWriter(outStream, "UTF-8"), true);
                    writer.println(replyMessage);
                    outStream.flush();
                    Server1.log("Reply client: " + replyMessage + "\n");

                    // Nếu lỗi thì không cần chạy jeton
                    if (replyMessage.startsWith("Lỗi")) {
                        Server1.log("Dừng jeton do lỗi validate.\n\n");
                        return;
                    }

                    // Thực thi trên DB local của Server 1
                    if (!message.endsWith("VIEW")) {
                        executeDatabaseAction(message);
                    }

                    // Khởi động vòng tròn Jeton — gửi "Locked" đến server tiếp theo
                    Server1.log("Khởi động vòng tròn Jeton: gửi Locked → " + rount.table[vt].name + "\n\n");
                    sendToNext(vt, "@$" + start + "|" + t + "|" + lamport + "|"
                            + serverName + "|Locked|" + act + "|" + circle + "$$" + message + "$@");
                    return;
                }

                // =============================================================
                // LOCKED đi xuôi: Chuyển tiếp đến server tiếp theo
                // =============================================================
                if (type.equals("Locked") && start != 4) {
                    Server1.log("Nhận Locked (start=" + start + "), chuyển tiếp đến " + rount.table[vt].name + "\n\n");
                    start++;
                    sendToNext(vt, "@$" + start + "|" + t + "|" + lamport + "|"
                            + serverName + "|Locked|" + action + "|" + circle + "$$" + message + "$@");
                    return;
                }

                // LOCKED đến server cuối (start == 4): Chuyển sang Temped đi ngược
                if (type.equals("Locked") && start == 4) {
                    Server1.log("Locked hoàn tất vòng. Tạo bảng tạm. Gửi Temped đi ngược.\n\n");
                    int stt = 1;
                    act++;
                    int tam = getPrevAlive(t, pos);
                    sendToNext(tam, "@$" + stt + "|" + t + "|" + lamport + "|"
                            + serverName + "|Temped|" + act + "|" + circle + "$$" + message + "$@");
                    return;
                }

                // =============================================================
                // TEMPED đi ngược: Chuyển tiếp ngược
                // =============================================================
                if (type.equals("Temped") && start != 4) {
                    Server1.log("Nhận Temped (start=" + start + "), chuyển ngược.\n\n");
                    start++;
                    int tam = getPrevAlive(t, pos);
                    sendToNext(tam, "@$" + start + "|" + t + "|" + lamport + "|"
                            + serverName + "|Temped|" + act + "|" + circle + "$$" + message + "$@");
                    return;
                }

                // TEMPED về đến server gốc (start == 4): Chuyển sang Updated đi xuôi
                if (type.equals("Temped") && start == 4) {
                    Server1.log("Temped hoàn tất. Cập nhật DB chính. Gửi Updated đi xuôi.\n\n");
                    int stt = 1;
                    act++;
                    executeDatabaseAction(message);
                    sendToNext(vt, "@$" + stt + "|" + t + "|" + lamport + "|"
                            + serverName + "|Updated|" + act + "|" + circle + "$$" + message + "$@");
                    return;
                }

                // =============================================================
                // UPDATED đi xuôi: Chuyển tiếp đến server tiếp theo
                // =============================================================
                if (type.equals("Updated") && start != 4) {
                    Server1.log("Nhận Updated (start=" + start + "), chuyển tiếp đến " + rount.table[vt].name + "\n\n");
                    start++;
                    sendToNext(vt, "@$" + start + "|" + t + "|" + lamport + "|"
                            + serverName + "|Updated|" + action + "|" + circle + "$$" + message + "$@");
                    return;
                }

                // UPDATED đến server cuối (start == 4): Chuyển sang Synchronymed đi ngược
                if (type.equals("Updated") && start == 4) {
                    Server1.log("Updated hoàn tất. Đồng bộ xong. Gửi Synchronymed đi ngược.\n\n");
                    int stt = 1;
                    act++;
                    int tam = getPrevAlive(t, pos);
                    sendToNext(tam, "@$" + stt + "|" + t + "|" + lamport + "|"
                            + serverName + "|Synchronymed|" + act + "|" + circle + "$$" + message + "$@");
                    return;
                }

                // =============================================================
                // SYNCHRONYMED đi ngược: Chuyển tiếp
                // =============================================================
                if (type.equals("Synchronymed") && start != 4) {
                    Server1.log("Nhận Synchronymed (start=" + start + "), chuyển ngược.\n\n");
                    start++;
                    int tam = getPrevAlive(t, pos);
                    sendToNext(tam, "@$" + start + "|" + t + "|" + lamport + "|"
                            + serverName + "|Synchronymed|" + action + "|" + circle + "$$" + message + "$@");
                    return;
                }

                // SYNCHRONYMED về server gốc (start == 4): Kết thúc giao dịch
                if (type.equals("Synchronymed") && start == 4) {
                    Server1.log("✅ HOÀN TẤT GIAO DỊCH. Vòng tròn Jeton kết thúc.\n\n");
                }

            } catch (Exception e) {
                Server1.log("LỖI runServer: " + e.getClass().getSimpleName() + " - " + e.getMessage() + "\n");
            }
        }

        // --- Cập nhật jeton theo action ---
        private String updateJeton(String je, int act, int pos) {
            // SỬA LỖI 8: Đảm bảo jeton luôn đủ 5 ký tự (cho 5 server)
            String base = "00000";
            if (je != null && je.length() >= 5) base = je;
            else if (je != null && !je.isEmpty()) base = (je + "00000").substring(0, 5);

            if (act == 4) {
                // Đi ngược, xóa bit của server sau
                return base;
            } else {
                // Đánh dấu server hiện tại đã nhận jeton
                char[] arr = base.toCharArray();
                arr[pos - 1] = '1';
                return new String(arr);
            }
        }

        // --- Tìm server trước còn sống để gửi ngược ---
        private int getPrevAlive(String jeton, int pos) {
            int tam = pos - 2;
            if (tam < 0) tam = rount.max - 1;
            // Kiểm tra server tại vị trí tam có sống không (bit = '1')
            try {
                if (jeton.charAt(tam) == '0') {
                    Server1.log("Server" + (tam + 1) + " bị sự cố (jeton=" + jeton + "), thử server trước.\n");
                    tam--;
                    if (tam < 0) tam = rount.max - 1;
                }
            } catch (Exception e) { /* giữ nguyên tam */ }
            return tam;
        }

        // --- Gửi tin đến server kế tiếp, có retry nếu thất bại ---
        private void sendToNext(int tableIndex, String msg) {
            int idx = tableIndex;
            for (int retry = 0; retry < rount.max; retry++) {
                try {
                    Connect co = new Connect(rount.table[idx].destination,
                            rount.table[idx].port, rount.table[idx].name);
                    co.connect();
                    co.requestServer(msg);
                    co.shutdown();
                    return; // Thành công
                } catch (Exception ex) {
                    Server1.log("⚠ " + rount.table[idx].name + " không phản hồi, thử server tiếp theo.\n");
                    idx = (idx + 1) % rount.max;
                }
            }
            Server1.log("❌ Tất cả server đều không phản hồi. Hủy jeton.\n");
        }

        // --- Thực thi hành động trên DB local ---
        private void executeDatabaseAction(String message) {
            try {
                Database db = new Database();
                ProcessData data = new ProcessData(message);
                if (message.endsWith("SET")) {
                    db.insertData(data.getPos(), data.getNum(), data.getType(), data.getColor(), data.getTime());
                    Server1.log("DB: Đã thêm vé ghế " + data.getPos() + "\n");
                } else if (message.endsWith("DEL")) {
                    db.delData(data.getPos());
                    Server1.log("DB: Đã xóa vé ghế " + data.getPos() + "\n");
                }
            } catch (Exception e) {
                Server1.log("LỖI executeDatabaseAction: " + e.getMessage() + "\n");
            }
        }
    }

    // =======================================================================
    // RUNNABLE CHÍNH: Lắng nghe port và tạo thread cho mỗi kết nối
    // =======================================================================
    public static class sv1 implements Runnable {

        sv1() {
            new Thread(this, "sv1-main").start();
        }

        @Override
        public void run() {
            Hashtable<String, String> hash = new Hashtable<>();
            int currentCircle = 0;

            // SỬA LỖI 9: Bỏ GetState.sendUpdate trước khi mở ServerSocket
            //            (gây lỗi kết nối vào chính mình khi port chưa mở)
            try {
                GetState gs = new GetState("Server1");
                gs.getCurrentCircle();
                // gs.sendUpdate đã bị xóa — không tự kết nối vào mình
            } catch (Exception e) {
                Server1.log("GetState khởi tạo thất bại (bỏ qua): " + e.getMessage() + "\n");
            }

            try (ServerSocket server = new ServerSocket(2001)) {
                Server1.log("=== Server 1 đang lắng nghe tại cổng 2001 ===\n");

                while (true) {
                    try {
                        Socket client = server.accept();
                        client.setSoTimeout(10000); // 10 giây timeout đọc

                        // SỬA LỖI CHÍNH: Mỗi kết nối chạy trong thread riêng
                        final Hashtable<String, String> hashRef = hash;
                        new Thread(new ConnectionHandler(client, hashRef),
                                "conn-" + client.getPort()).start();

                        currentCircle++;
                    } catch (Exception e) {
                        Server1.log("LỖI accept: " + e.getMessage() + "\n");
                    }
                }
            } catch (IOException e) {
                Server1.log("❌ Không thể mở cổng 2001: " + e.getMessage() + "\n");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // Web monitor tại cổng 8080
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(8080), 0);
        httpServer.createContext("/", exchange -> {
            String response = "<html><head><meta charset='UTF-8'>"
                    + "<meta http-equiv='refresh' content='2'></head>"
                    + "<body style='background:#1e1e1e;color:#00ff00;font-family:monospace;padding:20px;'>"
                    + "<h2>MÁY CHỦ 1 - RẠP PHIM (Google Cloud)</h2>"
                    + "<div style='border:1px solid #444;padding:15px;height:80vh;overflow-y:auto;'>"
                    + webLogs.toString() + "</div></body></html>";
            byte[] bytes = response.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        httpServer.start();

        Server1.log("=== Hệ thống Server 1 đã sẵn sàng ===\n");
        Server1.log("Web Monitor: http://localhost:8080\n");

        new sv1();
    }
}
