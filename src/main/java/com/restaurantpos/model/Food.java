package com.restaurantpos.model;

public class Food extends MenuItem {

    public Food(int id, String name, double price) {
        super(id, name, Math.round(price * 100), "Food", true);
    }

    @Override
    public void displayInfo() {
        System.out.println("Category: Food");
        super.displayInfo();
    }
}
