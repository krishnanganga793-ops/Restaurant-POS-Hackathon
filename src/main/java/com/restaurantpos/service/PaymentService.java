package com.restaurantpos.service;

import com.restaurantpos.database.PaymentRepository;
import com.restaurantpos.model.OrderSummary;
import com.restaurantpos.model.Payment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.List;

/** Validates full-payment input before asking the repository to record it. */
public class PaymentService {
    private final PaymentRepository repository;

    public PaymentService() {
        this(new PaymentRepository());
    }

    public PaymentService(PaymentRepository repository) {
        this.repository = repository;
    }

    public List<OrderSummary> getUnpaidOrders() throws SQLException {
        return repository.findUnpaidOrders();
    }

    public Payment recordPayment(int orderId, String amount, String method) throws SQLException {
        if (orderId <= 0) {
            throw new IllegalArgumentException("Select a saved order first.");
        }
        if (method == null || !List.of("CASH", "CARD", "UPI", "OTHER").contains(method)) {
            throw new IllegalArgumentException("Choose a valid payment method.");
        }
        long amountPaise = parseAmount(amount);
        return repository.recordFullPayment(orderId, amountPaise, method);
    }

    private long parseAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            throw new IllegalArgumentException("Enter the payment amount.");
        }
        try {
            BigDecimal rupees = new BigDecimal(amount.trim());
            if (rupees.signum() <= 0) {
                throw new IllegalArgumentException("Payment amount must be greater than ₹0.00.");
            }
            return rupees.setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2).longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Enter a valid amount with up to two decimal places.");
        }
    }
}
