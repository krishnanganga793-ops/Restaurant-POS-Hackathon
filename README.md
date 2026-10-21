# Restaurant POS

A JavaFX desktop point-of-sale application for a small restaurant. It uses SQLite for persistent staff, menu, table, order, and payment data.

## Features

- Login and cashier registration. New public registrations receive the `CASHIER` role.
- Menu management: add and edit food and beverage items, prices, and availability.
- Order creation with a cart, quantity controls, paise-based totals, and saved order history.
- Table management with ten initial tables, reservations, and links between dine-in orders and tables.
- Payment recording for saved orders, plus receipt display, text-file saving, and printing.
- Sales dashboard with figures read from the SQLite database.

## Technologies

- Java 27
- JavaFX 27
- SQLite with SQLite JDBC 3.53.4.0
- Maven

## Project layout

```text
src/main/java/com/restaurantpos/
  app/          JavaFX application entry point
  database/     SQLite connection, initialization, and repositories
  model/        Application data models
  service/      Authentication and business rules
  ui/           JavaFX screens and navigation
src/main/resources/
  CSS stylesheets and application images
pom.xml         Maven dependencies, Java version, and JavaFX launch configuration
```

The console demonstration in `Main.java` is separate from the JavaFX application. Maven's JavaFX launch plugin starts `com.restaurantpos.app.RestaurantPosApp`.

## Prerequisites

- A JDK that supports Java 27, configured as the JDK used by Maven.
- Apache Maven.
- Internet access the first time Maven needs to download dependencies.

Check the Java and Maven versions with `java -version` and `mvn -version` before building.

## Build and run from a clone

From the project root, run:

```shell
mvn clean compile
mvn javafx:run
```

The JavaFX Maven plugin and application entry point are configured in `pom.xml`. These are the project's documented Maven commands; they have not been run as part of preparing this documentation.

`.maven-validation-settings.xml` contains a machine-specific local Maven repository path and is excluded from Git. Do not rely on that file in a clean clone. Use your normal Maven settings and local repository, or create your own machine-specific settings file outside the public repository if needed.

## Database

On startup, the application creates the database directory and initializes missing tables. It does not reset an existing database. The local database file is named `restaurant-pos.db` and is stored in the operating system's application-data location:

- Windows: `%LOCALAPPDATA%\RestaurantPOS\restaurant-pos.db` (or `~/AppData/Local/RestaurantPOS/restaurant-pos.db` if `LOCALAPPDATA` is unavailable)
- macOS: `~/Library/Application Support/RestaurantPOS/restaurant-pos.db`
- Linux and other systems: `$XDG_DATA_HOME/RestaurantPOS/restaurant-pos.db`, or `~/.local/share/RestaurantPOS/restaurant-pos.db` when `XDG_DATA_HOME` is unset

The database schema, relationships, and table-assignment migration are documented in [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md). Money is stored as integer paise.

## Demo accounts

These credentials are for local demonstration only. Change or remove demo accounts before using the application with real staff data.

| Role | Username | Password |
| --- | --- | --- |
| Admin | `admin` | `admin123` |
| Cashier | `cashier` | `cashier123` |

The application seeds a demo account only if that username is not already present; it does not reset the password on each launch. Passwords are stored as salted hashes.

## Known limitations

- Admin and Cashier accounts currently have the same application permissions; role-based access restrictions are not meaningfully differentiated.
- Public registration creates cashier accounts, but the registration flow only checks that fields are present and passwords match; it does not enforce a stronger password policy.
- Table setup currently creates ten tables with capacity four. There is no screen to configure table count or capacity.
- Payments record the full outstanding order amount; partial payments and online payment gateways are not implemented.
- Saved orders can be reviewed through order history, but the order-entry screen does not edit an already saved order.
- Demo credentials are intentionally present for evaluation and must not be treated as production credentials.

## Verification status

This documentation describes the project configuration and behavior found by source inspection. No build, automated test suite, or runtime workflow was run for this documentation-only change.
