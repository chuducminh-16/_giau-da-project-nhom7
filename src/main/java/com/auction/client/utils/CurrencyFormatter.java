package com.auction.client.utils;

import javafx.application.Platform;
import javafx.scene.control.TextField;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * CurrencyFormatter - Tien ich dinh dang so tien su dung chung toan ung dung.
 *
 * Tinh nang:
 *   - Tu dong them dau cham phan cach hang nghin khi nguoi dung go so (vi du: 1.000.000)
 *   - Co the gan vao bat ky TextField nao trong toan ung dung
 *   - Xu ly an toan: chi chap nhan ky tu so, bo qua ky tu khac
 *
 * Su dung:
 *   CurrencyFormatter.attach(txtStartingPrice);
 *   CurrencyFormatter.attach(txtBidIncrement);
 */
public class CurrencyFormatter {

    private CurrencyFormatter() {} // Utility class, khong cho khoi tao

    /**
     * Gan bo lang nghe tu dong dinh dang so tien vao TextField.
     * Nguoi dung go gi, so se tu dong them dau cham phan cach hang nghin.
     *
     * @param field TextField can gan dinh dang
     */
    public static void attach(TextField field) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator('.');
        DecimalFormat formatter = new DecimalFormat("#,###", symbols);

        field.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.isEmpty()) return;

            String cleanString = newValue.replaceAll("[^\\d]", "");
            if (cleanString.isEmpty()) {
                field.setText("");
                return;
            }

            try {
                double parsed = Double.parseDouble(cleanString);
                String formatted = formatter.format(parsed);
                if (!newValue.equals(formatted)) {
                    Platform.runLater(() -> {
                        field.setText(formatted);
                        field.positionCaret(formatted.length());
                    });
                }
            } catch (NumberFormatException ignored) {}
        });
    }

    /**
     * Chuyen doi so double thanh chuoi co dau cham phan cach hang nghin.
     * Vi du: 2000000.0 -> "2.000.000"
     *
     * @param value Gia tri so can dinh dang
     * @return Chuoi da dinh dang
     */
    public static String format(double value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator('.');
        DecimalFormat formatter = new DecimalFormat("#,###", symbols);
        return formatter.format(value);
    }

    /**
     * Lay gia tri so nguyen tu TextField co chua so da dinh dang (co dau cham).
     * Vi du: "1.500.000" -> 1500000.0
     *
     * @param field TextField can lay gia tri
     * @return Gia tri so nguyen, hoac 0 neu khong hop le
     */
    public static double getValue(TextField field) {
        String clean = field.getText().trim().replaceAll("\\.", "");
        if (clean.isEmpty()) return 0;
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}