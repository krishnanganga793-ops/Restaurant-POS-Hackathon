package com.restaurantpos.model;

public class MenuItem implements Printable{
    private int id;
    private String name;
    private long pricePaise;
    private String category;
    private boolean available;

    public MenuItem(int id, String name, double price) {
        this(id, name, Math.round(price * 100), "Food", true);
    }

    public MenuItem(int id, String name, long pricePaise, String category, boolean available) {
        this.id = id;
        this.name = name;
        this.pricePaise = pricePaise;
        this.category = category;
        this.available = available;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return pricePaise / 100.0;
    }

    public long getPricePaise() {
        return pricePaise;
    }

    public String getCategory() {
        return category;
    }

    public boolean isAvailable() {
        return available;
    }

    public void displayInfo() {
        System.out.println("Item ID: " + id);
        System.out.println("Item Name: " + name);
        System.out.println("Price: Rs. " + getPrice());
    }
    @Override 
    public void print(){
        displayInfo();
    }
} 
