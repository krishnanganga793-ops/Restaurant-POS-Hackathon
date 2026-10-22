package com.restaurantpos.ui;

import com.restaurantpos.model.MenuItem;
import com.restaurantpos.model.Order;
import com.restaurantpos.model.OrderItem;
import com.restaurantpos.model.User;
import com.restaurantpos.model.Customer;
import com.restaurantpos.service.OrderService;
import com.restaurantpos.service.LoyaltyService;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Screen for selecting available menu items and saving a billed order. */
public class OrderEntryView {
    private final Stage stage;
    private final User user;
    private final Integer tableId;
    private final OrderService orderService = new OrderService();
    private final LoyaltyService loyaltyService = new LoyaltyService();
    private final ComboBox<Customer> customerBox = new ComboBox<>();
    private final TextField customerPhoneField = new TextField();
    private final Label customerBalanceLabel = new Label("Guest · loyalty disabled");
    private final TableView<MenuItem> menuTable = new TableView<>();
    private final TableView<OrderItem> cartTable = new TableView<>();
    private final ObservableList<OrderItem> cart = FXCollections.observableArrayList();
    private final TextField quantityField = new TextField("1");
    private final Label totalLabel = new Label("Total: ₹0.00");
    private final Label pointsPreviewLabel = new Label("Loyalty points on successful payment: 0");
    private final Label messageLabel = new Label(" ");

    public OrderEntryView(Stage stage, User user) {
        this(stage, user, null);
    }

    public OrderEntryView(Stage stage, User user, Integer tableId) {
        this.stage = stage;
        this.user = user;
        this.tableId = tableId;
    }

    public Scene createScene() {
        configureMenuTable();
        configureCartTable();

        Label heading = new Label("Order Entry & Billing");
        heading.getStyleClass().add("order-heading");
        String tableContext = tableId == null ? "Takeaway order" : "New order for Table " + tableId;
        Label signedIn = new Label(tableContext + " · Signed in as "
            + user.getUsername() + " · " + user.getRole());
        signedIn.getStyleClass().add("order-subtitle");

        Button refreshButton = new Button("Refresh available items");
        refreshButton.getStyleClass().add("order-secondary-button");
        refreshButton.setOnAction(event -> loadAvailableMenu());
        customerBox.setPromptText("Guest order (no loyalty points)");
        customerBox.setPrefWidth(230);
        customerPhoneField.setPromptText("Search by customer phone");
        customerPhoneField.setPrefWidth(190);
        Button searchCustomer = new Button("Find customer");
        searchCustomer.getStyleClass().add("order-secondary-button");
        searchCustomer.setOnAction(event -> searchCustomerByPhone());
        Button addCustomer = new Button("Register customer");
        addCustomer.getStyleClass().add("order-secondary-button");
        addCustomer.setOnAction(event -> registerCustomer());
        Button guestOrder = new Button("Guest / Clear customer");
        guestOrder.getStyleClass().add("order-secondary-button");
        guestOrder.setOnAction(event -> clearCustomerSelection());
        loadCustomers();
        customerBox.valueProperty().addListener((observable, oldValue, newValue) -> updateCustomerBalance());
        HBox navigation = new HBox(
            10, NavigationBar.create(stage, user, "order-secondary-button"), refreshButton);
        HBox customerSearchRow = new HBox(10, new Label("Find by phone"), customerPhoneField,
            searchCustomer, addCustomer);
        customerSearchRow.setAlignment(Pos.CENTER_LEFT);
        HBox customerSelectionRow = new HBox(10, new Label("Selected customer"), customerBox,
            guestOrder, customerBalanceLabel);
        customerSelectionRow.setAlignment(Pos.CENTER_LEFT);
        VBox customerSelection = new VBox(6, customerSearchRow, customerSelectionRow);

        VBox menuPanel = createMenuPanel();
        VBox cartPanel = createCartPanel();
        HBox workArea = new HBox(20, menuPanel, cartPanel);
        HBox.setHgrow(menuPanel, Priority.ALWAYS);
        HBox.setHgrow(cartPanel, Priority.ALWAYS);
        VBox.setVgrow(workArea, Priority.ALWAYS);

        messageLabel.getStyleClass().add("order-message");
        messageLabel.setWrapText(true);
        VBox root = new VBox(12, heading, signedIn, navigation, customerSelection, workArea, messageLabel);
        root.setPadding(new Insets(28));
        root.getStyleClass().add("order-root");

        Scene scene = new Scene(root, 1120, 740);
        scene.getStylesheets().add(
            OrderEntryView.class.getResource("/order.css").toExternalForm()
        );
        stage.setTitle("Restaurant POS - Order Entry");
        stage.setMinWidth(940);
        stage.setMinHeight(650);
        loadAvailableMenu();
        return scene;
    }

