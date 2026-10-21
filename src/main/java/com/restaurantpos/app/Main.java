package com.restaurantpos.app;

import com.restaurantpos.model.Beverage;
import com.restaurantpos.model.Food;
import com.restaurantpos.model.Order;
import com.restaurantpos.model.OrderItem;
import com.restaurantpos.model.Payment;
import com.restaurantpos.model.Receipt;

public class Main {
    public static void main(String[] args) {
        Food food1 = new Food(1, "Chicken Biryani", 180.00);
        Beverage beverage1 = new Beverage(2, "Fresh Lime Juice", 50.00);

        Order order = new Order(101);

        OrderItem item1 = new OrderItem(food1, 2);
        OrderItem item2 = new OrderItem(beverage1, 3);

        order.addItem(item1);
        order.addItem(item2);

        Payment payment = new Payment(order.getTotal(), "UPI");

        Receipt receipt = new Receipt(order, payment);
        receipt.print();
    }
}
