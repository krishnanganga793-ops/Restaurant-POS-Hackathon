package com.restaurantpos.ui;

import com.restaurantpos.model.User;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/** Shared navigation buttons for the staff screens. */
final class NavigationBar {
    private NavigationBar() {
    }

    static HBox create(Stage stage, User user, String styleClass) {
        Button dashboard = button("Dashboard", styleClass,
            () -> {
                SalesDashboardView view = new SalesDashboardView(stage, user);
                stage.setScene(view.createScene());
                stage.show();
            });
        Button menu = button("Menu Management", styleClass,
            () -> stage.setScene(new MenuManagementView(stage, user).createScene()));
        Button order = button("Order Entry", styleClass,
            () -> stage.setScene(new OrderEntryView(stage, user).createScene()));
        Button payments = button("Payments", styleClass,
            () -> stage.setScene(new PaymentView(stage, user).createScene()));
        Button history = button("Order History", styleClass,
            () -> stage.setScene(new OrderHistoryView(stage, user).createScene()));
        Button tables = button("Tables", styleClass,
            () -> stage.setScene(new TableManagementView(stage, user).createScene()));
        return new HBox(8, dashboard, menu, order, payments, history, tables);
    }

    private static Button button(String text, String styleClass, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add(styleClass);
        button.setOnAction(event -> action.run());
        return button;
    }
}
