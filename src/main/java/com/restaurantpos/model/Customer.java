package com.restaurantpos.model;

public record Customer(int customerId, String name, String phone) {
    @Override
    public String toString() { return name + " (" + phone + ")"; }
}
