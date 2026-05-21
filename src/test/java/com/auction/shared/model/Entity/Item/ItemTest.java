package com.auction.shared.model.Entity.Item;

import com.auction.shared.pattern.ItemFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Test cho Item subclasses và ItemFactory (Factory Pattern).
 *
 * Kiểm tra:
 *  - Inheritance: Art/Electronics/Vehicle kế thừa Item đúng
 *  - getType() đúng cho từng loại
 *  - showDetails() chứa thông tin đặc trưng
 *  - ItemFactory tạo đúng loại item
 *  - ItemFactory ném exception với type không hợp lệ
 */
@DisplayName("Item Model & ItemFactory Tests")
public class ItemTest {

    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(7);
    private static final String END_STR = LocalDateTime.now().plusDays(7).toString();

    // ─────────────────────────────────────────────────────────────────────────
    // ART
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Art: getType() trả về ART")
    void art_getType_returnsART() {
        Art art = new Art("i1", "Mona Lisa", 5000.0, FUTURE, "s1", "Da Vinci");
        assertEquals("ART", art.getType());
    }

    @Test
    @DisplayName("Art: getArtist() trả đúng tên nghệ sĩ")
    void art_getArtist_correct() {
        Art art = new Art("i1", "Starry Night", 8000.0, FUTURE, "s1", "Van Gogh");
        assertEquals("Van Gogh", art.getArtist());
    }

    @Test
    @DisplayName("Art: showDetails() chứa tên sản phẩm và tên tác giả")
    void art_showDetails_containsNameAndArtist() {
        Art art = new Art("i1", "Starry Night", 8000.0, FUTURE, "s1", "Van Gogh");
        String details = art.showDetails();
        assertTrue(details.contains("Starry Night"));
        assertTrue(details.contains("Van Gogh"));
    }

    @Test
    @DisplayName("Art: getStartingPrice() trả đúng giá")
    void art_getStartingPrice_correct() {
        Art art = new Art("i1", "Art01", 3000.0, FUTURE, "s1", "Picasso");
        assertEquals(3000.0, art.getStartingPrice(), 0.001);
    }

    @Test
    @DisplayName("Art: constructor với String endTime không throw exception")
    void art_stringEndTimeConstructor_noException() {
        assertDoesNotThrow(() ->
                new Art("i1", "Art01", 1000.0, END_STR, "s1", "Picasso"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ELECTRONICS
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Electronics: getType() trả về ELECTRONICS")
    void electronics_getType_returnsELECTRONICS() {
        Electronics e = new Electronics("i2", "Laptop", 20000.0, FUTURE, "s1", 12);
        assertEquals("ELECTRONICS", e.getType());
    }

    @Test
    @DisplayName("Electronics: getWarrantyPeriod() trả đúng số tháng bảo hành")
    void electronics_getWarrantyPeriod_correct() {
        Electronics e = new Electronics("i2", "Phone", 10000.0, FUTURE, "s1", 24);
        assertEquals(24, e.getWarrantyPeriod());
    }

    @Test
    @DisplayName("Electronics: showDetails() chứa số tháng bảo hành")
    void electronics_showDetails_containsWarranty() {
        Electronics e = new Electronics("i2", "Laptop", 20000.0, FUTURE, "s1", 12);
        assertTrue(e.showDetails().contains("12"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VEHICLE
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Vehicle: getType() trả về VEHICLE")
    void vehicle_getType_returnsVEHICLE() {
        Vehicle v = new Vehicle("i3", "Toyota", 50000.0, FUTURE, "s1", 30000);
        assertEquals("VEHICLE", v.getType());
    }

    @Test
    @DisplayName("Vehicle: getMileage() trả đúng số km")
    void vehicle_getMileage_correct() {
        Vehicle v = new Vehicle("i3", "Honda Civic", 40000.0, FUTURE, "s1", 15000);
        assertEquals(15000, v.getMileage());
    }

    @Test
    @DisplayName("Vehicle: showDetails() chứa số km")
    void vehicle_showDetails_containsMileage() {
        Vehicle v = new Vehicle("i3", "Honda Civic", 40000.0, FUTURE, "s1", 15000);
        assertTrue(v.showDetails().contains("15000"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POLYMORPHISM qua Item reference
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Polymorphism: Item ref → getType() đúng cho từng subclass")
    void polymorphism_getType_throughItemRef() {
        Item art   = new Art("i1", "Art",   1000, FUTURE, "s1", "Artist");
        Item elec  = new Electronics("i2", "Elec", 2000, FUTURE, "s1", 12);
        Item veh   = new Vehicle("i3", "Car", 3000, FUTURE, "s1", 0);

        assertEquals("ART",         art.getType());
        assertEquals("ELECTRONICS", elec.getType());
        assertEquals("VEHICLE",     veh.getType());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ITEM FACTORY (Factory Pattern)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ItemFactory: tạo ART → trả về instance Art")
    void factory_createArt_returnsArtInstance() {
        Item item = ItemFactory.createItem("ART", "i1", "Painting",
                5000.0, END_STR, "s1", "Da Vinci");
        assertNotNull(item);
        assertInstanceOf(Art.class, item);
        assertEquals("ART", item.getType());
    }

    @Test
    @DisplayName("ItemFactory: tạo ELECTRONICS → trả về instance Electronics")
    void factory_createElectronics_returnsElectronicsInstance() {
        Item item = ItemFactory.createItem("ELECTRONICS", "i2", "Laptop",
                20000.0, END_STR, "s1", "24");
        assertNotNull(item);
        assertInstanceOf(Electronics.class, item);
        assertEquals("ELECTRONICS", item.getType());
    }

    @Test
    @DisplayName("ItemFactory: tạo VEHICLE → trả về instance Vehicle")
    void factory_createVehicle_returnsVehicleInstance() {
        Item item = ItemFactory.createItem("VEHICLE", "i3", "Toyota",
                50000.0, END_STR, "s1", "30000");
        assertNotNull(item);
        assertInstanceOf(Vehicle.class, item);
        assertEquals("VEHICLE", item.getType());
    }

    @Test
    @DisplayName("ItemFactory: type không hợp lệ → ném IllegalArgumentException")
    void factory_invalidType_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                ItemFactory.createItem("UNKNOWN", "i9", "X",
                        100.0, END_STR, "s1", ""));
    }

    @Test
    @DisplayName("ItemFactory: type null → ném IllegalArgumentException")
    void factory_nullType_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                ItemFactory.createItem(null, "i9", "X",
                        100.0, END_STR, "s1", ""));
    }

    @Test
    @DisplayName("ItemFactory: type viết thường cũng chấp nhận (case insensitive)")
    void factory_lowercaseType_accepted() {
        Item item = ItemFactory.createItem("art", "i1", "Painting",
                500.0, END_STR, "s1", "Artist");
        assertInstanceOf(Art.class, item);
    }
}
