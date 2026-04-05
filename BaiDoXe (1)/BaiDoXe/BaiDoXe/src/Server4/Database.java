package Server4; // CHÚ Ý: Khi chép sang Server 2, 3, 4, 5 thì nhớ sửa số ở đây!

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class Database {

    // Đã nâng cấp Driver chuẩn cho MySQL đời mới để không bị lỗi kết nối
    String drivername = "com.mysql.cj.jdbc.Driver"; 
    String connectionURL = "jdbc:mysql://localhost:3306/db?useSSL=false&allowPublicKeyRetrieval=true";
    String username = "root";
    String password = "root"; // Mật khẩu máy bro đang là root, tui giữ nguyên nhé
    Statement stmt = null;
    ResultSet rs = null;
    Connection conn;

    public Database() {
        try {
            Class.forName(drivername).newInstance();
            conn = DriverManager.getConnection(connectionURL, username, password);
            stmt = conn.createStatement();
        } catch (Exception ex) {
            System.out.println("Lỗi kết nối CSDL: " + ex.getMessage());
        }
    }

    // Đã đổi tham số sang chuẩn Rạp phim (soghe, tenkhach, loaive, thanhtoan, giodat)
    public void insertData(String soghe, String tenkhach, String loaive, String thanhtoan, String giodat) {
        // CHÚ Ý: Chép sang Server khác nhớ đổi chữ 'server1' thành 'server2', 'server3'...
        String sSQL = "INSERT INTO server1 VALUES ('" + soghe + "','" + tenkhach + "','" + loaive + "','" + thanhtoan + "','" + giodat + "')";
        try {
            stmt.executeUpdate(sSQL);
        } catch (Exception e) {
            System.out.println("Lỗi Insert: " + e.getMessage());
        }
    }

    public void delData(String id) {
        try {
            // Hủy vé theo số ghế
            String sSQL = "DELETE FROM server1 WHERE soghe='" + id + "'";
            stmt.executeUpdate(sSQL);
        } catch (Exception e) {
            System.out.println("Lỗi Delete: " + e.getMessage());
        }
    }

    public String getData() {
        String pos, num, type, clr, time, st = "";
        try {
            String sSQL = "SELECT * FROM server1";
            rs = stmt.executeQuery(sSQL);
            while (rs.next()) {
                pos = rs.getString("soghe");      // Vị trí -> Số ghế
                num = rs.getString("tenkhach");   // Biển số -> Tên khách
                type = rs.getString("loaive");    // Hiệu xe -> Loại vé
                clr = rs.getString("thanhtoan");  // Màu xe -> Thanh toán
                time = rs.getString("giodat");    // Giờ đến -> Giờ đặt
                
                // Vẫn giữ nguyên cấu trúc ghép chuỗi bằng dấu "|" để ProcessData cắt không bị lỗi
                st = st + pos + "|" + num + "|" + type + "|" + clr + "|" + time + "|";
            }
        } catch (Exception e) {
        }
        return st;
    }

    // Hàm check xem ghế đã có người đặt chưa
    public boolean isEmpty(String id) {
        boolean check = true;
        try {
            String sSQL = "SELECT soghe FROM server1 WHERE soghe='" + id + "'";
            rs = stmt.executeQuery(sSQL);
            if (rs.next()) {
                check = false; // Nếu tìm thấy số ghế này trong DB nghĩa là đã bị đặt
            }
        } catch (Exception e) {
        }
        return check;
    }
    
    // Hàm truy vấn chi tiết 1 vé
    public boolean querySQL(String soghe, String tenkhach, String loaive, String thanhtoan) {
        boolean check = true;
        try {
            String sSQL = "SELECT * FROM server1 WHERE soghe='" + soghe + "'"
                    + "AND tenkhach='" + tenkhach + "'"
                    + "AND loaive='" + loaive + "'"
                    + "AND thanhtoan='" + thanhtoan + "'";
            rs = stmt.executeQuery(sSQL);
            if (rs != null && rs.next()) {
                check = false;
            }
        } catch (Exception e) {
        }
        return check;
    }
}