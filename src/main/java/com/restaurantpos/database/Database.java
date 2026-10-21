package com.restaurantpos.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Opens the local SQLite database and creates its initial schema. */
public final class Database {
    private static final String DATABASE_NAME = "restaurant-pos.db";

    private Database() {
    }

    /** Opens a connection and enables foreign-key checks for that connection. */
    public static Connection openConnection() throws SQLException {
        Path databaseFile = getDatabaseFile();
        try {
            Files.createDirectories(databaseFile.getParent());
        } catch (IOException exception) {
            throw new SQLException("Could not create the application data folder.", exception);
        }

        String sqlitePath = databaseFile.toAbsolutePath().toString().replace('\\', '/');
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            return connection;
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
    }

    /** Creates the tables the first time the application runs. */
    public static void initialize() throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS users (
                    user_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL COLLATE NOCASE UNIQUE,
                    password_hash TEXT NOT NULL,
                    password_salt TEXT NOT NULL,
                    role TEXT NOT NULL CHECK (role IN ('ADMIN', 'CASHIER')),
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS menu_items (
                    menu_item_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    category TEXT NOT NULL CHECK (category IN ('Food', 'Beverage')),
                    price_paise INTEGER NOT NULL CHECK (price_paise >= 0),
                    available INTEGER NOT NULL DEFAULT 1 CHECK (available IN (0, 1)),
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS restaurant_tables (
                    table_id INTEGER PRIMARY KEY,
                    capacity INTEGER NOT NULL CHECK (capacity > 0),
                    status TEXT NOT NULL DEFAULT 'AVAILABLE'
                        CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'RESERVED'))
                )
                """);
            for (int tableId = 1; tableId <= 10; tableId++) {
                try (var seed = connection.prepareStatement(
                    "INSERT OR IGNORE INTO restaurant_tables (table_id, capacity) VALUES (?, 4)")) {
                    seed.setInt(1, tableId);
                    seed.executeUpdate();
                }
            }

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS orders (
                    order_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    status TEXT NOT NULL DEFAULT 'OPEN'
                        CHECK (status IN ('OPEN', 'PAID', 'CANCELLED')),
                    total_paise INTEGER NOT NULL DEFAULT 0 CHECK (total_paise >= 0),
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    table_id INTEGER,
                    FOREIGN KEY (table_id) REFERENCES restaurant_tables(table_id),
                    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE RESTRICT
                )
                """);
            boolean hasTableId = false;
            try (var columns = statement.executeQuery("PRAGMA table_info(orders)")) {
                while (columns.next()) {
                    if ("table_id".equalsIgnoreCase(columns.getString("name"))) {
                        hasTableId = true;
                        break;
                    }
                }
            }
            if (!hasTableId) {
                statement.executeUpdate("ALTER TABLE orders ADD COLUMN table_id INTEGER "
                    + "REFERENCES restaurant_tables(table_id)");
            }

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS order_items (
                    order_item_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_id INTEGER NOT NULL,
                    menu_item_id INTEGER NOT NULL,
                    item_name TEXT NOT NULL,
                    unit_price_paise INTEGER NOT NULL CHECK (unit_price_paise >= 0),
                    quantity INTEGER NOT NULL CHECK (quantity > 0),
                    subtotal_paise INTEGER NOT NULL
                        CHECK (subtotal_paise = unit_price_paise * quantity),
                    FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
                    FOREIGN KEY (menu_item_id) REFERENCES menu_items(menu_item_id)
                        ON DELETE RESTRICT
                )
                """);

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS payments (
                    payment_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_id INTEGER NOT NULL,
                    amount_paise INTEGER NOT NULL CHECK (amount_paise > 0),
                    payment_method TEXT NOT NULL
                        CHECK (payment_method IN ('CASH', 'CARD', 'UPI', 'OTHER')),
                    recorded_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE RESTRICT
                )
                """);
        }
    }

    /** Returns the location used for the local database file. */
    public static Path getDatabaseFile() {
        String operatingSystem = System.getProperty("os.name", "").toLowerCase();
        Path dataDirectory;

        if (operatingSystem.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            dataDirectory = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), "AppData", "Local")
                : Path.of(localAppData);
        } else if (operatingSystem.contains("mac")) {
            dataDirectory = Path.of(System.getProperty("user.home"), "Library", "Application Support");
        } else {
            String xdgDataHome = System.getenv("XDG_DATA_HOME");
            dataDirectory = xdgDataHome == null || xdgDataHome.isBlank()
                ? Path.of(System.getProperty("user.home"), ".local", "share")
                : Path.of(xdgDataHome);
        }

        return dataDirectory.resolve("RestaurantPOS").resolve(DATABASE_NAME);
    }
}
