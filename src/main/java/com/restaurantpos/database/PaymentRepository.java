package com.restaurantpos.database;

import com.restaurantpos.model.OrderSummary;
import com.restaurantpos.model.Payment;
import com.restaurantpos.model.LoyaltyCheckout;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Records a full payment and changes the order status atomically. */
public class PaymentRepository {
    public LoyaltyCheckout getLoyaltyCheckout(int orderId, int requestedPoints) throws SQLException {
        try (Connection c = Database.openConnection()) {
            String sql = "SELECT o.total_paise,o.customer_id,c.name,COALESCE((SELECT SUM(CASE movement_type WHEN 'EARN' THEN points ELSE -points END) FROM loyalty_ledger l WHERE l.customer_id=o.customer_id),0) balance,(SELECT setting_value FROM loyalty_settings WHERE setting_key='points_per_rupee') ratio FROM orders o LEFT JOIN customers c ON c.customer_id=o.customer_id WHERE o.order_id=?";
            try (PreparedStatement s=c.prepareStatement(sql)) { s.setInt(1,orderId); try(ResultSet r=s.executeQuery()) {
                if(!r.next()) throw new SQLException("The selected order could not be found.");
                int rawId=r.getInt("customer_id"); Integer customerId=r.wasNull()?null:rawId;
                long balance=Math.max(0L,r.getLong("balance")), subtotal=r.getLong("total_paise"); int ratio=r.getInt("ratio");
                validateRedemption(customerId, balance, requestedPoints);
                long maxDiscount=subtotal>1?((subtotal-1)/100L)*100L:0;
                long discount=customerId==null?0:Math.min((long)(requestedPoints/ratio)*100L,maxDiscount);
                return new LoyaltyCheckout(customerId,r.getString("name"),balance,ratio,subtotal,discount);
            }}
        }
    }
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

    public Payment recordFullPayment(int orderId, long amountPaise, String method, int requestedPoints)
        throws SQLException {
        try (Connection connection = Database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                long subtotalPaise = getUnpaidAmount(connection, orderId);
                LoyaltyCheckout quote = loyaltyCheckout(connection, orderId, requestedPoints);
                validateRedemption(quote.customerId(), quote.availablePoints(), requestedPoints);
                long discountPaise = quote.discountPaise();
                int pointsToDebit = (int) Math.min((long) requestedPoints,
                    (discountPaise / 100L) * quote.pointsPerRupee());
                long duePaise = subtotalPaise - discountPaise;
                Integer tableId = findOrderTableId(connection, orderId);
                if (amountPaise != duePaise) {
                    throw new SQLException("Payment amount must match the final amount after discount.");
                }

                if (discountPaise > 0) {
                    try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE orders SET loyalty_discount_paise=? WHERE order_id=? AND status='OPEN'")) {
                        update.setLong(1,discountPaise); update.setInt(2,orderId); update.executeUpdate();
                    }
                    insertMovement(connection, quote.customerId(), orderId, "REDEEM", pointsToDebit);
                    try (PreparedStatement redemption=connection.prepareStatement(
                        "INSERT INTO loyalty_redemptions(order_id,customer_id,points_redeemed,discount_paise) VALUES(?,?,?,?)")) {
                        redemption.setInt(1,orderId);redemption.setInt(2,quote.customerId());redemption.setInt(3,pointsToDebit);redemption.setLong(4,discountPaise);redemption.executeUpdate();
                    }
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

                if (quote.customerId() != null) {
                    int earned = calculateEarned(connection, orderId);
                    try (PreparedStatement award=connection.prepareStatement(
                        "INSERT INTO loyalty_awards(order_id,customer_id,points_awarded) VALUES(?,?,?)")) {
                        award.setInt(1,orderId);award.setInt(2,quote.customerId());award.setInt(3,earned);award.executeUpdate();
                    }
                    if(earned>0) insertMovement(connection,quote.customerId(),orderId,"EARN",earned);
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

    private LoyaltyCheckout loyaltyCheckout(Connection c,int orderId,int points)throws SQLException {
        String sql="SELECT o.total_paise,o.customer_id,c.name,COALESCE((SELECT SUM(CASE movement_type WHEN 'EARN' THEN points ELSE -points END) FROM loyalty_ledger l WHERE l.customer_id=o.customer_id),0) balance,(SELECT setting_value FROM loyalty_settings WHERE setting_key='points_per_rupee') ratio FROM orders o LEFT JOIN customers c ON c.customer_id=o.customer_id WHERE o.order_id=?";
        try(PreparedStatement s=c.prepareStatement(sql)){s.setInt(1,orderId);try(ResultSet r=s.executeQuery()){
            if(!r.next())throw new SQLException("The selected order could not be found.");int id=r.getInt("customer_id");Integer customerId=r.wasNull()?null:id;long subtotal=r.getLong("total_paise"),balance=Math.max(0L,r.getLong("balance"));int ratio=r.getInt("ratio");
            validateRedemption(customerId, balance, points);long maxDiscount=subtotal>1?((subtotal-1)/100L)*100L:0;long discount=customerId==null?0:Math.min((long)(points/ratio)*100L,maxDiscount);
            return new LoyaltyCheckout(customerId,r.getString("name"),balance,ratio,subtotal,discount);
        }}
    }
    private void validateRedemption(Integer customerId, long balance, int points) throws SQLException {
        if (points < 0) throw new SQLException("Points to redeem cannot be negative.");
        if (points > 0 && customerId == null) throw new SQLException("Guest orders cannot redeem loyalty points.");
        if (points > balance) throw new SQLException("Customer does not have enough loyalty points.");
    }
    private int calculateEarned(Connection c,int orderId)throws SQLException {
        String sql="SELECT COALESCE(SUM(r.points_awarded),0) FROM loyalty_rules r JOIN menu_items m ON m.menu_item_id=r.menu_item_id AND m.category='Food' AND m.available=1 JOIN order_items i ON i.menu_item_id=r.menu_item_id WHERE i.order_id=? AND r.active=1 AND i.quantity>=r.minimum_quantity";
        try(PreparedStatement s=c.prepareStatement(sql)){s.setInt(1,orderId);try(ResultSet r=s.executeQuery()){r.next();return r.getInt(1);}}
    }
    private void insertMovement(Connection c,int customerId,int orderId,String type,int points)throws SQLException {
        if(points<=0) return;
        try(PreparedStatement s=c.prepareStatement("INSERT INTO loyalty_ledger(customer_id,order_id,movement_type,points) VALUES(?,?,?,?)")){s.setInt(1,customerId);s.setInt(2,orderId);s.setString(3,type);s.setInt(4,points);s.executeUpdate();}
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
