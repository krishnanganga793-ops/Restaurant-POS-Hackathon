package com.restaurantpos.model;

/** Database-backed sales and order totals for the current local calendar day. */
public record SalesDashboardSummary(
    long salesTodayPaise,
    long ordersCreatedToday,
    long paidOrdersCount,
    long unpaidOrdersCount,
    long averageOrderValuePaise
) {
}
