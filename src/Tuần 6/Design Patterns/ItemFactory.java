package Tuần 6.Design Patterns;

// Lớp Factory
public class ItemFactory {
    
    // Factory Method
    public static Item createItem(String type, String name, String description) {
        if (type == null) return null;  // Kiểm tra nếu type là null, trả về null hoặc có thể ném một ngoại lệ tùy theo yêu cầu
        
        switch (type.toLowerCase()) {
            case "electronics":
                return new Electronics(name, description); // Tạo một đối tượng Electronics
            case "art":       
                return new Art(name, description);
            case "vehicle":
                return new Vehicle(name, description);
            default:
                throw new IllegalArgumentException("Loại Item không hợp lệ: " + type);  // Xử lý lỗi nếu loại không hợp lệ
        }
    }
}
