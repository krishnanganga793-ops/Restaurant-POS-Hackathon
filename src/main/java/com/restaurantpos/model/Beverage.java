package com.restaurantpos.model;

public class Beverage extends MenuItem {

    public Beverage(int id, String name, double price) {
        super(id, name, Math.round(price * 100), "Beverage", true);
    }

    @Override
    public void displayInfo() {
        System.out.println("Category: Beverage");
        super.displayInfo();
    }
}
