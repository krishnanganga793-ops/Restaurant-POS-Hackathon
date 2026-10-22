package com.restaurantpos.database;

import com.restaurantpos.model.Customer;
import com.restaurantpos.model.LoyaltyRule;
import com.restaurantpos.model.MenuItem;
import java.sql.*;
import java.util.*;

/** Persistence for customers, rule configuration, conversion rate, and customer balances. */
public class LoyaltyRepository {
    public List<Customer> findCustomers() throws SQLException {
        List<Customer> result = new ArrayList<>();
        try (Connection c = Database.openConnection(); PreparedStatement s = c.prepareStatement(
            "SELECT customer_id,name,phone FROM customers ORDER BY name COLLATE NOCASE"); ResultSet r = s.executeQuery()) {
            while (r.next()) result.add(new Customer(r.getInt(1), r.getString(2), r.getString(3)));
        }
        return result;
    }

    public Customer findCustomerByPhone(String phone) throws SQLException {
        String sql = "SELECT customer_id, name, phone FROM customers WHERE phone = ? COLLATE NOCASE";
        try (Connection connection = Database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, phone.trim());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new Customer(result.getInt("customer_id"), result.getString("name"),
                    result.getString("phone"));
            }
        }
    }
    public Customer addCustomer(String name, String phone) throws SQLException {
        try (Connection c = Database.openConnection(); PreparedStatement s = c.prepareStatement(
            "INSERT INTO customers(name,phone) VALUES(?,?)")) {
            s.setString(1, name.trim()); s.setString(2, phone.trim()); s.executeUpdate();
            try (Statement id = c.createStatement(); ResultSet r = id.executeQuery("SELECT last_insert_rowid()")) {
                return new Customer(r.getInt(1), name.trim(), phone.trim());
            }
        }
    }
    public long getBalance(int customerId) throws SQLException {
        try (Connection c = Database.openConnection(); PreparedStatement s = c.prepareStatement(
            "SELECT COALESCE(SUM(CASE movement_type WHEN 'EARN' THEN points ELSE -points END),0) FROM loyalty_ledger WHERE customer_id=?")) {
            s.setInt(1, customerId); try (ResultSet r = s.executeQuery()) { return r.getLong(1); }
        }
    }
    public List<LoyaltyRule> findRules() throws SQLException {
        List<LoyaltyRule> rules = new ArrayList<>();
        String sql = "SELECT r.rule_id,r.menu_item_id,m.name,m.category,m.available,r.minimum_quantity,r.points_awarded,r.active FROM loyalty_rules r JOIN menu_items m USING(menu_item_id) ORDER BY m.name COLLATE NOCASE,r.menu_item_id";
        try (Connection c = Database.openConnection(); PreparedStatement s = c.prepareStatement(sql); ResultSet r = s.executeQuery()) {
            while (r.next()) rules.add(new LoyaltyRule(r.getInt(1), r.getInt(2), r.getString(3),
                r.getString(4), r.getInt(5) == 1, r.getInt(6), r.getInt(7), r.getInt(8) == 1));
        }
        return rules;
    }
    public void saveRule(int menuItemId, int minimumQuantity, int points, boolean active) throws SQLException {
        String sql = "INSERT INTO loyalty_rules(menu_item_id,minimum_quantity,points_awarded,active) VALUES(?,?,?,?) ON CONFLICT(menu_item_id) DO UPDATE SET minimum_quantity=excluded.minimum_quantity,points_awarded=excluded.points_awarded,active=excluded.active";
        try (Connection c = Database.openConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setInt(1,menuItemId);s.setInt(2,minimumQuantity);s.setInt(3,points);s.setInt(4,active?1:0);s.executeUpdate();
        }
    }
    public void deleteRule(int menuItemId) throws SQLException {
        try(Connection c=Database.openConnection();PreparedStatement s=c.prepareStatement("DELETE FROM loyalty_rules WHERE menu_item_id=?")){s.setInt(1,menuItemId);s.executeUpdate();}
    }
    public int getPointsPerRupee() throws SQLException {
        try(Connection c=Database.openConnection();PreparedStatement s=c.prepareStatement("SELECT setting_value FROM loyalty_settings WHERE setting_key='points_per_rupee'" );ResultSet r=s.executeQuery()){return r.next()?r.getInt(1):100;}
    }
    public void setPointsPerRupee(int value) throws SQLException {
        try(Connection c=Database.openConnection();PreparedStatement s=c.prepareStatement("UPDATE loyalty_settings SET setting_value=? WHERE setting_key='points_per_rupee'")){s.setInt(1,value);s.executeUpdate();}
    }
    public List<MenuItem> findFoodItems() throws SQLException {
        List<MenuItem> items=new ArrayList<>();
        try(Connection c=Database.openConnection();PreparedStatement s=c.prepareStatement("SELECT menu_item_id,name,price_paise,category,available FROM menu_items WHERE category='Food' AND available=1 ORDER BY name COLLATE NOCASE");ResultSet r=s.executeQuery()){
            while(r.next())items.add(new MenuItem(r.getInt(1),r.getString(2),r.getLong(3),r.getString(4),r.getInt(5)==1));
        } return items;
    }
}
