package com.restaurantpos.service;

import com.restaurantpos.database.LoyaltyRepository;
import com.restaurantpos.model.Customer;
import com.restaurantpos.model.LoyaltyRule;
import com.restaurantpos.model.OrderItem;
import java.sql.SQLException;
import java.util.*;

/** Validates loyalty inputs and applies the one-award-per-eligible-item/order policy. */
public class LoyaltyService {
    private final LoyaltyRepository repository;
    public LoyaltyService() { this(new LoyaltyRepository()); }
    public LoyaltyService(LoyaltyRepository repository) { this.repository=repository; }
    public List<Customer> getCustomers() throws SQLException { return repository.findCustomers(); }
    public Customer findCustomerByPhone(String phone) throws SQLException {
        if (phone == null || !phone.trim().matches("[+0-9][0-9 -]{6,19}")) {
            throw new IllegalArgumentException("Enter a valid phone number.");
        }
        return repository.findCustomerByPhone(phone);
    }
    public Customer registerCustomer(String name,String phone) throws SQLException {
        if(name==null||name.isBlank())throw new IllegalArgumentException("Enter the customer's name.");
        if(phone==null||!phone.trim().matches("[+0-9][0-9 -]{6,19}"))throw new IllegalArgumentException("Enter a valid phone number.");
        return repository.addCustomer(name,phone);
    }
    public long getBalance(int id) throws SQLException { return repository.getBalance(id); }
    public List<LoyaltyRule> getRules() throws SQLException { return repository.findRules(); }
    public List<com.restaurantpos.model.MenuItem> getFoodItems() throws SQLException { return repository.findFoodItems(); }
    public int getPointsPerRupee() throws SQLException { return repository.getPointsPerRupee(); }
    public void setPointsPerRupee(int value) throws SQLException { if(value<=0)throw new IllegalArgumentException("Points per rupee must be a positive whole number."); repository.setPointsPerRupee(value); }
    public void saveRule(int itemId,int minimum,int points,boolean active) throws SQLException {
        if(itemId<=0||minimum<=0||points<=0)throw new IllegalArgumentException("Choose a food item and enter positive quantity and points.");
        repository.saveRule(itemId,minimum,points,active);
    }
    public void deleteRule(int itemId) throws SQLException { repository.deleteRule(itemId); }
    public int calculateEarned(List<OrderItem> items) throws SQLException {
        return calculateEarned(items, repository.findRules());
    }
    public static int calculateEarned(List<OrderItem> items, List<LoyaltyRule> rules) {
        Map<Integer,Integer> quantities=new HashMap<>();
        for(OrderItem item:items) quantities.merge(item.getMenuItem().getId(),item.getQuantity(),Math::addExact);
        int total=0;
        for(LoyaltyRule rule:rules) if(rule.active()&&rule.itemAvailable()&&"Food".equalsIgnoreCase(rule.category())&&quantities.getOrDefault(rule.menuItemId(),0)>=rule.minimumQuantity()) total=Math.addExact(total,rule.pointsAwarded());
        return total;
    }
}
