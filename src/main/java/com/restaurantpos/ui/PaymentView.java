package com.restaurantpos.ui;

import com.restaurantpos.model.Order;
import com.restaurantpos.model.OrderItem;
import com.restaurantpos.model.OrderSummary;
import com.restaurantpos.model.Payment;
import com.restaurantpos.model.LoyaltyCheckout;
import com.restaurantpos.model.User;
import com.restaurantpos.service.OrderHistoryService;
import com.restaurantpos.service.PaymentService;
import com.restaurantpos.service.ReceiptService;
import com.restaurantpos.service.LoyaltyService;
import java.math.BigDecimal;
import java.sql.SQLException;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Staff screen for recording payment against a saved unpaid order. */
public class PaymentView {
    private final Stage stage;
    private final User user;
    private final PaymentService paymentService = new PaymentService();
    private final OrderHistoryService orderService = new OrderHistoryService();
    private final ReceiptService receiptService = new ReceiptService();
    private final LoyaltyService loyaltyService = new LoyaltyService();
    private final TableView<OrderSummary> orderTable = new TableView<>();
    private final TextArea detailsArea = new TextArea();
    private final TextArea receiptArea = new TextArea();
    private final TextField amountField = new TextField();
    private final TextField redeemPointsField = new TextField("0");
    private final Label loyaltyLabel = new Label("Select an order to view loyalty balance.");
    private final ComboBox<String> methodBox = new ComboBox<>();
    private final Label messageLabel = new Label(" ");
    private OrderSummary selectedOrder;
    private boolean normalizingRedemptionInput;

    public PaymentView(Stage stage, User user) {
        this.stage = stage;
        this.user = user;
    }

    public Scene createScene() {
        Label heading = new Label("Payments");
        heading.getStyleClass().add("pay-heading");
        Label signedIn = new Label("Signed in as " + user.getUsername() + " · " + user.getRole());
        signedIn.getStyleClass().add("pay-subtitle");
        HBox navigation = NavigationBar.create(stage, user, "pay-nav-button");
        Button refresh = new Button("Refresh unpaid orders");
        refresh.getStyleClass().add("pay-nav-button");
        refresh.setOnAction(event -> loadUnpaidOrders());

        configureOrderTable();
        detailsArea.setEditable(false);
        detailsArea.setWrapText(true);
        detailsArea.setPromptText("Select an unpaid order to see its items and amount due.");
        receiptArea.setEditable(false);
        receiptArea.setWrapText(true);
        receiptArea.setPromptText("A receipt will appear here after payment is recorded.");
        methodBox.setItems(FXCollections.observableArrayList("Cash", "Card", "UPI", "Other"));
        methodBox.setValue("Cash");
        amountField.setPromptText("Amount in rupees");
        redeemPointsField.setPromptText("Points to redeem");
        redeemPointsField.setDisable(true);
        redeemPointsField.setTextFormatter(new TextFormatter<String>(change ->
            change.getControlNewText().matches("\\d{0,10}") ? change : null));
        redeemPointsField.textProperty().addListener((o, oldText, newText) -> {
            if (!normalizingRedemptionInput) updateQuote();
        });

        Button recordButton = new Button("Record full payment");
        recordButton.getStyleClass().add("pay-primary-button");
        recordButton.setOnAction(event -> recordPayment());
        Button saveReceipt = new Button("Save receipt as text");
        saveReceipt.getStyleClass().add("pay-nav-button");
        saveReceipt.setOnAction(event -> ReceiptViewSupport.save(stage, receiptArea));
        Button printReceipt = new Button("Print receipt");
        printReceipt.getStyleClass().add("pay-nav-button");
        printReceipt.setOnAction(event -> ReceiptViewSupport.print(stage, receiptArea));

        VBox paymentControls = new VBox(9,
            new Label("Loyalty balance and discount"), loyaltyLabel,
            new Label("Redeem points (optional)"), redeemPointsField,
            new Label("Amount due (₹)"), amountField,
            new Label("Payment method"), methodBox,
            recordButton
        );
        HBox receiptButtons = new HBox(8, saveReceipt, printReceipt);
        VBox right = new VBox(10,
            new Label("Selected order"), detailsArea, paymentControls,
            new Label("Receipt"), receiptArea, receiptButtons
        );
        right.getStyleClass().add("pay-panel");
        right.setPadding(new Insets(18));
        VBox.setVgrow(detailsArea, Priority.ALWAYS);
        VBox.setVgrow(receiptArea, Priority.ALWAYS);

        VBox left = new VBox(10, new Label("Unpaid orders"), orderTable, refresh);
        left.getStyleClass().add("pay-panel");
        left.setPadding(new Insets(18));
        left.setPrefWidth(440);
        VBox.setVgrow(orderTable, Priority.ALWAYS);
        HBox work = new HBox(18, left, right);
        HBox.setHgrow(right, Priority.ALWAYS);
        VBox.setVgrow(work, Priority.ALWAYS);
        messageLabel.getStyleClass().add("pay-message");
        messageLabel.setWrapText(true);

        VBox root = new VBox(12, heading, signedIn, navigation, work, messageLabel);
        root.setPadding(new Insets(26));
        root.getStyleClass().add("pay-root");
        Scene scene = new Scene(root, 1180, 800);
        scene.getStylesheets().add(PaymentView.class.getResource("/payment.css").toExternalForm());
        stage.setTitle("Restaurant POS - Payments");
        stage.setMinWidth(980);
        stage.setMinHeight(700);
        loadUnpaidOrders();
        return scene;
    }

