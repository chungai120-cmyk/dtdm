package Client1;

import javax.swing.*;
import java.awt.*;

public class Client extends JFrame {

    private JFrame mainFrm;
    private JTabbedPane tab;

    public Client() {

        mainFrm = new JFrame("Hệ thống Đặt vé Rạp chiếu phim");
        mainFrm.setSize(420, 520);

        // 🔥 FONT CHUNG
        Font font = new Font("Segoe UI", Font.PLAIN, 14);
        UIManager.put("Label.font", font);
        UIManager.put("Button.font", font);
        UIManager.put("TabbedPane.font", font);
        UIManager.put("TextField.font", font);
        UIManager.put("TextArea.font", font);

        tab = new JTabbedPane();

        tab.addTab("Đặt - Hủy vé", new ActPark());
        tab.addTab("Sơ đồ Rạp", new View());

        mainFrm.add(tab);
        mainFrm.setVisible(true);
        mainFrm.setResizable(false);
        mainFrm.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    public static void main(String args[]) {
        new Client();
    }
}