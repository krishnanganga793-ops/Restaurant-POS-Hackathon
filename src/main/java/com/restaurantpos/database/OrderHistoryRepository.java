package com.restaurantpos.database;

import com.restaurantpos.model.MenuItem;
import com.restaurantpos.model.Order;
import com.restaurantpos.model.OrderItem;
import com.restaurantpos.model.OrderSummary;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Reads saved orders and their stored item snapshots for history and receipts. */
public class OrderHistoryRepository {
    public List<OrderSummary> findAll() throws SQLException {
        String sql = """
            SELECT o.order_id, o.created_at, o.total_paise, o.status,
                   CASE WHEN EXISTS (
                       SELECT 1 FROM payments p WHERE p.order_id = o.order_id
                   ) THEN 'PAID' ELSE 'UNPAID' END AS payment_status
            FROM orders o
            ORDER BY o.order_id DESC
            """;
        List<OrderSummary> orders = new ArrayList<>();
        try (Connection connection = Database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                orders.add(new OrderSummary(
                    results.getInt("order_id"),
                    results.getString("created_at"),
                    results.getLong("total_paise"),
                    results.getString("status"),
                    results.getString("payment_status")
                ));
            }
        }
        return orders;
    }

    public Order findOrder(int orderId) throws SQLException {
        String orderSql = """
            SELECT order_id, user_id, created_at, status
            FROM orders WHERE order_id = ?
            """;
        try (Connection connection = Database.openConnection();
             PreparedStatement statement = connection.prepareStatement(orderSql)) {
            statement.setInt(1, orderId);
            int savedOrderId;
            int userId;
            String createdAt;
            String status;
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                savedOrderId = result.getInt("order_id");
                userId = result.getInt("user_id");
                createdAt = result.getString("created_at");
                status = result.getString("status");
            }
            return new Order(savedOrderId, userId, findItems(connection, orderId), createdAt, status);
        }
    }

    private List<OrderItem> findItems(Connection connection, int orderId) throws SQLException {
        String sql = """
            SELECT menu_item_id, item_name, unit_price_paise, quantity
            FROM order_items WHERE order_id = ? ORDER BY order_item_id
            """;
        List<OrderItem> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    MenuItem snapshot = new MenuItem(
                        results.getInt("menu_item_id"),
                        results.getString("item_name"),
                        results.getLong("unit_price_paise"),
                        "",
                        true
                    );
                    items.add(new OrderItem(snapshot, results.getInt("quantity")));
                }
            }
        }
        return items;
    }
}
