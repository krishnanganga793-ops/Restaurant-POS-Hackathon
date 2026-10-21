package com.restaurantpos.ui;

import com.restaurantpos.model.RestaurantTable;
import com.restaurantpos.model.User;
import com.restaurantpos.service.RestaurantTableService;
import java.math.BigDecimal;
import java.sql.SQLException;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Staff screen for table availability, reservations, and active table orders. */
public class TableManagementView {
    private final Stage stage;
    private final User user;
    private final RestaurantTableService tableService = new RestaurantTableService();
    private final GridPane tableGrid = new GridPane();
    private final Label messageLabel = new Label(" ");

    public TableManagementView(Stage stage, User user) {
        this.stage = stage;
        this.user = user;
    }

    public Scene createScene() {
        Label heading = new Label("Table Management");
        heading.getStyleClass().add("tables-heading");
        Label signedIn = new Label("Restaurant seating · Signed in as "
            + user.getUsername() + " · " + user.getRole());
        signedIn.getStyleClass().add("tables-subtitle");
        Button refresh = new Button("Refresh tables");
        refresh.getStyleClass().add("tables-button");
        refresh.setOnAction(event -> loadTables());
        HBox navigation = NavigationBar.create(stage, user, "tables-button");

        tableGrid.setHgap(16);
        tableGrid.setVgap(16);
        tableGrid.setPadding(new Insets(4));
        for (int i = 0; i < 5; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(20);
            column.setHgrow(Priority.ALWAYS);
            tableGrid.getColumnConstraints().add(column);
        }
        messageLabel.getStyleClass().add("tables-message");
        messageLabel.setWrapText(true);
        VBox root = new VBox(14, heading, signedIn, navigation, refresh, tableGrid, messageLabel);
        root.setPadding(new Insets(28));
        root.getStyleClass().add("tables-root");
        VBox.setVgrow(tableGrid, Priority.ALWAYS);
        Scene scene = new Scene(root, 1180, 760);
        scene.getStylesheets().add(
            TableManagementView.class.getResource("/tables.css").toExternalForm());
        stage.setTitle("Restaurant POS - Table Management");
        stage.setMinWidth(1000);
        stage.setMinHeight(680);
        loadTables();
        return scene;
    }

    private void loadTables() {
        try {
            tableGrid.getChildren().clear();
            int index = 0;
            for (RestaurantTable table : tableService.getTables()) {
                tableGrid.add(createCard(table), index % 5, index / 5);
                index++;
            }
            showMessage("Table statuses loaded from the local database.", false);
        } catch (SQLException exception) {
            tableGrid.getChildren().clear();
            showMessage("Could not load tables: " + exception.getMessage(), true);
        }
    }

    private VBox createCard(RestaurantTable table) {
        String status = table.status();
        String statusStyle = status.toLowerCase();
        VBox card = new VBox(10);
        card.setPadding(new Insets(18));
        card.setMinHeight(205);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().addAll("tables-card", "table-" + statusStyle);

        Label name = new Label("Table " + table.tableId());
        name.getStyleClass().add("table-name");
        Label capacity = new Label("Seats " + table.capacity());
        capacity.getStyleClass().add("table-detail");
        Label state = new Label(toDisplayStatus(status));
        state.getStyleClass().addAll("table-status", "table-status-" + statusStyle);
        card.getChildren().addAll(name, capacity, state);

        if ("OCCUPIED".equals(status)) {
            Label total = new Label("Current total: " + formatMoney(table.currentOrderTotalPaise()));
            total.getStyleClass().add("table-total");
            card.getChildren().add(total);
            Button view = new Button("View order & bill");
            view.getStyleClass().add("tables-button");
            view.setMaxWidth(Double.MAX_VALUE);
            if (table.currentOrderId() != null) {
                view.setOnAction(event -> stage.setScene(
                    new OrderHistoryView(stage, user, table.currentOrderId()).createScene()));
            } else {
                view.setDisable(true);
            }
            card.getChildren().add(view);
        } else if ("RESERVED".equals(status)) {
            Button cancel = new Button("Cancel reservation");
            cancel.getStyleClass().add("tables-button");
            cancel.setMaxWidth(Double.MAX_VALUE);
            cancel.setOnAction(event -> changeReservation(table, false));
            card.getChildren().add(cancel);
        } else if ("AVAILABLE".equals(status)) {
            Button start = new Button("Start order");
            start.getStyleClass().add("tables-primary-button");
            start.setMaxWidth(Double.MAX_VALUE);
            start.setOnAction(event -> stage.setScene(
                new OrderEntryView(stage, user, table.tableId()).createScene()));
            Button reserve = new Button("Reserve table");
            reserve.getStyleClass().add("tables-button");
            reserve.setMaxWidth(Double.MAX_VALUE);
            reserve.setOnAction(event -> changeReservation(table, true));
            card.getChildren().addAll(start, reserve);
        }
        return card;
    }

    private void changeReservation(RestaurantTable table, boolean reserve) {
        try {
            if (reserve) {
                tableService.reserve(table.tableId());
                loadTables();
                showMessage("Table " + table.tableId() + " reserved.", false);
            } else {
                tableService.cancelReservation(table.tableId());
                loadTables();
                showMessage("Reservation for Table " + table.tableId() + " cancelled.", false);
            }
        } catch (SQLException exception) {
            loadTables();
            showMessage(exception.getMessage(), true);
        }
    }

    private String toDisplayStatus(String status) {
        return switch (status) {
            case "AVAILABLE" -> "Available";
            case "OCCUPIED" -> "Occupied";
            case "RESERVED" -> "Reserved";
            default -> status;
        };
    }

    private String formatMoney(long paise) {
        return "₹" + BigDecimal.valueOf(paise, 2).toPlainString();
    }

    private void showMessage(String text, boolean error) {
        messageLabel.setText(text == null || text.isBlank() ? "Table operation failed." : text);
        messageLabel.getStyleClass().removeAll("tables-error", "tables-success");
        messageLabel.getStyleClass().add(error ? "tables-error" : "tables-success");
    }
}
