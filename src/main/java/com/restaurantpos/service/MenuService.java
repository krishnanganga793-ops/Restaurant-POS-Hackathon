package com.restaurantpos.service;

import com.restaurantpos.database.MenuItemRepository;
import com.restaurantpos.model.MenuItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.List;

/** Validates menu input and passes the approved changes to the repository. */
public class MenuService {
    private final MenuItemRepository repository;

    public MenuService() {
        this(new MenuItemRepository());
    }

    public MenuService(MenuItemRepository repository) {
        this.repository = repository;
    }

    public List<MenuItem> getAllItems() throws SQLException {
        return repository.findAll();
    }

    public void addItem(String name, String category, String price) throws SQLException {
        String cleanName = validateName(name);
        validateCategory(category);
        repository.add(cleanName, category, parsePrice(price));
    }

    public void updateItem(
        int id, String name, String category, String price, boolean available
    ) throws SQLException {
        String cleanName = validateName(name);
        validateCategory(category);
        long pricePaise = parsePrice(price);
        repository.update(new MenuItem(id, cleanName, pricePaise, category, available));
    }

    public void markUnavailable(int id) throws SQLException {
        repository.markUnavailable(id);
    }

    public void setAvailability(int id, boolean available) throws SQLException {
        if (id <= 0) {
            throw new IllegalArgumentException("Select a valid menu item.");
        }
        repository.setAvailability(id, available);
    }

    private String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Enter a name for the menu item.");
        }
        return name.trim();
    }

    private void validateCategory(String category) {
        if (!"Food".equals(category) && !"Beverage".equals(category)) {
            throw new IllegalArgumentException("Choose Food or Beverage as the category.");
        }
    }

    private long parsePrice(String price) {
        if (price == null || price.isBlank()) {
            throw new IllegalArgumentException("Enter a valid price with up to two decimal places.");
        }
        try {
            BigDecimal rupees = new BigDecimal(price.trim());
            if (rupees.signum() <= 0) {
                throw new IllegalArgumentException("Price must be greater than ₹0.00.");
            }
            return rupees.setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Enter a valid price with up to two decimal places.");
        }
    }
}
