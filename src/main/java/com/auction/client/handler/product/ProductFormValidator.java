package com.auction.client.handler.product;

import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import java.time.LocalDateTime;

/**
 * 🩺 BỘ THẨM ĐỊNH FORM SẢN PHẨM (PRODUCT FORM VALIDATOR)
 * - Mục đích: Tách biệt toàn bộ logic kiểm tra chuỗi dữ liệu đầu vào, ép phím số thời gian.
 * - Cập nhật mới: Hỗ trợ tính năng dùng phím mũi tên TRÁI/PHẢI để di chuyển nhanh giữa các ô Giờ, Phút, Giây.
 */
public class ProductFormValidator {

    /**
     * 🕹️ KẾT NỐI ĐIỀU HƯỚNG PHÍM MŨI TÊN GIỮA BỘ BA THỜI GIAN
     * - Bấm mũi tên PHẢI khi con trỏ ở cuối text -> Nhảy sang ô tiếp theo.
     * - Bấm mũi tên TRÁI khi con trỏ ở đầu text -> Quay về ô phía trước.
     */
    public static void linkTimeFieldsNavigation(TextField hourField, TextField minuteField, TextField secondField) {
        if (hourField == null || minuteField == null || secondField == null) return;

        configureArrowNavigation(hourField, null, minuteField);
        configureArrowNavigation(minuteField, hourField, secondField);
        configureArrowNavigation(secondField, minuteField, null);
    }

    private static void configureArrowNavigation(TextField current, TextField previous, TextField next) {
        current.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            int caretPosition = current.getCaretPosition();
            int textLength = current.getLength();

            if (event.getCode() == KeyCode.RIGHT) {
                // Nếu con trỏ chuột đang ở cuối dòng text (hoặc ô đang trống), bấm sang phải sẽ chuyển ô
                if (caretPosition == textLength && next != null) {
                    next.requestFocus();
                    next.positionCaret(0); // Đặt con trỏ ở đầu ô tiếp theo
                    event.consume(); // Chặn sự kiện mặc định để tránh nhảy tab ngoài ý muốn
                }
            } else if (event.getCode() == KeyCode.LEFT) {
                // Nếu con trỏ chuột đang ở đầu dòng text (vị trí số 0), bấm sang trái sẽ lùi ô
                if (caretPosition == 0 && previous != null) {
                    previous.requestFocus();
                    previous.positionCaret(previous.getLength()); // Đặt con trỏ ở cuối ô phía trước
                    event.consume();
                }
            }
        });
    }

    /**
     * Gắn bộ lắng nghe trực tiếp lên các ô nhập giờ/phút/giây để giới hạn người dùng không nhập quá mốc quy định
     */
    public static void limitTimeField(TextField field, int maxValue) {
        if (field == null) return;
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) {
                field.setText(newVal.replaceAll("[^\\d]", ""));
                return;
            }
            if (newVal.length() > 2) { 
                field.setText(oldVal); 
                return; 
            }
            if (!newVal.isEmpty()) {
                try {
                    if (Integer.parseInt(newVal) > maxValue)
                        field.setText(String.valueOf(maxValue));
                } catch (NumberFormatException ignored) {}
            }
        });
    }

    /**
     * Thẩm định toàn diện các ô thông tin trên giao diện trước khi đóng gói gửi lên Server
     */
    public static boolean validateProductForm(
            String name, String priceStr, String incrementStr,
            DatePicker dpStart, DatePicker dpEnd,
            TextField sh, TextField sm, TextField ss,
            TextField eh, TextField em, TextField es,
            java.util.function.BiConsumer<String, Boolean> statusCallback) {

        if (name.isEmpty()) {
            statusCallback.accept("⚠ Tên sản phẩm không được để trống.", true);
            return false;
        }

        String cleanPrice = priceStr != null ? priceStr.replaceAll("\\.", "") : "";
        String cleanIncrement = incrementStr != null ? incrementStr.replaceAll("\\.", "") : "";

        if (cleanPrice.isEmpty() || cleanIncrement.isEmpty()) {
            statusCallback.accept("⚠ Giá tiền và bước giá không được để trống.", true);
            return false;
        }

        try {
            double price = Double.parseDouble(cleanPrice);
            double increment = Double.parseDouble(cleanIncrement);

            if (price <= 0 || increment <= 0) {
                statusCallback.accept("⚠ Giá khởi điểm và bước giá phải lớn hơn 0.", true);
                return false;
            }
        } catch (NumberFormatException e) {
            statusCallback.accept("⚠ Giá tiền và bước giá phải là số nguyên hợp lệ.", true);
            return false;
        }

        if (dpEnd.getValue() == null) {
            statusCallback.accept("⚠ Vui lòng chọn ngày kết thúc.", true);
            return false;
        }
        if (dpStart.getValue() != null && dpStart.getValue().isAfter(dpEnd.getValue())) {
            statusCallback.accept("⚠ Ngày bắt đầu phải trước ngày kết thúc.", true);
            return false;
        }
        if (!isValidTime(sh, sm, ss)) {
            statusCallback.accept("⚠ Giờ bắt đầu không hợp lệ.", true);
            return false;
        }
        if (!isValidTime(eh, em, es)) {
            statusCallback.accept("⚠ Giờ kết thúc không hợp lệ.", true);
            return false;
        }
        return true;
    }

    private static boolean isValidTime(TextField h, TextField m, TextField s) {
        try {
            int hour   = h.getText().isEmpty() ? 0 : Integer.parseInt(h.getText());
            int minute = m.getText().isEmpty() ? 0 : Integer.parseInt(m.getText());
            int second = s.getText().isEmpty() ? 0 : Integer.parseInt(s.getText());
            return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59 && second >= 0 && second <= 59;
        } catch (NumberFormatException e) { 
            return false; 
        }
    }

    public static LocalDateTime buildDateTime(DatePicker dp, TextField h, TextField m, TextField s, LocalDateTime fallback) {
        if (dp.getValue() == null) return fallback;
        return dp.getValue().atTime(parseTimeField(h, 0), parseTimeField(m, 0), parseTimeField(s, 0));
    }

    private static int parseTimeField(TextField field, int def) {
        try {
            String t = field.getText().trim();
            return t.isEmpty() ? def : Integer.parseInt(t);
        } catch (NumberFormatException e) { 
            return def; 
        }
    }
}