package com.restaurantpos.model;

import java.util.ArrayList;
import java.util.List;

public class Order {
    private int orderId;
    private int userId;
    private List<OrderItem> items;
    private String createdAt;
    private String status;

    public Order(int orderId) {
        this.orderId = orderId;
        this.items = new ArrayList<>();
    }

    public Order(int orderId, int userId, List<OrderItem> items) {
        this(orderId, userId, items, "", "OPEN");
    }

    public Order(int orderId, int userId, List<OrderItem> items, String createdAt, String status) {
        this(orderId);
        this.userId = userId;
        this.items.addAll(items);
        this.createdAt = createdAt;
        this.status = status;
    }

    public int getOrderId() {
        return orderId;
    }

    public int getUserId() {
        return userId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getStatus() {
        return status;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    public void addItem(OrderItem item) {
        items.add(item);
    }

    public double getTotal() {
        return getTotalPaise() / 100.0;
    }

    public long getTotalPaise() {
        long total = 0;
        for (OrderItem item : items) {
            total = Math.addExact(total, item.getSubtotalPaise());
        }
        return total;
    }

    public void displayOrder() {
        System.out.println("Order ID: " + orderId);
        System.out.println("----- ORDER ITEMS -----");

        for (OrderItem item : items) {
            System.out.println(
                item.getMenuItem().getName()
                + " x " + item.getQuantity()
                + " = Rs. " + item.getSubtotal()
            );
        }

        System.out.println("-----------------------");
        System.out.println("Total: Rs. " + getTotal());
    }
}
