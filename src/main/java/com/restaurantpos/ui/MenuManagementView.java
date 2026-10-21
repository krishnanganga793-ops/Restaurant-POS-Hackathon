package com.restaurantpos.ui;

import com.restaurantpos.model.MenuItem;
import com.restaurantpos.model.User;
import com.restaurantpos.service.MenuService;
import java.math.BigDecimal;
import java.sql.SQLException;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** JavaFX screen for viewing and maintaining the restaurant's menu. */
public class MenuManagementView {
    private final Stage stage;
    private final User user;
    private final MenuService menuService = new MenuService();
    private final TableView<MenuItem> itemTable = new TableView<>();
    private final TextField nameField = new TextField();
    private final ComboBox<String> categoryBox = new ComboBox<>();
    private final TextField priceField = new TextField();
    private final CheckBox availableBox = new CheckBox("Available for ordering");
    private final Label messageLabel = new Label(" ");
    private MenuItem selectedItem;

    public MenuManagementView(Stage stage, User user) {
        this.stage = stage;
        this.user = user;
    }

    public Scene createScene() {
        Label heading = new Label("Menu Management");
        heading.getStyleClass().add("menu-heading");
        Label welcome = new Label("Signed in as " + user.getUsername() + " · " + user.getRole());
        welcome.getStyleClass().add("menu-subtitle");

        configureTable();
        VBox editor = createEditor();
        HBox content = new HBox(24, itemTable, editor);
        content.setAlignment(Pos.TOP_CENTER);
        HBox.setHgrow(itemTable, Priority.ALWAYS);

        Button refreshButton = new Button("Refresh list");
        refreshButton.getStyleClass().add("menu-secondary-button");
        refreshButton.setOnAction(event -> loadItems());
        HBox navigation = new HBox(
            10, NavigationBar.create(stage, user, "menu-secondary-button"), refreshButton);

        VBox root = new VBox(12, heading, welcome, navigation, content);
        root.setPadding(new Insets(30));
        root.getStyleClass().add("menu-root");
        VBox.setVgrow(content, Priority.ALWAYS);

        Scene scene = new Scene(root, 1100, 720);
        scene.getStylesheets().add(
            MenuManagementView.class.getResource("/menu.css").toExternalForm()
        );
        stage.setTitle("Restaurant POS - Menu Management");
        stage.setMinWidth(900);
        stage.setMinHeight(620);
        loadItems();
        return scene;
    }