    private VBox createMenuPanel() {
        Label title = new Label("Available menu items");
        title.getStyleClass().add("order-panel-heading");
        quantityField.setPrefWidth(90);
        Button addButton = new Button("Add selected item");
        addButton.getStyleClass().add("order-primary-button");
        addButton.setOnAction(event -> addSelectedItem());
        HBox controls = new HBox(10, new Label("Quantity"), quantityField, addButton);
        controls.setAlignment(Pos.CENTER_LEFT);
        VBox panel = new VBox(12, title, menuTable, controls);
        panel.getStyleClass().add("order-panel");
        panel.setPadding(new Insets(18));
        VBox.setVgrow(menuTable, Priority.ALWAYS);
        return panel;
    }

    private VBox createCartPanel() {
        Label title = new Label("Current order");
        title.getStyleClass().add("order-panel-heading");
        TextField newQuantityField = new TextField();
        newQuantityField.setPromptText("New quantity");
        newQuantityField.setPrefWidth(120);

        Button updateButton = new Button("Update quantity");
        updateButton.getStyleClass().add("order-secondary-button");
        updateButton.setOnAction(event -> updateQuantity(newQuantityField.getText()));
        Button removeButton = new Button("Remove item");
        removeButton.getStyleClass().add("order-secondary-button");
        removeButton.setOnAction(event -> removeSelectedItem());
        HBox cartControls = new HBox(8, newQuantityField, updateButton, removeButton);
        cartControls.setAlignment(Pos.CENTER_LEFT);

        totalLabel.getStyleClass().add("order-total");
        Button saveButton = new Button("Save order");
        saveButton.getStyleClass().add("order-primary-button");
        saveButton.setMaxWidth(Double.MAX_VALUE);
        saveButton.setOnAction(event -> saveOrder());

        VBox panel = new VBox(12, title, cartTable, cartControls, totalLabel, pointsPreviewLabel, saveButton);
        panel.getStyleClass().add("order-panel");
        panel.setPadding(new Insets(18));
        VBox.setVgrow(cartTable, Priority.ALWAYS);
        return panel;
    }

