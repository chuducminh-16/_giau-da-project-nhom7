package com.auction.server.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

public class QueryDB {
    public static void main(String[] args) {
        System.out.println("=== QUERY DATABASE ===");
        queryTable("users");
        queryTable("items");
        queryTable("auctions");
        queryTable("bids");
        queryTable("transactions");
    }

    private static void queryTable(String tableName) {
        System.out.println("\n--- Table: " + tableName + " ---");
        String sql = "SELECT * FROM " + tableName;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            for (int i = 1; i <= cols; i++) {
                System.out.print(meta.getColumnName(i) + "\t");
            }
            System.out.println();
            
            while (rs.next()) {
                for (int i = 1; i <= cols; i++) {
                    System.out.print(rs.getObject(i) + "\t");
                }
                System.out.println();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
