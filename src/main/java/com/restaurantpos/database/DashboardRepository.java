package com.restaurantpos.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Reads the current day's dashboard totals from saved orders and payments. */
public class DashboardRepository {
    public DashboardData readToday() throws SQLException {
        String sql = """
            SELECT
                (SELECT COALESCE(SUM(p.amount_paise), 0)
                 FROM payments p
                 WHERE date(p.recorded_at, 'localtime') = date('now', 'localtime')) AS sales_today,
                (SELECT COUNT(*)
                 FROM orders o
                 WHERE date(o.created_at, 'localtime') = date('now', 'localtime')) AS orders_today,
                (SELECT COUNT(*)
                 FROM orders o
                 WHERE EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.order_id)) AS paid_orders,
                (SELECT COUNT(*)
                 FROM orders o
                 WHERE o.status = 'OPEN'
                   AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.order_id)) AS unpaid_today,
                (SELECT COALESCE(SUM(o.total_paise), 0)
                 FROM orders o
                 WHERE o.status <> 'CANCELLED') AS order_value,
                (SELECT COUNT(*)
                 FROM orders o
                 WHERE o.status <> 'CANCELLED') AS order_count_for_average
            """;
        try (Connection connection = Database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                return new DashboardData(0, 0, 0, 0, 0, 0);
            }
            return new DashboardData(
                result.getLong("sales_today"),
                result.getLong("orders_today"),
                result.getLong("paid_orders"),
                result.getLong("unpaid_today"),
                result.getLong("order_value"),
                result.getLong("order_count_for_average")
            );
        }
    }

    public record DashboardData(
        long salesTodayPaise,
        long ordersCreatedToday,
        long paidOrdersCount,
        long unpaidOrdersCount,
        long orderValueTodayPaise,
        long averageOrderCount
    ) {
    }
}
