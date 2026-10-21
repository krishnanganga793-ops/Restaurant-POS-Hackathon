# SQLite database schema

This document describes the schema and initialization behavior implemented by `com.restaurantpos.database.Database`. It is based on the initialization and migration code, not on a query of the user's live database contents. The application creates missing tables on startup and preserves existing database rows.

## Location and connection behavior

The database file is named `restaurant-pos.db` and is placed in the operating system's application-data directory:

| Platform | Location |
| --- | --- |
| Windows | `%LOCALAPPDATA%\RestaurantPOS\restaurant-pos.db`; fallback `~/AppData/Local/RestaurantPOS/restaurant-pos.db` |
| macOS | `~/Library/Application Support/RestaurantPOS/restaurant-pos.db` |
| Linux/other | `$XDG_DATA_HOME/RestaurantPOS/restaurant-pos.db`; fallback `~/.local/share/RestaurantPOS/restaurant-pos.db` |

The connection setup enables SQLite foreign-key enforcement with `PRAGMA foreign_keys=ON`. Initialization uses `CREATE TABLE IF NOT EXISTS`, so starting the app does not recreate or clear an existing database.

## Tables

### `users`

| Column | Definition / purpose |
| --- | --- |
| `user_id` | `INTEGER PRIMARY KEY AUTOINCREMENT` |
| `username` | Required, unique, case-insensitive (`COLLATE NOCASE`) username |
| `password_hash` | Required salted password hash |
| `salt` | Required per-account salt |
| `role` | Required; constrained to `ADMIN` or `CASHIER` |
| `created_at` | Creation timestamp, default `CURRENT_TIMESTAMP` |

### `menu_items`

| Column | Definition / purpose |
| --- | --- |
| `menu_item_id` | `INTEGER PRIMARY KEY AUTOINCREMENT` |
| `name` | Required item name |
| `category` | Required; constrained to `Food` or `Beverage` |
| `price_paise` | Required integer price, constrained to zero or greater |
| `available` | Required integer availability flag, constrained to `0` or `1` |
| `created_at` | Creation timestamp, default `CURRENT_TIMESTAMP` |

### `restaurant_tables`

| Column | Definition / purpose |
| --- | --- |
| `table_id` | Integer table number and primary key |
| `capacity` | Required seating capacity, constrained to greater than zero |
| `status` | Required; constrained to `AVAILABLE`, `OCCUPIED`, or `RESERVED` |

Initialization inserts Table 1 through Table 10 with capacity 4 and status `AVAILABLE`, using insert-if-absent behavior so existing table records are retained.

### `orders`

| Column | Definition / purpose |
| --- | --- |
| `order_id` | `INTEGER PRIMARY KEY AUTOINCREMENT`; displayed as the order number |
| `user_id` | Required staff user ID; foreign key to `users.user_id` with delete restricted |
| `status` | Required; constrained to `OPEN`, `PAID`, or `CANCELLED` |
| `total_paise` | Required integer total, constrained to zero or greater |
| `created_at` | Creation timestamp, default `CURRENT_TIMESTAMP` |
| `table_id` | Nullable foreign key to `restaurant_tables.table_id`; null represents a takeaway order |

### `order_items`

| Column | Definition / purpose |
| --- | --- |
| `order_item_id` | `INTEGER PRIMARY KEY AUTOINCREMENT` |
| `order_id` | Required foreign key to `orders.order_id`, with delete cascading |
| `menu_item_id` | Required foreign key to `menu_items.menu_item_id`, with delete restricted |
| `item_name` | Required item-name snapshot captured when the order is saved |
| `unit_price_paise` | Required non-negative integer unit-price snapshot |
| `quantity` | Required integer quantity greater than zero |
| `subtotal_paise` | Required integer line subtotal, constrained to `unit_price_paise * quantity` |

The saved name and price snapshots keep historical order details stable when a menu item is later renamed or repriced.

### `payments`

| Column | Definition / purpose |
| --- | --- |
| `payment_id` | `INTEGER PRIMARY KEY AUTOINCREMENT` |
| `order_id` | Required foreign key to `orders.order_id`, with delete restricted |
| `amount_paise` | Required integer payment amount, constrained to greater than zero |
| `method` | Required; constrained to `CASH`, `CARD`, `UPI`, or `OTHER` |
| `recorded_at` | Payment timestamp, default `CURRENT_TIMESTAMP` |

The payment workflow records the payment and updates the order status in a transaction. The inspected schema does not declare `order_id` unique in `payments`; duplicate-payment prevention is enforced by application logic rather than a unique database constraint.

## Relationships

```text
users 1 -> many orders
restaurant_tables 1 -> many orders (orders.table_id is optional)
orders 1 -> many order_items
menu_items 1 -> many order_items
orders 1 -> payments
```

Foreign-key enforcement is enabled per connection. Existing rows are protected by the configured delete actions: staff and menu items referenced by orders cannot be deleted, order lines cascade if their order is deleted, and orders referenced by payments cannot be deleted.

## Table-assignment migration

Earlier databases may have an `orders` table without a `table_id` column. During initialization, the database code checks the existing columns using `PRAGMA table_info(orders)`. If `table_id` is missing, it adds a nullable integer column referencing `restaurant_tables(table_id)`. Existing orders are preserved and remain unassigned (`NULL`); no old order is deleted or automatically assigned to a table. New takeaway orders can also leave `table_id` null.

## Money and timestamps

All persisted monetary amounts use integer paise rather than floating-point values: menu prices, order totals, item unit-price snapshots and subtotals, and payment amounts. The interface converts between rupees for display/input and paise for storage. The timestamp columns use SQLite `CURRENT_TIMESTAMP` defaults.
