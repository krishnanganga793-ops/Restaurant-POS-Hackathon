package com.restaurantpos.database;

import com.restaurantpos.model.MenuItem;
import com.restaurantpos.model.Order;
import com.restaurantpos.model.OrderItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Saves an order and its item snapshots together in one database transaction. */
public class OrderRepository {
    public Order saveOrder(int staffUserId, List<OrderItem> requestedItems) throws SQLException {
        return saveOrder(staffUserId, requestedItems, null);
    }

    public Order saveOrder(int staffUserId, List<OrderItem> requestedItems, Integer tableId)
        throws SQLException {
        try (Connection connection = Database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                if (tableId != null) {
                    occupyTable(connection, tableId);
                }
                List<OrderItem> currentItems = readCurrentAvailableItems(connection, requestedItems);
                Order orderForTotal = new Order(0, staffUserId, currentItems);
                long totalPaise;
                try {
                    totalPaise = orderForTotal.getTotalPaise();
                } catch (ArithmeticException exception) {
                    throw new SQLException("The order total is too large to save.", exception);
                }

                int orderId = insertOrder(connection, staffUserId, totalPaise, tableId);
                insertOrderItems(connection, orderId, currentItems);
                connection.commit();
                return new Order(orderId, staffUserId, currentItems);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void occupyTable(Connection connection, int tableId) throws SQLException {
        String sql = """
            UPDATE restaurant_tables SET status = 'OCCUPIED'
            WHERE table_id = ? AND status = 'AVAILABLE'
              AND NOT EXISTS (
                  SELECT 1 FROM orders WHERE table_id = ? AND status = 'OPEN'
              )
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, tableId);
            statement.setInt(2, tableId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("That table is no longer available for a new order.");
            }
        }
    }

    private List<OrderItem> readCurrentAvailableItems(
        Connection connection, List<OrderItem> requestedItems
    ) throws SQLException {
        String sql = """
            SELECT menu_item_id, name, category, price_paise, available
            FROM menu_items
            WHERE menu_item_id = ?
            """;
        List<OrderItem> currentItems = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (OrderItem requested : requestedItems) {
                statement.setInt(1, requested.getMenuItem().getId());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next() || result.getInt("available") != 1) {
                        throw new SQLException(
                            "A selected item is no longer available. Refresh the menu and try again."
                        );
                    }
                    MenuItem current = new MenuItem(
                        result.getInt("menu_item_id"),
                        result.getString("name"),
                        result.getLong("price_paise"),
                        result.getString("category"),
                        true
                    );
                    currentItems.add(new OrderItem(current, requested.getQuantity()));
                }
            }
        }
        return currentItems;
    }

    private int insertOrder(Connection connection, int staffUserId, long totalPaise, Integer tableId)
        throws SQLException {
        String sql = "INSERT INTO orders (user_id, total_paise, table_id) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(
            sql, Statement.RETURN_GENERATED_KEYS
        )) {
            statement.setInt(1, staffUserId);
            statement.setLong(2, totalPaise);
            if (tableId == null) {
                statement.setNull(3, java.sql.Types.INTEGER);
            } else {
                statement.setInt(3, tableId);
            }
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("The order was saved without a generated order ID.");
    }

    private void insertOrderItems(
        Connection connection, int orderId, List<OrderItem> items
    ) throws SQLException {
        String sql = """
            INSERT INTO order_items
                (order_id, menu_item_id, item_name, unit_price_paise, quantity, subtotal_paise)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (OrderItem item : items) {
                statement.setInt(1, orderId);
                statement.setInt(2, item.getMenuItem().getId());
                statement.setString(3, item.getMenuItem().getName());
                statement.setLong(4, item.getMenuItem().getPricePaise());
                statement.setInt(5, item.getQuantity());
                try {
                    statement.setLong(6, item.getSubtotalPaise());
                } catch (ArithmeticException exception) {
                    throw new SQLException("An order item subtotal is too large to save.", exception);
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