    private void configureMenuTable() {
        TableColumn<MenuItem, String> name = new TableColumn<>("Item");
        name.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getName()));
        TableColumn<MenuItem, String> category = new TableColumn<>("Category");
        category.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getCategory()));
        TableColumn<MenuItem, String> price = new TableColumn<>("Unit price");
        price.setCellValueFactory(cell ->
            new ReadOnlyStringWrapper(formatMoney(cell.getValue().getPricePaise())));
        menuTable.getColumns().setAll(name, category, price);
        menuTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        menuTable.getStyleClass().add("order-table");
    }

    private void configureCartTable() {
        TableColumn<OrderItem, String> name = new TableColumn<>("Item");
        name.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
            cell.getValue().getMenuItem().getName()));
        TableColumn<OrderItem, String> price = new TableColumn<>("Unit");
        price.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
            formatMoney(cell.getValue().getMenuItem().getPricePaise())));
        TableColumn<OrderItem, String> quantity = new TableColumn<>("Qty");
        quantity.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
            Integer.toString(cell.getValue().getQuantity())));
        TableColumn<OrderItem, String> subtotal = new TableColumn<>("Subtotal");
        subtotal.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
            formatMoney(cell.getValue().getSubtotalPaise())));
        cartTable.getColumns().setAll(name, price, quantity, subtotal);
        cartTable.setItems(cart);
        cartTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        cartTable.getStyleClass().add("order-table");
    }

    private void loadAvailableMenu() {
        try {
            menuTable.setItems(FXCollections.observableArrayList(
                orderService.getAvailableMenuItems()));
            showMessage("Available menu refreshed.", false);
        } catch (SQLException exception) {
            showMessage("Could not load available menu items. Please try again.", true);
        }
    }

    private void addSelectedItem() {
        MenuItem selected = menuTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Select an available menu item first.", true);
            return;
        }
        try {
            int quantity = parseQuantity(quantityField.getText());
            for (int i = 0; i < cart.size(); i++) {
                OrderItem existing = cart.get(i);
                if (existing.getMenuItem().getId() == selected.getId()) {
                    int combined = Math.addExact(existing.getQuantity(), quantity);
                    cart.set(i, new OrderItem(selected, combined));
                    updateTotal();
                    showMessage("Item quantity updated in the cart.", false);
                    return;
                }
            }
            cart.add(new OrderItem(selected, quantity));
            updateTotal();
            showMessage("Item added to the cart.", false);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            showMessage(exception.getMessage() == null
                ? "That quantity is too large." : exception.getMessage(), true);
        }
    }

    private void updateQuantity(String text) {
        OrderItem selected = cartTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Select a cart entry before changing its quantity.", true);
            return;
        }
        try {
            int quantity = parseQuantity(text);
            int index = cart.indexOf(selected);
            cart.set(index, new OrderItem(selected.getMenuItem(), quantity));
            updateTotal();
            showMessage("Cart quantity updated.", false);
        } catch (IllegalArgumentException exception) {
            showMessage(exception.getMessage(), true);
        }
    }

    private void removeSelectedItem() {
        OrderItem selected = cartTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Select a cart entry to remove it.", true);
            return;
        }
        cart.remove(selected);
        updateTotal();
        showMessage("Item removed from the cart.", false);
    }

    private void saveOrder() {
        if (cart.isEmpty()) {
            showMessage("Add at least one item before saving the order.", true);
            return;
        }
        try {
            Customer customer = customerBox.getValue();
            Order order = orderService.saveOrder(user.getUserId(), new ArrayList<>(cart), tableId,
                customer == null ? null : customer.customerId());
            long totalPaise = order.getTotalPaise();
            cart.clear();
            updateTotal();
            showMessage("Order #" + order.getOrderId()
                + (tableId == null ? " saved." : " saved for Table " + tableId + ".") + " Total: "
                + formatMoney(totalPaise), false);
        } catch (IllegalArgumentException exception) {
            showMessage(exception.getMessage(), true);
        } catch (SQLException exception) {
            showMessage(exception.getMessage() != null
                && exception.getMessage().contains("no longer available")
                    ? exception.getMessage()
                    : "Could not save the order. Please try again.", true);
        }
    }

    private void loadCustomers() {
        try { customerBox.setItems(FXCollections.observableArrayList(loyaltyService.getCustomers())); }
        catch (SQLException e) { showMessage("Could not load customers.", true); }
    }

    private void searchCustomerByPhone() {
        try {
            Customer customer = loyaltyService.findCustomerByPhone(customerPhoneField.getText());
            if (customer != null) {
                selectCustomer(customer);
                showMessage("Selected registered customer " + customer.name() + ".", false);
                return;
            }

            String phone = customerPhoneField.getText().trim();
            customerBox.getSelectionModel().clearSelection();
            updateCustomerBalance();
            customerPhoneField.setText(phone);
            ButtonType register = new ButtonType("Register customer", ButtonBar.ButtonData.OK_DONE);
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "No customer is registered with " + phone + ". Would you like to register them?");
            alert.setTitle("Customer not found");
            alert.setHeaderText(null);
            alert.getButtonTypes().setAll(register, ButtonType.CANCEL);
            if (alert.showAndWait().filter(register::equals).isPresent()) {
                showCustomerRegistrationForm(phone);
            } else {
                showMessage("No matching customer. The order can continue as a guest.", false);
            }
        } catch (IllegalArgumentException exception) {
            showMessage(exception.getMessage(), true);
        } catch (SQLException exception) {
            showMessage("Could not search for this customer. Please try again.", true);
        }
    }

    private void registerCustomer() {
        String phone = customerPhoneField.getText() == null ? "" : customerPhoneField.getText().trim();
        showCustomerRegistrationForm(phone);
    }

    private void showCustomerRegistrationForm(String phone) {
        Dialog<Customer> dialog = new Dialog<>();
        dialog.setTitle("Register customer");
        dialog.setHeaderText("Enter the customer's name and phone number");
        ButtonType register = new ButtonType("Register", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(register, ButtonType.CANCEL);

        TextField nameField = new TextField();
        nameField.setPromptText("Customer name");
        TextField phoneField = new TextField(phone);
        phoneField.setPromptText("Phone number");
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("Name:"), nameField);
        form.addRow(1, new Label("Phone:"), phoneField);
        dialog.getDialogPane().setContent(form);
        dialog.setResultConverter(button -> button == register
            ? new Customer(0, nameField.getText().trim(), phoneField.getText().trim()) : null);
        dialog.getDialogPane().lookupButton(register).addEventFilter(
            javafx.event.ActionEvent.ACTION, event -> {
                String name = nameField.getText() == null ? "" : nameField.getText().trim();
                String enteredPhone = phoneField.getText() == null ? "" : phoneField.getText().trim();
                if (name.isEmpty()) {
                    showMessage("Enter the customer's name.", true);
                    event.consume();
                    return;
                }
                if (!enteredPhone.matches("[+0-9][0-9 -]{6,19}")) {
                    showMessage("Enter a valid phone number.", true);
                    event.consume();
                    return;
                }
                phoneField.setText(enteredPhone);
                try {
                    Customer existing = loyaltyService.findCustomerByPhone(enteredPhone);
                    if (existing != null) {
                        selectCustomer(existing);
                        showMessage("That phone number is already registered. Selected "
                            + existing.name() + " instead.", false);
                        event.consume();
                        dialog.setResult(existing);
                        dialog.close();
                    }
                } catch (SQLException exception) {
                    showMessage("Could not check whether this customer is already registered.", true);
                    event.consume();
                }
            });

        var submitted = dialog.showAndWait();
        if (submitted.isEmpty()) return;
        Customer formCustomer = submitted.get();
        if (formCustomer.customerId() != 0) return;
        try {
            Customer customer = loyaltyService.registerCustomer(formCustomer.name(), formCustomer.phone());
            selectCustomer(customer);
            customerPhoneField.setText(customer.phone());
            showMessage("Customer registered and selected.", false);
        } catch (IllegalArgumentException exception) {
            showMessage(exception.getMessage(), true);
        } catch (SQLException exception) {
            // Another cashier may have registered this phone while the dialog was open.
            try {
                Customer existing = loyaltyService.findCustomerByPhone(formCustomer.phone());
                if (existing != null) {
                    selectCustomer(existing);
                    showMessage("That phone number is already registered. Selected "
                        + existing.name() + " instead.", false);
                    return;
                }
            } catch (SQLException | IllegalArgumentException lookupException) {
                // Keep the original registration failure as the user-facing result.
            }
            showMessage("Could not register the customer. Please check the phone number and try again.", true);
        }
    }

    private void selectCustomer(Customer customer) {
        loadCustomers();
        customerBox.getItems().stream()
            .filter(existing -> existing.customerId() == customer.customerId())
            .findFirst()
            .ifPresent(customerBox.getSelectionModel()::select);
        customerPhoneField.setText(customer.phone());
        updateCustomerBalance();
    }

    private void clearCustomerSelection() {
        customerBox.getSelectionModel().clearSelection();
        customerPhoneField.clear();
        updateCustomerBalance();
        showMessage("Guest order selected. No loyalty points will be earned or redeemed.", false);
    }

    private int pointsPreview() {
        if (customerBox.getValue() == null) return 0;
        try { return loyaltyService.calculateEarned(new ArrayList<>(cart)); }
        catch (Exception e) { return 0; }
    }

    private void updateCustomerBalance() {
        Customer customer = customerBox.getValue();
        if (customer == null) { customerBalanceLabel.setText("Guest · loyalty disabled"); }
        else {
            try { customerBalanceLabel.setText(customer.name() + " · "
                + loyaltyService.getBalance(customer.customerId()) + " points"); }
            catch (SQLException e) { customerBalanceLabel.setText("Balance unavailable"); }
        }
        updateTotal();
    }

    private int parseQuantity(String text) {
        try {
            int quantity = Integer.parseInt(text == null ? "" : text.trim());
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than zero.");
            }
            return quantity;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a whole-number quantity greater than zero.");
        }
    }

    private void updateTotal() {
        pointsPreviewLabel.setText("Loyalty points on successful payment: " + pointsPreview());
        try {
            long totalPaise = 0;
            for (OrderItem item : cart) {
                totalPaise = Math.addExact(totalPaise, item.getSubtotalPaise());
            }
            totalLabel.setText("Total: " + formatMoney(totalPaise));
        } catch (ArithmeticException exception) {
            totalLabel.setText("Total: amount too large");
        }
    }

    private String formatMoney(long paise) {
        return "₹" + BigDecimal.valueOf(paise, 2).toPlainString();
    }

    private void showMessage(String text, boolean error) {
        messageLabel.setText(text);
        messageLabel.getStyleClass().removeAll("order-error", "order-success");
        messageLabel.getStyleClass().add(error ? "order-error" : "order-success");
    }
}
