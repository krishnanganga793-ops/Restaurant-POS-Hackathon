package com.restaurantpos.model;

public class Receipt implements Printable {
    private Order order;
    private Payment payment;

    public Receipt(Order order, Payment payment) {
        this.order = order;
        this.payment = payment;
    }

    @Override
    public void print() {
        System.out.println("\n===== RESTAURANT RECEIPT =====");

        order.displayOrder();

        System.out.println("\n----- PAYMENT DETAILS -----");
        payment.displayPayment();

        System.out.println("=============================");
        System.out.println("Thank you for visiting!");
    }
}
