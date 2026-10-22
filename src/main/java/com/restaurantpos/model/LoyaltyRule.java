package com.restaurantpos.model;

public record LoyaltyRule(int ruleId, int menuItemId, String itemName, String category,
                          boolean itemAvailable, int minimumQuantity, int pointsAwarded,
                          boolean active) {
    @Override
    public String toString() { return itemName + " (min " + minimumQuantity + ", " + pointsAwarded + " pts)"; }
}
