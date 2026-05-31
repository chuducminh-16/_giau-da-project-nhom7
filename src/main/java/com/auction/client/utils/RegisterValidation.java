package com.auction.client.utils;

/**
 * RegisterValidation - Kiem tra du lieu dau vao form dang ky.
 * Tach biet quy tac nghiep vu ra khoi controller giao dien.
 */
public class RegisterValidation {

    /**
     * Kiem tra day du cac truong form dang ky.
     *
     * @param fullName       Ho va ten
     * @param username       Ten dang nhap
     * @param email          Dia chi email
     * @param phone          So dien thoai
     * @param password       Mat khau
     * @param confirmPassword Xac nhan mat khau
     * @return Chuoi thong bao loi neu co vi pham, hoac null neu hop le.
     */
    public static String validate(String fullName, String username, String email,
                                  String phone, String password, String confirmPassword) {
        if (fullName == null || fullName.isBlank())
            return "Vui long nhap ho va ten.";
        if (username == null || username.isBlank())
            return "Vui long nhap ten dang nhap.";
        if (email == null || email.isBlank())
            return "Vui long nhap dia chi email.";
        if (!email.contains("@"))
            return "Email khong hop le.";
        if (password == null || password.length() < 6)
            return "Mat khau phai co it nhat 6 ky tu.";
        if (!password.equals(confirmPassword))
            return "Mat khau xac nhan khong khop.";
        return null;
    }

    /**
     * Overload 4 tham so de giu tuong thich nguoc voi ProfileController cu.
     */
    public static String validate(String username, String email,
                                  String password, String confirmPassword) {
        return validate("(ok)", username, email, "", password, confirmPassword);
    }
}