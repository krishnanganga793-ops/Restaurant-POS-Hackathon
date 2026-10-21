package com.restaurantpos.database;

import com.restaurantpos.model.OrderSummary;
import com.restaurantpos.model.Payment;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Records a full payment and changes the order status atomically. */
public class PaymentRepository {
    public List<OrderSummary> findUnpaidOrders() throws SQLException {
        String sql = """
            SELECT o.order_id, o.created_at, o.total_paise, o.status, 'UNPAID' AS payment_status
            FROM orders o
            WHERE o.status = 'OPEN'
              AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.order_id)
            ORDER BY o.order_id DESC
            """;
        List<OrderSummary> orders = new ArrayList<>();
        try (Connection connection = Database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                orders.add(new OrderSummary(
                    results.getInt("order_id"), results.getString("created_at"),
                    results.getLong("total_paise"), results.getString("status"),
                    results.getString("payment_status")
                ));
            }
        }
        return orders;
    }

    public Payment recordFullPayment(int orderId, long amountPaise, String method)
        throws SQLException {
        try (Connection connection = Database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                long duePaise = getUnpaidAmount(connection, orderId);
                Integer tableId = findOrderTableId(connection, orderId);
                if (amountPaise != duePaise) {
                    throw new SQLException("Payment amount must match the full order total.");
                }

                String insertSql = """
                    INSERT INTO payments (order_id, amount_paise, payment_method)
                    VALUES (?, ?, ?)
                    """;
                try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                    insert.setInt(1, orderId);
                    insert.setLong(2, amountPaise);
                    insert.setString(3, method);
                    insert.executeUpdate();
                }

                String updateSql = "UPDATE orders SET status = 'PAID' WHERE order_id = ? AND status = 'OPEN'";
                try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                    update.setInt(1, orderId);
                    if (update.executeUpdate() != 1) {
                        throw new SQLException("This order has already been paid or changed.");
                    }
                }

                if (tableId != null) {
                    try (PreparedStatement release = connection.prepareStatement(
                        "UPDATE restaurant_tables SET status = 'AVAILABLE' "
                            + "WHERE table_id = ? AND status = 'OCCUPIED'")) {
                        release.setInt(1, tableId);
                        if (release.executeUpdate() != 1) {
                            throw new SQLException(
                                "The order was paid, but its table status could not be updated."
                            );
                        }
                    }
                }

                Payment savedPayment = findPayment(connection, orderId);
                connection.commit();
                return savedPayment;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private Integer findOrderTableId(Connection connection, int orderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT table_id FROM orders WHERE order_id = ?")) {
            statement.setInt(1, orderId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("The selected order could not be found.");
                }
                int tableId = result.getInt("table_id");
                return result.wasNull() ? null : tableId;
            }
        }
    }

    public Payment findPayment(int orderId) throws SQLException {
        try (Connection connection = Database.openConnection()) {
            return findPayment(connection, orderId);
        }
    }

    private long getUnpaidAmount(Connection connection, int orderId) throws SQLException {
        String sql = """
            SELECT o.total_paise, o.status,
                   EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.order_id) AS has_payment
            FROM orders o WHERE o.order_id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("The selected order could not be found.");
                }
                if (!"OPEN".equals(result.getString("status")) || result.getInt("has_payment") != 0) {
                    throw new SQLException("This order has already been paid or is not payable.");
                }
                return result.getLong("total_paise");
            }
        }
    }

    private Payment findPayment(Connection connection, int orderId) throws SQLException {
        String sql = """
            SELECT payment_id, order_id, amount_paise, payment_method, recorded_at
            FROM payments WHERE order_id = ? ORDER BY payment_id DESC LIMIT 1
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new Payment(
                    result.getInt("payment_id"), result.getInt("order_id"),
                    result.getLong("amount_paise"), result.getString("payment_method"),
                    result.getString("recorded_at")
                );
            }
        }
    }
}
