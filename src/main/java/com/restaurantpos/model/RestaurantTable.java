package com.restaurantpos.model;

/** Persisted table status plus its open order, when the table is occupied. */
public record RestaurantTable(
    int tableId,
    int capacity,
    String status,
    Integer currentOrderId,
    long currentOrderTotalPaise
) {
}
