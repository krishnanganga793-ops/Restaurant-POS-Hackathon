package com.restaurantpos.service;

import static org.junit.jupiter.api.Assertions.*;

import com.restaurantpos.database.*;
import com.restaurantpos.model.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class LoyaltyServiceTest {
    @TempDir Path temp;
    private LoyaltyRepository loyalty;
    private int foodId;
    private int customerId;

    @BeforeEach void setUp() throws Exception {
        System.setProperty("restaurantpos.database.file",temp.resolve("loyalty-test.db").toString());
        Database.initialize(); loyalty=new LoyaltyRepository();
        try(Connection c=Database.openConnection();Statement s=c.createStatement()) {
            s.executeUpdate("INSERT INTO users(username,password_hash,password_salt,role) VALUES('test','x','x','CASHIER')");
            s.executeUpdate("INSERT INTO menu_items(name,category,price_paise) VALUES('Test Food','Food',15000)");
            try(ResultSet id=s.executeQuery("SELECT last_insert_rowid()")){id.next();foodId=id.getInt(1);}
        }
        customerId=loyalty.addCustomer("Test Customer","9990000000").customerId();
    }
    @AfterEach void tearDown(){System.clearProperty("restaurantpos.database.file");}

    @Test void oneAwardPerQualifyingItemAtAndAboveThreshold() {
        MenuItem food=new MenuItem(foodId,"Test Food",15000,"Food",true);
        LoyaltyRule rule=new LoyaltyRule(1,foodId,"Test Food","Food",true,2,10,true);
        assertEquals(0,LoyaltyService.calculateEarned(List.of(new OrderItem(food,1)),List.of(rule)));
        assertEquals(10,LoyaltyService.calculateEarned(List.of(new OrderItem(food,2)),List.of(rule)));
        assertEquals(10,LoyaltyService.calculateEarned(List.of(new OrderItem(food,4)),List.of(rule)));
        assertEquals(0,LoyaltyService.calculateEarned(List.of(new OrderItem(food,4)),List.of(
            new LoyaltyRule(2,foodId,"Test Food","Food",true,2,10,false))));
        assertEquals(0,LoyaltyService.calculateEarned(List.of(new OrderItem(food,4)),List.of(
            new LoyaltyRule(3,foodId,"Test Food","Beverage",true,1,10,true))));
    }

    @Test void paymentCreditsOnceAndRedemptionIsAtomicAndCappedByBalance() throws Exception {
        loyalty.saveRule(foodId,2,100,true);
        int first=saveOrder(2);
        PaymentRepository payments=new PaymentRepository();
        assertThrows(SQLException.class,()->payments.recordFullPayment(first,1,"CASH",0));
        assertEquals(0,loyalty.getBalance(customerId));
        payments.recordFullPayment(first,30000,"CASH",0);
        assertEquals(100,loyalty.getBalance(customerId));
        assertThrows(SQLException.class,()->payments.recordFullPayment(first,30000,"CASH",0));

        int second=saveOrder(2);
        assertThrows(SQLException.class,()->payments.recordFullPayment(second,29900,"CASH",101));
        assertEquals(100,loyalty.getBalance(customerId));
        payments.recordFullPayment(second,29900,"CASH",100);
        assertEquals(100,loyalty.getBalance(customerId));
        try(Connection c=Database.openConnection();PreparedStatement s=c.prepareStatement(
            "SELECT points_redeemed,discount_paise FROM loyalty_redemptions WHERE order_id=?")) {
            s.setInt(1,second);try(ResultSet r=s.executeQuery()){assertTrue(r.next());assertEquals(100,r.getInt(1));assertEquals(100,r.getLong(2));}
        }
        assertThrows(SQLException.class,()->payments.recordFullPayment(second,29900,"CASH",100));
        assertEquals(100,loyalty.getBalance(customerId));
    }

    @Test void orderCustomerBalanceUpdatesOnlyAfterSuccessfulPayment() throws Exception {
        loyalty.saveRule(foodId, 2, 10, true);
        MenuItem food = new MenuItem(foodId, "Test Food", 15000, "Food", true);
        var savedOrder = new OrderRepository().saveOrder(
            1, List.of(new OrderItem(food, 2)), null, customerId);
        PaymentRepository payments = new PaymentRepository();

        try (Connection connection = Database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT customer_id FROM orders WHERE order_id=?")) {
            statement.setInt(1, savedOrder.getOrderId());
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(customerId, result.getInt("customer_id"));
            }
        }

        LoyaltyCheckout beforePayment = payments.getLoyaltyCheckout(savedOrder.getOrderId(), 0);
        assertEquals(customerId, beforePayment.customerId());
        assertEquals(0, beforePayment.availablePoints());
        assertEquals(0, beforePayment.discountPaise());
        assertThrows(SQLException.class,
            () -> payments.getLoyaltyCheckout(savedOrder.getOrderId(), 1));

        payments.recordFullPayment(savedOrder.getOrderId(), 30000, "CASH", 0);

        LoyaltyCheckout afterPayment = payments.getLoyaltyCheckout(savedOrder.getOrderId(), 0);
        assertEquals(customerId, afterPayment.customerId());
        assertEquals(10, afterPayment.availablePoints());
        assertEquals(10, loyalty.getBalance(customerId));
    }

    @Test void redemptionValidatesBalanceAndUsesConfiguredPointsPerRupee() throws Exception {
        loyalty.setPointsPerRupee(10);
        loyalty.saveRule(foodId, 2, 10, true);
        PaymentRepository payments = new PaymentRepository();
        PaymentService service = new PaymentService(payments);
        int earningOrder = saveOrder(2);
        payments.recordFullPayment(earningOrder, 30000, "CASH", 0);
        assertEquals(10, loyalty.getBalance(customerId));

        int redemptionOrder = saveOrder(2);
        assertThrows(SQLException.class,
            () -> payments.getLoyaltyCheckout(redemptionOrder, 11));
        assertThrows(SQLException.class,
            () -> service.recordPayment(redemptionOrder, "299.00", "CASH", 11));
        assertThrows(SQLException.class,
            () -> payments.recordFullPayment(redemptionOrder, 29900, "CASH", 11));
        assertThrows(IllegalArgumentException.class,
            () -> service.recordPayment(redemptionOrder, "300.00", "CASH", -1));
        assertEquals(10, loyalty.getBalance(customerId));

        LoyaltyCheckout quote = payments.getLoyaltyCheckout(redemptionOrder, 10);
        assertEquals(100, quote.discountPaise());
        assertThrows(SQLException.class,
            () -> service.recordPayment(redemptionOrder, "299.99", "CASH", 10));
        assertEquals(10, loyalty.getBalance(customerId));

        service.recordPayment(redemptionOrder, "299.00", "CASH", 10);
        assertEquals(10, loyalty.getBalance(customerId));
        try (Connection connection = Database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT movement_type,points FROM loyalty_ledger WHERE order_id=? ORDER BY movement_type")) {
            statement.setInt(1, redemptionOrder);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals("EARN", result.getString("movement_type"));
                assertEquals(10, result.getInt("points"));
                assertTrue(result.next());
                assertEquals("REDEEM", result.getString("movement_type"));
                assertEquals(10, result.getInt("points"));
                assertFalse(result.next());
            }
        }
    }

    @Test void guestOrderDoesNotEarnOrRedeem() throws Exception {
        loyalty.saveRule(foodId,1,50,true);
        int order;
        try(Connection c=Database.openConnection();Statement s=c.createStatement()) {
            s.executeUpdate("INSERT INTO orders(user_id,total_paise) VALUES(1,15000)");
            try(ResultSet id=s.executeQuery("SELECT last_insert_rowid()")){id.next();order=id.getInt(1);}
            s.executeUpdate("INSERT INTO order_items(order_id,menu_item_id,item_name,unit_price_paise,quantity,subtotal_paise) VALUES("+order+","+foodId+",'Test Food',15000,1,15000)");
        }
        new PaymentRepository().recordFullPayment(order,15000,"CASH",0);
        assertEquals(0,loyalty.getBalance(customerId));
    }

    @Test void customerCanBeFoundByTrimmedPhoneAndUsesInternalId() throws Exception {
        LoyaltyService service = new LoyaltyService(loyalty);
        Customer found = service.findCustomerByPhone(" 9990000000 ");
        assertNotNull(found);
        assertEquals(customerId, found.customerId());
        assertEquals("Test Customer", found.name());
        assertNull(service.findCustomerByPhone("9991111111"));
    }

    @Test void initializationPreservesCustomersAndRulesAndKeepsConversionSetting() throws Exception {
        loyalty.saveRule(foodId,3,25,true);
        loyalty.setPointsPerRupee(250);
        Database.initialize();
        assertEquals(1,loyalty.findCustomers().size());
        assertEquals(250,loyalty.getPointsPerRupee());
        assertEquals(3,loyalty.findRules().get(0).minimumQuantity());
    }

    @Test void selectorUsesAvailableFoodIdsAndUnavailableRulesDoNotEarn() throws Exception {
        int sameNameFoodId = addMenuItem("Test Food", "Food", true);
        int unavailableFoodId = addMenuItem("Hidden Food", "Food", false);
        addMenuItem("Test Food", "Beverage", true);
        var availableFood = loyalty.findFoodItems();
        assertTrue(availableFood.stream().anyMatch(item -> item.getId() == foodId));
        assertTrue(availableFood.stream().anyMatch(item -> item.getId() == sameNameFoodId));
        assertFalse(availableFood.stream().anyMatch(item -> item.getId() == unavailableFoodId));
        assertEquals(2, availableFood.stream().filter(item -> item.getName().equals("Test Food")).count());

        loyalty.saveRule(foodId, 1, 50, true);
        new MenuItemRepository().setAvailability(foodId, false);
        int unavailableOrder = saveOrder(1);
        PaymentRepository payments = new PaymentRepository();
        payments.recordFullPayment(unavailableOrder, 15000, "CASH", 0);
        assertEquals(0, loyalty.getBalance(customerId));
        assertTrue(loyalty.findRules().stream().anyMatch(rule -> rule.menuItemId() == foodId));

        new MenuItemRepository().setAvailability(foodId, true);
        int availableOrder = saveOrder(1);
        payments.recordFullPayment(availableOrder, 15000, "CASH", 0);
        assertEquals(50, loyalty.getBalance(customerId));
    }

    private int saveOrder(int quantity) throws Exception {
        try(Connection c=Database.openConnection();Statement s=c.createStatement()) {
            s.executeUpdate("INSERT INTO orders(user_id,total_paise,customer_id) VALUES(1,"+(15000L*quantity)+","+customerId+")");
            int order;try(ResultSet id=s.executeQuery("SELECT last_insert_rowid()")){id.next();order=id.getInt(1);}
            s.executeUpdate("INSERT INTO order_items(order_id,menu_item_id,item_name,unit_price_paise,quantity,subtotal_paise) VALUES("+order+","+foodId+",'Test Food',15000,"+quantity+","+(15000L*quantity)+")");
            return order;
        }
    }

    private int addMenuItem(String name, String category, boolean available) throws SQLException {
        try (Connection connection = Database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO menu_items(name,category,price_paise,available) VALUES(?,?,15000,?)")) {
            statement.setString(1, name);
            statement.setString(2, category);
            statement.setInt(3, available ? 1 : 0);
            statement.executeUpdate();
            try (Statement idQuery = connection.createStatement();
                 ResultSet id = idQuery.executeQuery("SELECT last_insert_rowid()")) {
                id.next();
                return id.getInt(1);
            }
        }
    }
}
