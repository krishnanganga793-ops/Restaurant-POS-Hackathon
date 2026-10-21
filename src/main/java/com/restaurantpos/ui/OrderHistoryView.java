package com.restaurantpos.ui;

import com.restaurantpos.model.OrderSummary;
import com.restaurantpos.model.User;
import com.restaurantpos.service.OrderHistoryService;
import com.restaurantpos.service.ReceiptService;
import java.math.BigDecimal;
import java.sql.SQLException;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Persistent listing of saved orders with their receipt details. */
public class OrderHistoryView {
    private final Stage stage;
    private final User user;
    private final Integer preselectedOrderId;
    private final OrderHistoryService historyService = new OrderHistoryService();
    private final ReceiptService receiptService = new ReceiptService();
    private final TableView<OrderSummary> orderTable = new TableView<>();
    private final TextArea receiptArea = new TextArea();
    private final Label messageLabel = new Label(" ");
    private OrderSummary selectedOrder;

    public OrderHistoryView(Stage stage, User user) {
        this(stage, user, null);
    }

    public OrderHistoryView(Stage stage, User user, Integer preselectedOrderId) {
        this.stage = stage;
        this.user = user;
        this.preselectedOrderId = preselectedOrderId;
    }

    public Scene createScene() {
        Label heading = new Label("Order History");
        heading.getStyleClass().add("history-heading");
        Label signedIn = new Label("Signed in as " + user.getUsername() + " · " + user.getRole());
        signedIn.getStyleClass().add("history-subtitle");
        HBox navigation = NavigationBar.create(stage, user, "history-nav-button");
        Button refresh = new Button("Refresh history");
        refresh.getStyleClass().add("history-nav-button");
        refresh.setOnAction(event -> loadOrders());

        configureTable();
        receiptArea.setEditable(false);
        receiptArea.setWrapText(false);
        receiptArea.setPromptText("Select a saved order to view its details and receipt.");
        Button viewReceipt = new Button("View receipt");
        viewReceipt.getStyleClass().add("history-primary-button");
        viewReceipt.setOnAction(event -> showSelectedReceipt());
        Button saveReceipt = new Button("Save receipt as text");
        saveReceipt.getStyleClass().add("history-nav-button");
        saveReceipt.setOnAction(event -> ReceiptViewSupport.save(stage, receiptArea));
        Button printReceipt = new Button("Print receipt");
        printReceipt.getStyleClass().add("history-nav-button");
        printReceipt.setOnAction(event -> ReceiptViewSupport.print(stage, receiptArea));
        HBox receiptActions = new HBox(8, viewReceipt, saveReceipt, printReceipt);

        VBox left = new VBox(10, new Label("Saved orders"), orderTable, refresh);
        left.getStyleClass().add("history-panel");
        left.setPadding(new Insets(18));
        left.setPrefWidth(620);
        VBox.setVgrow(orderTable, Priority.ALWAYS);
        VBox right = new VBox(10, new Label("Order details and receipt"), receiptArea, receiptActions);
        right.getStyleClass().add("history-panel");
        right.setPadding(new Insets(18));
        VBox.setVgrow(receiptArea, Priority.ALWAYS);
        HBox content = new HBox(18, left, right);
        HBox.setHgrow(right, Priority.ALWAYS);
        VBox.setVgrow(content, Priority.ALWAYS);

        messageLabel.getStyleClass().add("history-message");
        VBox root = new VBox(12, heading, signedIn, navigation, content, messageLabel);
        root.setPadding(new Insets(26));
        root.getStyleClass().add("history-root");
        Scene scene = new Scene(root, 1200, 760);
        scene.getStylesheets().add(
            OrderHistoryView.class.getResource("/history.css").toExternalForm());
        stage.setTitle("Restaurant POS - Order History");
        stage.setMinWidth(1000);
        stage.setMinHeight(680);
        loadOrders();
        return scene;
    }

    private void configureTable() {
        TableColumn<OrderSummary, String> order = new TableColumn<>("Order #");
        order.setCellValueFactory(cell -> new ReadOnlyStringWrapper("#" + cell.getValue().orderId()));
        TableColumn<OrderSummary, String> date = new TableColumn<>("Date/time");
        date.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().createdAt()));
        TableColumn<OrderSummary, String> total = new TableColumn<>("Total");
        total.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatMoney(cell.getValue().totalPaise())));
        TableColumn<OrderSummary, String> status = new TableColumn<>("Order status");
        status.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().status()));
        TableColumn<OrderSummary, String> payment = new TableColumn<>("Payment");
        payment.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().paymentStatus()));
        orderTable.getColumns().setAll(order, date, total, status, payment);
        orderTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        orderTable.getStyleClass().add("history-table");
        orderTable.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldOrder, newOrder) -> {
                selectedOrder = newOrder;
                if (newOrder == null) {
                    receiptArea.clear();
                } else {
                    showSelectedReceipt();
                }
            });
    }

    private void loadOrders() {
        try {
            orderTable.setItems(FXCollections.observableArrayList(historyService.getOrderHistory()));
            selectedOrder = null;
            receiptArea.clear();
            if (preselectedOrderId != null) {
                orderTable.getItems().stream()
                    .filter(order -> order.orderId() == preselectedOrderId)
                    .findFirst()
                    .ifPresent(order -> orderTable.getSelectionModel().select(order));
            }
            showMessage("Saved order history loaded.", false);
        } catch (SQLException exception) {
            showMessage("Could not load order history. Please try again.", true);
        }
    }

    private void showSelectedReceipt() {
        if (selectedOrder == null) {
            showMessage("Select an order to view its details.", true);
            return;
        }
        try {
            receiptArea.setText(receiptService.createReceipt(selectedOrder.orderId()));
            showMessage("Showing saved order details and receipt.", false);
        } catch (SQLException exception) {
            showMessage("Could not load this order's details. Please try again.", true);
        }
    }

    private String formatMoney(long paise) {
        return "₹" + BigDecimal.valueOf(paise, 2).toPlainString();
    }

    private void showMessage(String text, boolean error) {
        messageLabel.setText(text);
        messageLabel.getStyleClass().removeAll("history-error", "history-success");
        messageLabel.getStyleClass().add(error ? "history-error" : "history-success");
    }
}
