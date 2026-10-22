package com.restaurantpos.service;

import com.restaurantpos.database.OrderHistoryRepository;
import com.restaurantpos.database.PaymentRepository;
import com.restaurantpos.model.Order;
import com.restaurantpos.model.OrderItem;
import com.restaurantpos.model.Payment;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import com.restaurantpos.database.Database;

/** Builds receipt text from saved order item snapshots and payment records. */
public class ReceiptService {
    private final OrderHistoryRepository orderRepository;
    private final PaymentRepository paymentRepository;

    public ReceiptService() {
        this(new OrderHistoryRepository(), new PaymentRepository());
    }

    public ReceiptService(OrderHistoryRepository orderRepository, PaymentRepository paymentRepository) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
    }

    public String createReceipt(int orderId) throws SQLException {
        Order order = orderRepository.findOrder(orderId);
        if (order == null) {
            throw new SQLException("The selected order could not be found.");
        }
        Payment payment = paymentRepository.findPayment(orderId);
        String loyaltyCustomer = null; long discount = 0; long pointsEarned = 0; long pointsRedeemed = 0;
        try (Connection connection = Database.openConnection(); PreparedStatement statement = connection.prepareStatement(
            "SELECT c.name,o.loyalty_discount_paise,COALESCE(a.points_awarded,0),COALESCE(r.points_redeemed,0) FROM orders o LEFT JOIN customers c ON c.customer_id=o.customer_id LEFT JOIN loyalty_awards a ON a.order_id=o.order_id LEFT JOIN loyalty_redemptions r ON r.order_id=o.order_id WHERE o.order_id=?")) {
            statement.setInt(1,orderId); try(ResultSet result=statement.executeQuery()) { if(result.next()) {
                loyaltyCustomer=result.getString(1);discount=result.getLong(2);pointsEarned=result.getLong(3);pointsRedeemed=result.getLong(4);
            }}
        }

        StringBuilder receipt = new StringBuilder();
        receipt.append("RESTAURANT POS\nRECEIPT\n")
            .append("Order number: #").append(order.getOrderId()).append('\n')
            .append("Date/time: ").append(order.getCreatedAt()).append("\n\n")
            .append("ITEMS\n");
        for (OrderItem item : order.getItems()) {
            receipt.append(item.getMenuItem().getName()).append('\n')
                .append("  Quantity: ").append(item.getQuantity()).append('\n')
                .append("  Unit price: ").append(formatMoney(item.getMenuItem().getPricePaise())).append('\n')
                .append("  Subtotal: ").append(formatMoney(item.getSubtotalPaise())).append("\n\n");
        }
        receipt.append("------------------------------\n")
            .append("Subtotal: ").append(formatMoney(order.getTotalPaise())).append('\n')
            .append("Loyalty discount: ").append(formatMoney(discount)).append('\n')
            .append("Final payable: ").append(formatMoney(order.getTotalPaise()-discount)).append('\n')
            .append("Customer: ").append(loyaltyCustomer == null ? "Guest" : loyaltyCustomer).append('\n')
            .append("Points redeemed: ").append(pointsRedeemed).append('\n')
            .append("Points earned: ").append(pointsEarned).append('\n')
            .append("Payment status: ").append(payment == null ? "UNPAID" : "PAID").append('\n')
            .append("Payment method: ").append(payment == null ? "Not recorded" : payment.getPaymentMethod())
            .append('\n');
        if (payment != null) {
            receipt.append("Paid at: ").append(payment.getRecordedAt()).append('\n');
        }
        receipt.append("Order status: ").append(order.getStatus()).append('\n')
            .append("\nThank you!");
        return receipt.toString();
    }

    private String formatMoney(long paise) {
        return "\u20B9" + BigDecimal.valueOf(paise, 2).toPlainString();
    }
}