    private void configureOrderTable() {
        TableColumn<OrderSummary, String> order = new TableColumn<>("Order");
        order.setCellValueFactory(cell -> new ReadOnlyStringWrapper("#" + cell.getValue().orderId()));
        TableColumn<OrderSummary, String> date = new TableColumn<>("Date/time");
        date.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().createdAt()));
        TableColumn<OrderSummary, String> total = new TableColumn<>("Due");
        total.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatMoney(cell.getValue().totalPaise())));
        orderTable.getColumns().setAll(order, date, total);
        orderTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        orderTable.getStyleClass().add("pay-table");
        orderTable.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldOrder, newOrder) -> selectOrder(newOrder));
    }

    private void loadUnpaidOrders() {
        try {
            orderTable.setItems(FXCollections.observableArrayList(paymentService.getUnpaidOrders()));
            selectedOrder = null;
            detailsArea.clear();
            amountField.clear();
            setRedemptionText("0");
            redeemPointsField.setDisable(true);
            loyaltyLabel.setText("Select an order to view loyalty balance.");
            showMessage("Unpaid orders loaded.", false);
        } catch (SQLException exception) {
            showMessage("Could not load unpaid orders. Please try again.", true);
        }
    }

    private void selectOrder(OrderSummary summary) {
        selectedOrder = summary;
        receiptArea.clear();
        if (summary == null) {
            detailsArea.clear();
            amountField.clear();
            setRedemptionText("0");
            redeemPointsField.setDisable(true);
            loyaltyLabel.setText("Select an order to view loyalty balance.");
            return;
        }
        try {
            Order order = orderService.getOrder(summary.orderId());
            StringBuilder details = new StringBuilder("Order #")
                .append(order.getOrderId()).append("\nCreated: ")
                .append(order.getCreatedAt()).append("\n\nItems:\n");
            for (OrderItem item : order.getItems()) {
                details.append(item.getMenuItem().getName())
                    .append(" × ").append(item.getQuantity())
                    .append("  @ ").append(formatMoney(item.getMenuItem().getPricePaise()))
                    .append("  = ").append(formatMoney(item.getSubtotalPaise())).append('\n');
            }
            details.append("\nSubtotal: ").append(formatMoney(summary.totalPaise()));
            LoyaltyCheckout checkout = paymentService.getLoyaltyCheckout(summary.orderId(), 0);
            int points = checkout.customerId() == null ? 0 : loyaltyService.calculateEarned(order.getItems());
            details.append("\nCustomer: ").append(checkout.customerId() == null ? "Guest" : checkout.customerName())
                .append("\nPoints earned on successful payment: ").append(points);
            detailsArea.setText(details.toString());
            setRedemptionText("0");
            updateQuote();
            showMessage("Check the amount due, then record the full payment.", false);
        } catch (SQLException exception) {
            showMessage("Could not load this order's details.", true);
        }
    }

    private void updateQuote() {
        if (selectedOrder == null) {
            redeemPointsField.setDisable(true);
            return;
        }
        try {
            LoyaltyCheckout current = paymentService.getLoyaltyCheckout(selectedOrder.orderId(), 0);
            if (current.customerId() == null || current.availablePoints() <= 0) {
                redeemPointsField.setDisable(true);
                setRedemptionText("0");
                loyaltyLabel.setText(current.customerId() == null
                    ? "Guest order · no loyalty points available"
                    : current.customerName() + " · available 0 points · No loyalty points available.");
                amountField.setText(BigDecimal.valueOf(current.subtotalPaise(), 2).toPlainString());
                return;
            }

            redeemPointsField.setDisable(false);
            redeemPointsField.setPromptText("0 to " + current.availablePoints() + " points");
            int maxRedeemable = (int) Math.min(current.availablePoints(), Integer.MAX_VALUE);
            String input = redeemPointsField.getText();
            long requestedValue = input == null || input.isBlank() ? 0L : Long.parseLong(input);
            boolean wasLimited = requestedValue > maxRedeemable;
            if (wasLimited) {
                requestedValue = maxRedeemable;
                setRedemptionText(Integer.toString(maxRedeemable));
            }
            int requested = (int) requestedValue;
            LoyaltyCheckout quote = requested == 0 ? current
                : paymentService.getLoyaltyCheckout(selectedOrder.orderId(), requested);
            long discount = quote.discountPaise();
            long debited = Math.min(requested, discount / 100L * quote.pointsPerRupee());
            loyaltyLabel.setText(quote.customerName() + " · available " + quote.availablePoints()
                + " points · redeeming " + debited + " points = discount " + formatMoney(discount)
                + " (" + quote.pointsPerRupee() + " points per ₹1)"
                + (wasLimited ? " · limited to the available balance" : ""));
            amountField.setText(BigDecimal.valueOf(quote.subtotalPaise() - discount, 2).toPlainString());
        } catch (NumberFormatException e) {
            setRedemptionText("0");
            updateQuote();
        } catch (SQLException e) {
            redeemPointsField.setDisable(true);
            setRedemptionText("0");
            loyaltyLabel.setText("Could not refresh the loyalty balance. Redemption is disabled.");
        }
    }

    private void setRedemptionText(String text) {
        if (text.equals(redeemPointsField.getText())) return;
        normalizingRedemptionInput = true;
        try {
            redeemPointsField.setText(text);
        } finally {
            normalizingRedemptionInput = false;
        }
    }

    private void recordPayment() {
        if (selectedOrder == null) {
            showMessage("Select an unpaid order first.", true);
            return;
        }
        Payment payment;
        try {
            if (methodBox.getValue() == null) {
                showMessage("Choose a payment method.", true);
                return;
            }
            String method = methodBox.getValue().toUpperCase();
            int requestedPoints = redeemPointsField.getText().isBlank() ? 0
                : Integer.parseInt(redeemPointsField.getText().trim());
            payment = paymentService.recordPayment(
                selectedOrder.orderId(), amountField.getText(), method, requestedPoints);
        } catch (IllegalArgumentException exception) {
            showMessage(exception.getMessage(), true);
            return;
        } catch (SQLException exception) {
            String detail = exception.getMessage();
            showMessage(detail != null && (detail.contains("already been paid")
                || detail.contains("match the final") || detail.contains("enough loyalty")) ? detail
                : "Could not record payment. No paid status was saved; please try again.", true);
            return;
        }
        try {
            String receipt = receiptService.createReceipt(payment.getOrderId());
            loadUnpaidOrders();
            receiptArea.setText(receipt);
            showMessage("Payment recorded and order marked paid.", false);
        } catch (SQLException exception) {
            loadUnpaidOrders();
            showMessage("Payment was recorded, but the receipt could not be loaded. "
                + "Open Order History to view it.", true);
        }
    }

    private String formatMoney(long paise) {
        return "₹" + BigDecimal.valueOf(paise, 2).toPlainString();
    }

    private void showMessage(String text, boolean error) {
        messageLabel.setText(text);
        messageLabel.getStyleClass().removeAll("pay-error", "pay-success");
        messageLabel.getStyleClass().add(error ? "pay-error" : "pay-success");
    }
}
