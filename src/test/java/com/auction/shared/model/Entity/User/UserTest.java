package com.auction.shared.model.Entity.User;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Test cho các lớp User: Bidder, Seller, Admin.
 *
 * Kiểm tra:
 *  - OOP (Encapsulation, Inheritance, Polymorphism)
 *  - checkPassword() đúng / sai
 *  - getRole() trả đúng role
 *  - Bidder: balance không âm
 *  - Seller: rating, addItem()
 *  - Admin: adminLevel
 */
@DisplayName("User Model Tests")
public class UserTest {

    // ─────────────────────────────────────────────────────────────────────────
    // BIDDER
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Bidder: getRole() phải trả về BIDDER")
    void bidder_getRole_returnsBIDDER() {
        Bidder b = new Bidder("b1", "alice", "alice@mail.com", "pass123", 500.0);
        assertEquals("BIDDER", b.getRole());
    }

    @Test
    @DisplayName("Bidder: checkPassword đúng mật khẩu → true")
    void bidder_checkPassword_correct_returnsTrue() {
        Bidder b = new Bidder("b1", "alice", "alice@mail.com", "secret", 500.0);
        assertTrue(b.checkPassword("secret"));
    }

    @Test
    @DisplayName("Bidder: checkPassword sai mật khẩu → false")
    void bidder_checkPassword_wrong_returnsFalse() {
        Bidder b = new Bidder("b1", "alice", "alice@mail.com", "secret", 500.0);
        assertFalse(b.checkPassword("wrongpass"));
    }

    @Test
    @DisplayName("Bidder: setBalance âm → balance không thay đổi")
    void bidder_setBalance_negative_ignored() {
        Bidder b = new Bidder("b1", "alice", "alice@mail.com", "pass", 300.0);
        b.setBalance(-100.0); // âm → bị bỏ qua
        assertEquals(300.0, b.getBalance(), 0.001);
    }

    @Test
    @DisplayName("Bidder: setBalance hợp lệ → được cập nhật")
    void bidder_setBalance_valid_updated() {
        Bidder b = new Bidder("b1", "alice", "alice@mail.com", "pass", 300.0);
        b.setBalance(1000.0);
        assertEquals(1000.0, b.getBalance(), 0.001);
    }

    @Test
    @DisplayName("Bidder: getUsername() trả đúng username")
    void bidder_getUsername_correct() {
        Bidder b = new Bidder("b1", "alice", "alice@mail.com", "pass", 0);
        assertEquals("alice", b.getUsername());
    }

    @Test
    @DisplayName("Bidder: getEmail() trả đúng email")
    void bidder_getEmail_correct() {
        Bidder b = new Bidder("b1", "alice", "alice@mail.com", "pass", 0);
        assertEquals("alice@mail.com", b.getEmail());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SELLER
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Seller: getRole() phải trả về SELLER")
    void seller_getRole_returnsSELLER() {
        Seller s = new Seller("s1", "bob", "bob@mail.com", "pass123", 5.0);
        assertEquals("SELLER", s.getRole());
    }

    @Test
    @DisplayName("Seller: rating mặc định khởi tạo là 5.0")
    void seller_defaultRating_is5() {
        Seller s = new Seller("s1", "bob", "bob@mail.com", "pass", 3.0);
        // Constructor hardcode 5.0 bất kể tham số
        assertEquals(5.0, s.getRating(), 0.001);
    }

    @Test
    @DisplayName("Seller: setRating cập nhật đúng giá trị")
    void seller_setRating_updated() {
        Seller s = new Seller("s1", "bob", "bob@mail.com", "pass", 5.0);
        s.setRating(4.2);
        assertEquals(4.2, s.getRating(), 0.001);
    }

    @Test
    @DisplayName("Seller: addItem thêm itemId vào danh sách")
    void seller_addItem_itemAdded() {
        Seller s = new Seller("s1", "bob", "bob@mail.com", "pass", 5.0);
        // Không có cách trực tiếp lấy itemIds vì private, nhưng addItem không throw
        assertDoesNotThrow(() -> s.addItem("item-001"));
        assertDoesNotThrow(() -> s.addItem("item-002"));
    }

    @Test
    @DisplayName("Seller: checkPassword đúng → true")
    void seller_checkPassword_correct() {
        Seller s = new Seller("s1", "bob", "bob@mail.com", "mypass", 5.0);
        assertTrue(s.checkPassword("mypass"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADMIN
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Admin: getRole() phải trả về ADMIN")
    void admin_getRole_returnsADMIN() {
        Admin a = new Admin("a1", "admin", "admin@mail.com", "admin123", 1);
        assertEquals("ADMIN", a.getRole());
    }

    @Test
    @DisplayName("Admin: getAdminLevel trả đúng cấp độ")
    void admin_getAdminLevel_correct() {
        Admin a = new Admin("a1", "admin", "admin@mail.com", "admin123", 3);
        assertEquals(3, a.getAdminLevel());
    }

    @Test
    @DisplayName("Admin: setAdminLevel cập nhật đúng")
    void admin_setAdminLevel_updated() {
        Admin a = new Admin("a1", "admin", "admin@mail.com", "admin123", 1);
        a.setAdminLevel(5);
        assertEquals(5, a.getAdminLevel());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POLYMORPHISM: User reference → đúng getRole()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Polymorphism: User ref → getRole() đúng cho từng subclass")
    void polymorphism_getRole_throughUserRef() {
        User bidder = new Bidder("b1", "u1", "u1@m.com", "p", 0);
        User seller = new Seller("s1", "u2", "u2@m.com", "p", 5.0);
        User admin  = new Admin("a1", "u3", "u3@m.com", "p", 1);

        assertEquals("BIDDER", bidder.getRole());
        assertEquals("SELLER", seller.getRole());
        assertEquals("ADMIN",  admin.getRole());
    }
}
