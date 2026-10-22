package com.restaurantpos.model;

public record LoyaltyCheckout(Integer customerId, String customerName, long availablePoints,
                              int pointsPerRupee, long subtotalPaise, long discountPaise) { }
