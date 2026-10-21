package com.restaurantpos.model;

public class Payment {
    private int paymentId;
    private int orderId;
    private long amountPaise;
    private String paymentMethod;
    private String recordedAt;

    public Payment(double amount, String paymentMethod) {
        this(0, 0, Math.round(amount * 100), paymentMethod, "");
    }

    public Payment(int paymentId, int orderId, long amountPaise,
                   String paymentMethod, String recordedAt) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.amountPaise = amountPaise;
        this.paymentMethod = paymentMethod;
        this.recordedAt = recordedAt;
    }

    public int getPaymentId() {
        return paymentId;
    }

    public int getOrderId() {
        return orderId;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public double getAmount() {
        return amountPaise / 100.0;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getRecordedAt() {
        return recordedAt;
    }

    public void displayPayment() {
        System.out.println("Payment Method: " + paymentMethod);
        System.out.println("Amount Paid: Rs. " + getAmount());
    }
}
