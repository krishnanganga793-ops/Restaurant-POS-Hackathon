package com.restaurantpos.ui;

import com.restaurantpos.model.SalesDashboardSummary;
import com.restaurantpos.model.User;
import com.restaurantpos.service.DashboardService;
import java.math.BigDecimal;
import java.sql.SQLException;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Summary cards for today's saved sales and orders. */
public class SalesDashboardView {
    private final Stage stage;
    private final User user;
    private final DashboardService dashboardService = new DashboardService();
    private final Label salesValue = metricValue();
    private final Label ordersValue = metricValue();
    private final Label paidValue = metricValue();
    private final Label unpaidValue = metricValue();
    private final Label averageValue = metricValue();
    private final Label messageLabel = new Label(" ");
    private Button refreshButton;

    public SalesDashboardView(Stage stage, User user) {
        this.stage = stage;
        this.user = user;
    }

    public Scene createScene() {
        Label heading = new Label("Sales Dashboard");
        heading.getStyleClass().add("dashboard-heading");
        Label signedIn = new Label("Today's activity · signed in as "
            + user.getUsername() + " · " + user.getRole());
        signedIn.getStyleClass().add("dashboard-subtitle");

        refreshButton = new Button("Refresh figures");
        refreshButton.getStyleClass().add("dashboard-refresh");
        refreshButton.setOnAction(event -> loadSummaryAsync());

        GridPane cards = new GridPane();
        cards.setHgap(18);
        cards.setVgap(18);
        for (int i = 0; i < 3; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / 3);
            column.setHgrow(Priority.ALWAYS);
            cards.getColumnConstraints().add(column);
        }
        cards.add(createCard("Today's sales", salesValue, "Payments recorded today"), 0, 0);
        cards.add(createCard("Orders created", ordersValue, "Orders created today"), 1, 0);
        cards.add(createCard("Paid orders", paidValue, "Orders with recorded payments"), 2, 0);
        cards.add(createCard("Unpaid orders", unpaidValue, "Open orders without payments"), 0, 1);
        cards.add(createCard("Average order value", averageValue,
            "All non-cancelled orders"), 1, 1);
        GridPane.setColumnSpan(cards.getChildren().get(cards.getChildren().size() - 1), 2);

        messageLabel.getStyleClass().add("dashboard-message");
        VBox root = new VBox(16, heading, signedIn,
            NavigationBar.create(stage, user, "dashboard-nav-button"),
            refreshButton, cards, messageLabel);
        root.setPadding(new Insets(30));
        root.getStyleClass().add("dashboard-root");
        VBox.setVgrow(cards, Priority.ALWAYS);

        Scene scene = new Scene(root, 1160, 720);
        scene.getStylesheets().add(
            SalesDashboardView.class.getResource("/dashboard.css").toExternalForm());
        stage.setTitle("Restaurant POS - Sales Dashboard");
        stage.setMinWidth(980);
        stage.setMinHeight(650);
        loadSummaryAsync();
        return scene;
    }

    private VBox createCard(String title, Label value, String description) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dashboard-card-title");
        Label descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("dashboard-card-description");
        VBox card = new VBox(12, titleLabel, value, descriptionLabel);
        card.setPadding(new Insets(22));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinHeight(150);
        card.getStyleClass().add("dashboard-card");
        return card;
    }

    private Label metricValue() {
        Label label = new Label("Loading…");
        label.getStyleClass().add("dashboard-card-value");
        return label;
    }

    private void loadSummaryAsync() {
        refreshButton.setDisable(true);
        messageLabel.setText("Loading today's database figures...");
        messageLabel.getStyleClass().remove("dashboard-error");

        Task<SalesDashboardSummary> task = new Task<>() {
            @Override
            protected SalesDashboardSummary call() throws SQLException {
                return dashboardService.getTodaySummary();
            }
        };
        task.setOnSucceeded(event -> {
            SalesDashboardSummary summary = task.getValue();
            clearMetricErrorStyles();
            salesValue.setText(formatMoney(summary.salesTodayPaise()));
            ordersValue.setText(Long.toString(summary.ordersCreatedToday()));
            paidValue.setText(Long.toString(summary.paidOrdersCount()));
            unpaidValue.setText(Long.toString(summary.unpaidOrdersCount()));
            averageValue.setText(formatMoney(summary.averageOrderValuePaise()));
            messageLabel.setText("Figures loaded from the local SQLite database.");
            messageLabel.getStyleClass().remove("dashboard-error");
            refreshButton.setDisable(false);
        });
        task.setOnFailed(event -> {
            setMetricError(salesValue);
            setMetricError(ordersValue);
            setMetricError(paidValue);
            setMetricError(unpaidValue);
            setMetricError(averageValue);
            Throwable cause = task.getException();
            messageLabel.setText("Could not load dashboard figures: "
                + (cause == null || cause.getMessage() == null
                    ? "database error. Please try refreshing."
                    : cause.getMessage()));
            if (!messageLabel.getStyleClass().contains("dashboard-error")) {
                messageLabel.getStyleClass().add("dashboard-error");
            }
            refreshButton.setDisable(false);
        });
        Thread loader = new Thread(task, "sales-dashboard-loader");
        loader.setDaemon(true);
        loader.start();
    }

    private void setMetricError(Label label) {
        label.setText("Error");
        if (!label.getStyleClass().contains("dashboard-error-value")) {
            label.getStyleClass().add("dashboard-error-value");
        }
    }

    private void clearMetricErrorStyles() {
        for (Label label : new Label[] {salesValue, ordersValue, paidValue, unpaidValue, averageValue}) {
            label.getStyleClass().remove("dashboard-error-value");
        }
    }

    private String formatMoney(long paise) {
        return "\u20B9" + BigDecimal.valueOf(paise, 2).toPlainString();
    }
}
