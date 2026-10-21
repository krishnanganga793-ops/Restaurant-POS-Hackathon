package com.restaurantpos.service;

import com.restaurantpos.database.MenuItemRepository;
import com.restaurantpos.database.OrderRepository;
import com.restaurantpos.model.MenuItem;
import com.restaurantpos.model.Order;
import com.restaurantpos.model.OrderItem;
import java.sql.SQLException;
import java.util.List;

/** Applies order and cart rules before asking repositories to access SQLite. */
public class OrderService {
    private final MenuItemRepository menuRepository;
    private final OrderRepository orderRepository;

    public OrderService() {
        this(new MenuItemRepository(), new OrderRepository());
    }

    public OrderService(MenuItemRepository menuRepository, OrderRepository orderRepository) {
        this.menuRepository = menuRepository;
        this.orderRepository = orderRepository;
    }

    public List<MenuItem> getAvailableMenuItems() throws SQLException {
        return menuRepository.findAvailable();
    }

    public Order saveOrder(int staffUserId, List<OrderItem> cartItems) throws SQLException {
        return saveOrder(staffUserId, cartItems, null);
    }

    public Order saveOrder(int staffUserId, List<OrderItem> cartItems, Integer tableId)
        throws SQLException {
        if (staffUserId <= 0) {
            throw new IllegalArgumentException("The signed-in staff account is not valid.");
        }
        if (cartItems == null || cartItems.isEmpty()) {
            throw new IllegalArgumentException("Add at least one item before saving the order.");
        }
        for (OrderItem item : cartItems) {
            if (item == null || item.getMenuItem() == null) {
                throw new IllegalArgumentException("Remove the invalid cart entry and try again.");
            }
            if (item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Each quantity must be greater than zero.");
            }
            if (!item.getMenuItem().isAvailable()) {
                throw new IllegalArgumentException(
                    "Unavailable menu items cannot be added to a new order."
                );
            }
        }
        return orderRepository.saveOrder(staffUserId, List.copyOf(cartItems), tableId);
    }
}
