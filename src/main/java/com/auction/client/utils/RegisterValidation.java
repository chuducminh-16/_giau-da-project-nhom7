package com.auction.client.utils;

/**
 * 🛠️ BỘ KIỂM TRA DỮ LIỆU ĐĂNG KÝ (REGISTER VALIDATION)
 * - Nhiệm vụ: Tách biệt hoàn toàn các quy tắc nghiệp vụ kiểm tra chuỗi (Business Rules) ra khỏi tầng điều khiển giao diện.
 */
public class RegisterValidation {

    /**
     * Thực hiện kiểm tra dữ liệu thô người dùng nhập vào Form đăng ký.
     * @return Chuỗi thông báo lỗi cụ thể nếu vi phạm quy tắc, hoặc null nếu tất cả dữ liệu đều hợp lệ.
     */
    public static String validate(String username, String email, String password, String confirmPassword) {
        // 1. Kiểm tra các trường thông tin bắt buộc phải điền
        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            return "Vui lòng điền đầy đủ thông tin bắt buộc.";
        }
        
        // 2. Kiểm tra định dạng cấu trúc Email cơ bản
        if (!email.contains("@")) {
            return "Email không hợp lệ.";
        }
        
        // 3. Kiểm tra độ an toàn tối thiểu của mật khẩu
        if (password.length() < 6) {
            return "Mật khẩu phải có ít nhất 6 ký tự.";
        }
        
        // 4. Kiểm tra sự trùng khớp của mật khẩu gõ lại
        if (!password.equals(confirmPassword)) {
            return "Mật khẩu xác nhận không khớp.";
        }

        // Không phát hiện bất kỳ lỗi nào dữ liệu đầu vào
        return null;
    }
}