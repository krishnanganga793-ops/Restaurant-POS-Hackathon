package com.restaurantpos.service;

import com.restaurantpos.database.Database;
import com.restaurantpos.model.User;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class LoginService {

    /** Registers a new staff account with the cashier role and a salted password hash. */
    public User registerCashier(String username, String password) throws SQLException {
        String cleanUsername = username == null ? "" : username.trim();
        if (cleanUsername.isBlank() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Enter a username and password.");
        }

        String checkSql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection connection = Database.openConnection()) {
            try (PreparedStatement check = connection.prepareStatement(checkSql)) {
                check.setString(1, cleanUsername);
                try (ResultSet results = check.executeQuery()) {
                    if (results.next()) {
                        throw new IllegalArgumentException("That username is already in use.");
                    }
                }
            }

            PasswordHasher.PasswordHash passwordHash;
            try {
                passwordHash = PasswordHasher.create(password);
            } catch (GeneralSecurityException exception) {
                throw new SQLException("Could not secure the new account password.", exception);
            }

            String insertSql = """
                INSERT INTO users (username, password_hash, password_salt, role)
                VALUES (?, ?, ?, 'CASHIER')
                """;
            try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                insert.setString(1, cleanUsername);
                insert.setString(2, passwordHash.hash());
                insert.setString(3, passwordHash.salt());
                insert.executeUpdate();
            } catch (SQLException exception) {
                if (exception.getMessage() != null
                    && exception.getMessage().toLowerCase().contains("unique constraint")) {
                    throw new IllegalArgumentException("That username is already in use.");
                }
                throw exception;
            }

            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT last_insert_rowid()")) {
                if (result.next()) {
                    return new User(result.getInt(1), cleanUsername, "CASHIER");
                }
            }
        }
        throw new SQLException("The account was created without a user ID.");
    }

    /** Looks up an account and verifies its password against the stored salted hash. */
    public User login(String username, String password) throws SQLException {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            return null;
        }

        String sql = """
            SELECT user_id, username, role, password_hash, password_salt
            FROM users
            WHERE username = ?
            """;
        try (Connection connection = Database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);

            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return null;
                }

                boolean passwordMatches;
                try {
                    passwordMatches = PasswordHasher.verify(
                        password,
                        results.getString("password_salt"),
                        results.getString("password_hash")
                    );
                } catch (GeneralSecurityException exception) {
                    throw new SQLException("Could not verify the stored password.", exception);
                }

                if (!passwordMatches) {
                    return null;
                }

                return new User(
                    results.getInt("user_id"),
                    results.getString("username"),
                    results.getString("role")
                );
            }
        }
    }

    /** Adds the original demo accounts only when their usernames are not already present. */
    public void seedDemoAccounts() throws SQLException {
        try (Connection connection = Database.openConnection()) {
            seedDemoAccount(connection, "admin", "admin123", "ADMIN");
            seedDemoAccount(connection, "cashier", "cashier123", "CASHIER");
        }
    }

    private void seedDemoAccount(
        Connection connection, String username, String password, String role
    ) throws SQLException {
        String checkSql = "SELECT 1 FROM users WHERE username = ?";
        try (PreparedStatement check = connection.prepareStatement(checkSql)) {
            check.setString(1, username);
            try (ResultSet results = check.executeQuery()) {
                if (results.next()) {
                    return;
                }
            }
        }

        PasswordHasher.PasswordHash passwordHash;
        try {
            passwordHash = PasswordHasher.create(password);
        } catch (GeneralSecurityException exception) {
            throw new SQLException("Could not prepare the demo account password.", exception);
        }

        // INSERT OR IGNORE makes a concurrent startup safe and never overwrites an account.
        String insertSql = """
            INSERT OR IGNORE INTO users (username, password_hash, password_salt, role)
            VALUES (?, ?, ?, ?)
            """;
        try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
            insert.setString(1, username);
            insert.setString(2, passwordHash.hash());
            insert.setString(3, passwordHash.salt());
            insert.setString(4, role);
            insert.executeUpdate();
        }
    }
}
