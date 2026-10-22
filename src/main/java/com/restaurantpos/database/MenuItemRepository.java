package com.restaurantpos.database;

import com.restaurantpos.model.MenuItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Runs the SQL statements used to read and update menu items. */
public class MenuItemRepository {
    public List<MenuItem> findAll() throws SQLException {
        String sql = """
            SELECT menu_item_id, name, category, price_paise, available
            FROM menu_items
            ORDER BY category, name COLLATE NOCASE
            """;
        List<MenuItem> items = new ArrayList<>();
        try (Connection connection = Database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                items.add(readMenuItem(results));
            }
        }
        return items;
    }

    /** Returns only menu items that can be selected for a new order. */
    public List<MenuItem> findAvailable() throws SQLException {
        String sql = """
            SELECT menu_item_id, name, category, price_paise, available
            FROM menu_items
            WHERE available = 1
            ORDER BY category, name COLLATE NOCASE
            """;
        List<MenuItem> items = new ArrayList<>();
        try (Connection connection = Database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                items.add(readMenuItem(results));
            }
        }
        return items;
    }

    public void add(String name, String category, long pricePaise) throws SQLException {
        String sql = """
            INSERT INTO menu_items (name, category, price_paise, available)
            VALUES (?, ?, ?, 1)
            """;
        try (Connection connection = Database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, category);
            statement.setLong(3, pricePaise);
            statement.executeUpdate();
        }
    }

    public void update(MenuItem item) throws SQLException {
        String sql = """
            UPDATE menu_items
            SET name = ?, category = ?, price_paise = ?, available = ?
            WHERE menu_item_id = ?
            """;
        try (Connection connection = Database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, item.getName());
            statement.setString(2, item.getCategory());
            statement.setLong(3, item.getPricePaise());
            statement.setInt(4, item.isAvailable() ? 1 : 0);
            statement.setInt(5, item.getId());
            statement.executeUpdate();
        }
    }

    public void markUnavailable(int itemId) throws SQLException {
        String sql = "UPDATE menu_items SET available = 0 WHERE menu_item_id = ?";
        try (Connection connection = Database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, itemId);
            statement.executeUpdate();
        }
    }

    public void setAvailability(int itemId, boolean available) throws SQLException {
        String sql = "UPDATE menu_items SET available = ? WHERE menu_item_id = ?";
        try (Connection connection = Database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, available ? 1 : 0);
            statement.setInt(2, itemId);
            statement.executeUpdate();
        }
    }

    private MenuItem readMenuItem(ResultSet results) throws SQLException {
        return new MenuItem(
            results.getInt("menu_item_id"),
            results.getString("name"),
            results.getLong("price_paise"),
            results.getString("category"),
            results.getInt("available") == 1
        );
    }
}
