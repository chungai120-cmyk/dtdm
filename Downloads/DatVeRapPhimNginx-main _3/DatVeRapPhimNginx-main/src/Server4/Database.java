package Server4; // Đã đổi sang gói Server4

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class Database {

    String drivername = "com.mysql.cj.jdbc.Driver";
    String connectionURL = "jdbc:mysql://localhost:3306/db?useSSL=false&allowPublicKeyRetrieval=true";
    String username = "root";
    String password = "root"; 
    Statement stmt = null;
    ResultSet rs = null;
    Connection conn;

    public Database() {
        try {
            Class.forName(drivername).newInstance();
            conn = DriverManager.getConnection(connectionURL, username, password);
            stmt = conn.createStatement();
        } catch (Exception ex) {
            System.out.println("Lỗi kết nối CSDL Server 4: " + ex.getMessage());
        }
    }

    public void insertData(String soghe, String tenkhach, String loaive, String thanhtoan, String giodat) {
        // Đã sửa thành server4
        String sSQL = "INSERT INTO server4 VALUES ('" + soghe + "','" + tenkhach + "','" + loaive + "','" + thanhtoan
                + "','" + giodat + "')";
        try {
            stmt.executeUpdate(sSQL);
        } catch (Exception e) {
            System.out.println("Lỗi Insert Server 4: " + e.getMessage());
        }
    }

    public void delData(String id) {
        try {
            // Đã sửa thành server4
            String sSQL = "DELETE FROM server4 WHERE soghe='" + id + "'";
            stmt.executeUpdate(sSQL);
        } catch (Exception e) {
            System.out.println("Lỗi Delete Server 4: " + e.getMessage());
        }
    }

    public String getData() {
        String pos, num, type, clr, time, st = "";
        try {
            // Đã sửa thành server4
            String sSQL = "SELECT * FROM server4";
            rs = stmt.executeQuery(sSQL);
            while (rs.next()) {
                pos = rs.getString("soghe");
                num = rs.getString("tenkhach");
                type = rs.getString("loaive");
                clr = rs.getString("thanhtoan");
                time = rs.getString("giodat");
                st = st + pos + "|" + num + "|" + type + "|" + clr + "|" + time + "|";
            }
        } catch (Exception e) {
        }
        return st;
    }

    public boolean isEmpty(String id) {
        boolean check = true;
        try {
            // Đã sửa thành server4
            String sSQL = "SELECT soghe FROM server4 WHERE soghe='" + id + "'";
            rs = stmt.executeQuery(sSQL);
            if (rs.next()) {
                check = false;
            }
        } catch (Exception e) {
        }
        return check;
    }

    public boolean querySQL(String soghe, String tenkhach, String loaive, String thanhtoan) {
        boolean check = true;
        try {
            // Đã sửa thành server4
            String sSQL = "SELECT * FROM server4 WHERE soghe='" + soghe + "'"
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
