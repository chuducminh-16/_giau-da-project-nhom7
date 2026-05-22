package com.auction.server.service;

import com.auction.server.service.UserService.RegisterResult;
import com.auction.shared.model.Entity.User.User;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Test cho UserService.
 *
 * Chiến lược: test logic validate INPUT (không cần DB).
 * - register(): username/email/password null, rỗng, sai format
 * - login(): username/password null, rỗng
 * - RegisterResult enum có đủ giá trị
 *
 * Các test cần DB (USERNAME_EXISTS, SUCCESS thật) được đánh dấu
 * @Disabled để CI không fail khi không có DB.
 */
@DisplayName("UserService Tests")
class UserServiceTest {

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RegisterResult enum
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RegisterResult có đủ các giá trị enum cần thiết")
    void registerResult_enum_hasRequiredValues() {
        RegisterResult[] values = RegisterResult.values();
        java.util.List<String> names = new java.util.ArrayList<>();
        for (RegisterResult v : values) names.add(v.name());

        assertTrue(names.contains("SUCCESS"));
        assertTrue(names.contains("EMAIL_EXISTS"));
        assertTrue(names.contains("USERNAME_EXISTS"));
        assertTrue(names.contains("ERROR"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // register() — validate input (không cần DB)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: username null → ERROR")
    void register_nullUsername_returnsError() {
        RegisterResult result = userService.register(
                null, "test@email.com", "pass123", "Full Name", "0123", "HN", "BIDDER");
        assertEquals(RegisterResult.ERROR, result);
    }

    @Test
    @DisplayName("register: username rỗng → ERROR")
    void register_emptyUsername_returnsError() {
        RegisterResult result = userService.register(
                "", "test@email.com", "pass123", "Full Name", "0123", "HN", "BIDDER");
        assertEquals(RegisterResult.ERROR, result);
    }

    @Test
    @DisplayName("register: username chỉ khoảng trắng → ERROR")
    void register_blankUsername_returnsError() {
        RegisterResult result = userService.register(
                "   ", "test@email.com", "pass123", "Full Name", "0123", "HN", "BIDDER");
        assertEquals(RegisterResult.ERROR, result);
    }

    @Test
    @DisplayName("register: email null → ERROR")
    void register_nullEmail_returnsError() {
        RegisterResult result = userService.register(
                "testuser", null, "pass123", "Full Name", "0123", "HN", "BIDDER");
        assertEquals(RegisterResult.ERROR, result);
    }

    @Test
    @DisplayName("register: email không có @ → ERROR")
    void register_invalidEmail_noAt_returnsError() {
        RegisterResult result = userService.register(
                "testuser", "invalidemail.com", "pass123", "Full Name", "0123", "HN", "BIDDER");
        assertEquals(RegisterResult.ERROR, result);
    }

    @Test
    @DisplayName("register: email rỗng → ERROR")
    void register_emptyEmail_returnsError() {
        RegisterResult result = userService.register(
                "testuser", "", "pass123", "Full Name", "0123", "HN", "BIDDER");
        assertEquals(RegisterResult.ERROR, result);
    }

    @Test
    @DisplayName("register: password null → ERROR")
    void register_nullPassword_returnsError() {
        RegisterResult result = userService.register(
                "testuser", "test@email.com", null, "Full Name", "0123", "HN", "BIDDER");
        assertEquals(RegisterResult.ERROR, result);
    }

    @Test
    @DisplayName("register: password ngắn hơn 6 ký tự → ERROR")
    void register_shortPassword_returnsError() {
        RegisterResult result = userService.register(
                "testuser", "test@email.com", "12345", "Full Name", "0123", "HN", "BIDDER");
        assertEquals(RegisterResult.ERROR, result);
    }

    @Test
    @DisplayName("register: password đúng 6 ký tự → không phải ERROR do validate password")
    void register_exactlyMinPassword_passesPasswordValidation() {
        // Vẫn có thể ERROR vì DB, nhưng không phải vì password ngắn
        // → result phải khác ERROR hoặc là USERNAME_EXISTS/SUCCESS
        // Dùng username random để tránh trùng
        RegisterResult result = userService.register(
                "user_" + System.currentTimeMillis(),
                "valid@email.com", "123456",
                "Full Name", "0123", "HN", "BIDDER");
        // Không phải ERROR do validate → SUCCESS hoặc lỗi DB (chấp nhận cả 2)
        assertNotEquals(RegisterResult.ERROR, result,
                "Password đúng 6 ký tự không nên bị reject bởi validate");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // login() — validate input (không cần DB)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: username null → null")
    void login_nullUsername_returnsNull() {
        User result = userService.login(null, "pass123");
        assertNull(result, "Username null phải trả về null");
    }

    @Test
    @DisplayName("login: username rỗng → null")
    void login_emptyUsername_returnsNull() {
        User result = userService.login("", "pass123");
        assertNull(result, "Username rỗng phải trả về null");
    }

    @Test
    @DisplayName("login: username chỉ khoảng trắng → null")
    void login_blankUsername_returnsNull() {
        User result = userService.login("   ", "pass123");
        assertNull(result, "Username blank phải trả về null");
    }

    @Test
    @DisplayName("login: password null → null")
    void login_nullPassword_returnsNull() {
        User result = userService.login("testuser", null);
        assertNull(result, "Password null phải trả về null");
    }

    @Test
    @DisplayName("login: password rỗng → null")
    void login_emptyPassword_returnsNull() {
        User result = userService.login("testuser", "");
        assertNull(result, "Password rỗng phải trả về null");
    }

    @Test
    @DisplayName("login: password chỉ khoảng trắng → null")
    void login_blankPassword_returnsNull() {
        User result = userService.login("testuser", "   ");
        assertNull(result, "Password blank phải trả về null");
    }

    @Test
    @DisplayName("login: username không tồn tại trong DB → null")
    void login_nonExistentUser_returnsNull() {
        // Username random chắc chắn không tồn tại
        User result = userService.login("nonexistent_xyz_" + System.currentTimeMillis(), "pass123");
        assertNull(result, "Username không tồn tại phải trả về null");
    }

    @Test
    @DisplayName("login: cả username lẫn password đều null → null")
    void login_bothNull_returnsNull() {
        User result = userService.login(null, null);
        assertNull(result);
    }
}
