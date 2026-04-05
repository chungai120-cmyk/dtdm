package Server2; // CHÚ Ý: Khi chép sang Server 2, 3, 4, 5 thì nhớ sửa số ở đây!

public class ProcessData {

    // Đã đổi tên biến cho chuẩn Rạp Phim
    String soghe, tenkhach, loaive, thanhtoan, giodat, act;

    public ProcessData(String recvMess) {
        int i;
        String temp = recvMess;

        // Cắt khúc 1: Lấy Số ghế (Trước đây là Vị trí)
        try {
            i = temp.indexOf("|");
        } catch (Exception ex) {
            i = 0;
        }
        soghe = temp.substring(0, i);
        i += 1;
        temp = temp.substring(i);

        // Cắt khúc 2: Lấy Tên Khách (Trước đây là Biển số)
        try {
            i = temp.indexOf("|");
        } catch (Exception ex) {
            i = 0;
        }
        tenkhach = temp.substring(0, i);
        i += 1;
        temp = temp.substring(i);

        // Cắt khúc 3: Lấy Loại vé (Trước đây là Hãng xe)
        try {
            i = temp.indexOf("|");
        } catch (Exception ex) {
            i = 0;
        }
        loaive = temp.substring(0, i);
        i += 1;
        temp = temp.substring(i);

        // Cắt khúc 4: Lấy Thanh toán (Trước đây là Màu xe)
        try {
            i = temp.indexOf("|");
        } catch (Exception ex) {
            i = 0;
        }
        thanhtoan = temp.substring(0, i);
        i += 1;
        temp = temp.substring(i);

        // Cắt khúc 5: Lấy Giờ đặt (Trước đây là Giờ đến)
        try {
            i = temp.indexOf("|");
        } catch (Exception ex) {
            i = 0;
        }
        giodat = temp.substring(0, i);
        i += 1;
        temp = temp.substring(i);

        // Cắt khúc cuối: Lấy Hành động (SET hoặc DEL)
        act = temp;
    }

    // --- CÁC HÀM GETTER GIỮ NGUYÊN TÊN ĐỂ KHÔNG BỊ LỖI Ở FILE KHÁC ---

    public String getPos() {
        return soghe; // Trả về Số ghế
    }

    public String getNum() {
        return tenkhach.toUpperCase(); // Trả về Tên khách (viết hoa)
    }

    public String getType() {
        return loaive.toUpperCase(); // Trả về Loại vé
    }

    public String getColor() {
        return thanhtoan.toUpperCase(); // Trả về Hình thức thanh toán
    }

    public String getTime() {
        return giodat.toUpperCase(); // Trả về Giờ đặt
    }

    public String getAct() {
        return act.toUpperCase(); // Trả về Hành động
    }
}