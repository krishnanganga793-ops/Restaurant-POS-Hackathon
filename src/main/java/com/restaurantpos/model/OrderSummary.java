package com.restaurantpos.model;

/** The fields shown for one saved order in payment selection and order history. */
public record OrderSummary(
    int orderId,
    String createdAt,
    long totalPaise,
    String status,
    String paymentStatus
) {
}
