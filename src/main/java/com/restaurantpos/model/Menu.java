package com.restaurantpos.model;

import java.util.ArrayList;
import java.util.List;

public class Menu {
    private List<MenuItem> items;

    public Menu() {
        items = new ArrayList<>();
    }

    public void addItem(MenuItem item) {
        items.add(item);
    }

    public void displayMenu() {
        System.out.println("\n===== RESTAURANT MENU =====");

        for (MenuItem item : items) {
            item.displayInfo();
            System.out.println("---------------------------");
        }
    }
}
