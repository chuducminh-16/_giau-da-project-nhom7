import java.util.Scanner;

public class UserAccount {
    private String username;
    private String password;

    // Constructor
    public UserAccount(String username, String password) {
        this.username = username;
        this.password = password;
    }

    // Hàm kiểm tra đăng nhập
    public boolean checkLogin(String inputUser, String inputPass) {
        return this.username.equals(inputUser) && this.password.equals(inputPass);
    }

    // --- ĐÂY LÀ HÀM MAIN ---
    public static void main(String[] args) {
        // 1. Tạo một tài khoản mẫu
        UserAccount admin = new UserAccount("tvhiep", "hiep123");

        // 2. Dùng Scanner để nhập từ bàn phím cho chuyên nghiệp
        Scanner sc = new Scanner(System.in);
        
        System.out.println("=== HE THONG DANG NHAP ===");
        System.out.print("Nhap username: ");
        String u = sc.nextLine();
        
        System.out.print("Nhap password: ");
        String p = sc.nextLine();

        // 3. Kiểm tra
        if (admin.checkLogin(u, p)) {
            System.out.println(">> Chuc mung tvhiep, ban da dang nhap thanh cong!");
        } else {
            System.out.println(">> Canh bao: Sai tai khoan hoac mat khau!");
        }
        
        sc.close(); // Đóng bộ nhập
    }
}