    private void configureTable() {
        TableColumn<MenuItem, String> nameColumn = new TableColumn<>("Menu item");
        nameColumn.setCellValueFactory(cell ->
            new ReadOnlyStringWrapper(cell.getValue().getName()));
        TableColumn<MenuItem, String> categoryColumn = new TableColumn<>("Category");
        categoryColumn.setCellValueFactory(cell ->
            new ReadOnlyStringWrapper(cell.getValue().getCategory()));
        TableColumn<MenuItem, String> priceColumn = new TableColumn<>("Price");
        priceColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
            "₹" + BigDecimal.valueOf(cell.getValue().getPricePaise(), 2).toPlainString()));
        TableColumn<MenuItem, String> availabilityColumn = new TableColumn<>("Status");
        availabilityColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
            cell.getValue().isAvailable() ? "Available" : "Unavailable"));
        itemTable.getColumns().setAll(nameColumn, categoryColumn, priceColumn, availabilityColumn);
        itemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        itemTable.getStyleClass().add("menu-table");
        itemTable.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldItem, newItem) -> selectItem(newItem));
    }

    private VBox createEditor() {
        Label editorTitle = new Label("Add or edit item");
        editorTitle.getStyleClass().add("editor-heading");

        nameField.setPromptText("e.g. Masala dosa");
        categoryBox.setItems(FXCollections.observableArrayList("Food", "Beverage"));
        categoryBox.setPromptText("Choose a category");
        categoryBox.setMaxWidth(Double.MAX_VALUE);
        priceField.setPromptText("e.g. 125.00");
        availableBox.setSelected(true);

        messageLabel.setWrapText(true);
        messageLabel.getStyleClass().add("menu-message");

        Button addButton = new Button("Add new item");
        addButton.getStyleClass().add("menu-primary-button");
        addButton.setMaxWidth(Double.MAX_VALUE);
        addButton.setOnAction(event -> addItem());

        Button saveButton = new Button("Save selected item");
        saveButton.getStyleClass().add("menu-primary-button");
        saveButton.setMaxWidth(Double.MAX_VALUE);
        saveButton.setOnAction(event -> saveItem());

        Button unavailableButton = new Button("Mark unavailable");
        unavailableButton.getStyleClass().add("menu-secondary-button");
        unavailableButton.setMaxWidth(Double.MAX_VALUE);
        unavailableButton.setOnAction(event -> markUnavailable());

        Button clearButton = new Button("Clear form");
        clearButton.getStyleClass().add("menu-secondary-button");
        clearButton.setMaxWidth(Double.MAX_VALUE);
        clearButton.setOnAction(event -> clearForm());

        VBox editor = new VBox(
            11,
            editorTitle,
            new Label("Name"), nameField,
            new Label("Category"), categoryBox,
            new Label("Price in rupees"), priceField,
            availableBox,
            addButton,
            saveButton,
            unavailableButton,
            clearButton,
            messageLabel
        );
        editor.setPadding(new Insets(22));
        editor.setPrefWidth(330);
        editor.setMinWidth(290);
        editor.getStyleClass().add("menu-editor");
        return editor;
    }

    private void loadItems() {
        try {
            itemTable.setItems(FXCollections.observableArrayList(menuService.getAllItems()));
            showMessage("Menu loaded.", false, false);
        } catch (SQLException exception) {
            showMessage("Could not load the menu. Please try again.", true, false);
        }
    }

    private void addItem() {
        try {
            menuService.addItem(nameField.getText(), categoryBox.getValue(), priceField.getText());
            loadItems();
            clearForm();
            showMessage("Menu item added.", false, true);
        } catch (IllegalArgumentException exception) {
            showMessage(exception.getMessage(), true, false);
        } catch (SQLException exception) {
            showMessage("Could not add this item. Please try again.", true, false);
        }
    }

    private void saveItem() {
        if (selectedItem == null) {
            showMessage("Select an item from the menu before saving changes.", true, false);
            return;
        }
        try {
            menuService.updateItem(
                selectedItem.getId(), nameField.getText(), categoryBox.getValue(),
                priceField.getText(), availableBox.isSelected()
            );
            int selectedId = selectedItem.getId();
            loadItems();
            itemTable.getItems().stream()
                .filter(item -> item.getId() == selectedId)
                .findFirst()
                .ifPresent(item -> itemTable.getSelectionModel().select(item));
            showMessage("Menu item updated.", false, true);
        } catch (IllegalArgumentException exception) {
            showMessage(exception.getMessage(), true, false);
        } catch (SQLException exception) {
            showMessage("Could not save these changes. Please try again.", true, false);
        }
    }

    private void markUnavailable() {
        if (selectedItem == null) {
            showMessage("Select an item before marking it unavailable.", true, false);
            return;
        }
        try {
            menuService.markUnavailable(selectedItem.getId());
            loadItems();
            showMessage("Menu item marked unavailable.", false, true);
        } catch (SQLException exception) {
            showMessage("Could not update this item. Please try again.", true, false);
        }
    }

    private void selectItem(MenuItem item) {
        selectedItem = item;
        if (item == null) {
            return;
        }
        nameField.setText(item.getName());
        categoryBox.setValue(item.getCategory());
        priceField.setText(BigDecimal.valueOf(item.getPricePaise(), 2).toPlainString());
        availableBox.setSelected(item.isAvailable());
        showMessage("Editing “" + item.getName() + "”.", false, false);
    }

    private void clearForm() {
        itemTable.getSelectionModel().clearSelection();
        selectedItem = null;
        nameField.clear();
        categoryBox.getSelectionModel().clearSelection();
        priceField.clear();
        availableBox.setSelected(true);
    }

    private void showMessage(String text, boolean error, boolean success) {
        messageLabel.setText(text);
        messageLabel.getStyleClass().removeAll("menu-error", "menu-success");
        if (error) {
            messageLabel.getStyleClass().add("menu-error");
        } else if (success) {
            messageLabel.getStyleClass().add("menu-success");
        }
    }
}
