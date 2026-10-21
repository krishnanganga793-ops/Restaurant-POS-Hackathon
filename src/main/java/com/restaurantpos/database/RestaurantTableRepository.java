package com.restaurantpos.database;

import com.restaurantpos.model.RestaurantTable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Reads table cards and performs reservation changes in SQLite. */
public class RestaurantTableRepository {
    public List<RestaurantTable> findAll() throws SQLException {
        String sql = """
            SELECT t.table_id, t.capacity, t.status,
                   (SELECT o.order_id FROM orders o
                    WHERE o.table_id = t.table_id AND o.status = 'OPEN'
                    ORDER BY o.order_id DESC LIMIT 1) AS current_order_id,
                   (SELECT o.total_paise FROM orders o
                    WHERE o.table_id = t.table_id AND o.status = 'OPEN'
                    ORDER BY o.order_id DESC LIMIT 1) AS current_order_total
            FROM restaurant_tables t ORDER BY t.table_id
            """;
        List<RestaurantTable> tables = new ArrayList<>();
        try (Connection connection = Database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                int orderId = result.getInt("current_order_id");
                Integer currentOrderId = result.wasNull() ? null : orderId;
                tables.add(new RestaurantTable(
                    result.getInt("table_id"), result.getInt("capacity"),
                    result.getString("status"), currentOrderId,
                    result.getLong("current_order_total")
                ));
            }
        }
        return tables;
    }

    public void reserve(int tableId) throws SQLException {
        updateReservedStatus(tableId, "AVAILABLE", "RESERVED",
            "Only an available table can be reserved.");
    }

    public void cancelReservation(int tableId) throws SQLException {
        updateReservedStatus(tableId, "RESERVED", "AVAILABLE",
            "This table does not have an active reservation.");
    }

    private void updateReservedStatus(int tableId, String expected, String updated, String error)
        throws SQLException {
        String sql = "UPDATE restaurant_tables SET status = ? WHERE table_id = ? AND status = ?";
        try (Connection connection = Database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, updated);
            statement.setInt(2, tableId);
            statement.setString(3, expected);
            if (statement.executeUpdate() != 1) {
                throw new SQLException(error);
            }
        }
    }
}
