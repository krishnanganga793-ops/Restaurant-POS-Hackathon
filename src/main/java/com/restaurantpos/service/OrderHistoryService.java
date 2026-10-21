package com.restaurantpos.service;

import com.restaurantpos.database.OrderHistoryRepository;
import com.restaurantpos.model.Order;
import com.restaurantpos.model.OrderSummary;
import java.sql.SQLException;
import java.util.List;

/** Provides saved order summaries and details for the history screen. */
public class OrderHistoryService {
    private final OrderHistoryRepository repository;

    public OrderHistoryService() {
        this(new OrderHistoryRepository());
    }

    public OrderHistoryService(OrderHistoryRepository repository) {
        this.repository = repository;
    }

    public List<OrderSummary> getOrderHistory() throws SQLException {
        return repository.findAll();
    }

    public Order getOrder(int orderId) throws SQLException {
        Order order = repository.findOrder(orderId);
        if (order == null) {
            throw new SQLException("The selected order could not be found.");
        }
        return order;
    }
}
