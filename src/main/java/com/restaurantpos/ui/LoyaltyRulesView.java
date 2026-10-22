package com.restaurantpos.ui;

import com.restaurantpos.model.LoyaltyRule;
import com.restaurantpos.model.MenuItem;
import com.restaurantpos.model.User;
import com.restaurantpos.service.LoyaltyService;
import java.sql.SQLException;
import java.math.BigDecimal;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ListCell;
import javafx.util.StringConverter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Administrator screen for loyalty rules and the points-to-currency conversion. */
public class LoyaltyRulesView {
    private final Stage stage;
    private final User user;
    private final LoyaltyService service = new LoyaltyService();
    private final TableView<LoyaltyRule> table = new TableView<>();
    private final ComboBox<MenuItem> itemBox = new ComboBox<>();
    private final TextField minimum = new TextField();
    private final TextField points = new TextField();
    private final TextField rate = new TextField();
    private final CheckBox active = new CheckBox("Rule active");
    private final Label message = new Label(" ");

    public LoyaltyRulesView(Stage stage, User user) {
        this.stage = stage;
        this.user = user;
    }

    public Scene createScene() {
        if (!"ADMIN".equals(user.getRole())) {
            throw new SecurityException("Only administrators can manage loyalty rules.");
        }
        configureRuleTable();
        configureFoodItemSelector();
        try {
            loadFoodItems();
        } catch (SQLException exception) {
            message.setText("Could not load food items from the menu.");
        }
        loadConversionRate();
        configureInputs();

        Button saveRule = new Button("Save rule");
        saveRule.setOnAction(event -> saveRule());
        Button deleteRule = new Button("Delete selected rule");
        deleteRule.setOnAction(event -> deleteRule());
        Button saveRate = new Button("Save conversion rate");
        saveRate.setOnAction(event -> saveRate());
        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> refreshConfiguration());

        VBox editor = new VBox(10,
            new Label("Food item"), itemBox,
            new Label("Minimum quantity"), minimum,
            new Label("Points awarded once per qualifying order"), points,
            active, saveRule, deleteRule, new Separator(),
            new Label("Points required for ₹1 discount"), rate, saveRate, message);
        editor.setPadding(new Insets(18));
        editor.setPrefWidth(320);
        HBox content = new HBox(16, table, editor);
        HBox.setHgrow(table, Priority.ALWAYS);
        VBox.setVgrow(content, Priority.ALWAYS);

        VBox root = new VBox(12,
            new Label("Loyalty Rule Management · Admin"),
            NavigationBar.create(stage, user, "menu-secondary-button"), content, refresh);
        root.setPadding(new Insets(24));
        stage.setTitle("Restaurant POS - Loyalty Rules");
        stage.setMinWidth(900);
        stage.setMinHeight(650);
        loadRules();
        return new Scene(root, 1100, 720);
    }

    private void configureRuleTable() {
        TableColumn<LoyaltyRule, String> item = new TableColumn<>("Eligible food");
        item.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().itemName()));
        TableColumn<LoyaltyRule, String> quantity = new TableColumn<>("Minimum qty");
        quantity.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
            Integer.toString(cell.getValue().minimumQuantity())));
        TableColumn<LoyaltyRule, String> awarded = new TableColumn<>("Points");
        awarded.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
            Integer.toString(cell.getValue().pointsAwarded())));
        TableColumn<LoyaltyRule, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
            !cell.getValue().itemAvailable() ? "Unavailable · not earning"
                : cell.getValue().active() ? "Active" : "Inactive"));
        table.getColumns().setAll(item, quantity, awarded, status);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldRule, rule) -> {
            if (rule != null) selectRule(rule);
        });
    }

    private void selectRule(LoyaltyRule rule) {
        itemBox.getSelectionModel().clearSelection();
        if (rule.itemAvailable()) {
            itemBox.getItems().stream()
                .filter(item -> item.getId() == rule.menuItemId())
                .findFirst().ifPresent(itemBox.getSelectionModel()::select);
        } else {
            message.setText("This rule is preserved but will not earn points until the item is available.");
        }
        minimum.setText(Integer.toString(rule.minimumQuantity()));
        points.setText(Integer.toString(rule.pointsAwarded()));
        active.setSelected(rule.active());
    }

    private void configureFoodItemSelector() {
        itemBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(MenuItem item) {
                if (item == null) return "";
                String price = "₹" + BigDecimal.valueOf(item.getPricePaise(), 2).toPlainString();
                return item.getName() + " · " + item.getCategory() + " · " + price;
            }

            @Override
            public MenuItem fromString(String value) {
                return null;
            }
        });
        itemBox.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(MenuItem item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : itemBox.getConverter().toString(item));
            }
        });
    }

    private void loadFoodItems() throws SQLException {
        var foodItems = FXCollections.observableArrayList(service.getFoodItems());
        itemBox.setItems(foodItems);
        itemBox.setDisable(foodItems.isEmpty());
        itemBox.setPromptText(foodItems.isEmpty()
            ? "No food items available · add one in Menu Management"
            : "Choose food item");
    }

    private void loadConversionRate() {
        try {
            rate.setText(Integer.toString(service.getPointsPerRupee()));
        } catch (SQLException exception) {
            message.setText("Could not load the points conversion rate.");
        }
    }

    private void refreshConfiguration() {
        try {
            loadFoodItems();
            loadRules();
            loadConversionRate();
            if (itemBox.getItems().isEmpty()) {
                message.setText("No eligible food items found. Add a Food item in Menu Management.");
            } else {
                message.setText("Food items and loyalty rules refreshed.");
            }
        } catch (SQLException exception) {
            message.setText("Could not refresh menu food items.");
        }
    }

    private void configureInputs() {
        minimum.setPromptText("Minimum quantity");
        points.setPromptText("Points awarded");
        rate.setPromptText("Points per ₹1");
    }

    private void saveRule() {
        try {
            MenuItem selected = itemBox.getValue();
            if (selected == null) throw new IllegalArgumentException("Choose a food item.");
            service.saveRule(selected.getId(), Integer.parseInt(minimum.getText().trim()),
                Integer.parseInt(points.getText().trim()), active.isSelected());
            loadRules();
            message.setText("Rule saved.");
        } catch (IllegalArgumentException exception) {
            message.setText(exception.getMessage());
        } catch (SQLException exception) {
            message.setText("Could not save this rule.");
        }
    }

    private void deleteRule() {
        LoyaltyRule selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            message.setText("Select a rule first.");
            return;
        }
        try {
            service.deleteRule(selected.menuItemId());
            loadRules();
            message.setText("Rule deleted.");
        } catch (SQLException exception) {
            message.setText("Could not delete this rule.");
        }
    }

    private void saveRate() {
        try {
            service.setPointsPerRupee(Integer.parseInt(rate.getText().trim()));
            message.setText("Conversion rate saved.");
        } catch (IllegalArgumentException exception) {
            message.setText(exception.getMessage());
        } catch (SQLException exception) {
            message.setText("Could not save conversion rate.");
        }
    }

    private void loadRules() {
        try {
            table.setItems(FXCollections.observableArrayList(service.getRules()));
        } catch (SQLException exception) {
            message.setText("Could not load rules.");
        }
    }
}
