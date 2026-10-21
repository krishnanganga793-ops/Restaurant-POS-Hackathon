package com.restaurantpos.ui;

import com.restaurantpos.model.User;
import com.restaurantpos.service.LoginService;
import java.net.URL;
import java.sql.SQLException;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

/** Login and cashier registration shown at application startup. */
public class LoginView {
    private final Stage stage;
    private final LoginService loginService = new LoginService();

    private TextField usernameField;
    private PasswordField passwordField;
    private PasswordField confirmPasswordField;
    private Label confirmPasswordLabel;
    private Label messageLabel;
    private Button submitButton;
    private boolean registering;

    public LoginView(Stage stage) {
        this.stage = stage;
    }

    public Scene createScene() {
        StackPane photo = createRestaurantPhoto();
        VBox form = createAuthenticationForm();

        photo.setMinWidth(450);
        photo.setPrefWidth(620);
        HBox.setHgrow(photo, Priority.ALWAYS);
        form.setMinWidth(400);
        form.setPrefWidth(480);
        HBox.setHgrow(form, Priority.SOMETIMES);

        HBox content = new HBox(24, photo, form);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(24));
        content.getStyleClass().add("login-root");

        Scene scene = new Scene(content, 1160, 700);
        stage.setMinWidth(1020);
        stage.setMinHeight(650);
        stage.setWidth(1160);
        stage.setHeight(700);
        scene.getStylesheets().add(
            LoginView.class.getResource("/login.css").toExternalForm());
        return scene;
    }

    private StackPane createRestaurantPhoto() {
        URL imageResource = LoginView.class.getResource("/restaurant-food.jpg");
        if (imageResource == null) {
            throw new IllegalStateException(
                "Could not find /restaurant-food.jpg in src/main/resources.");
        }
        Image ambienceImage = new Image(imageResource.toExternalForm(), false);
        if (ambienceImage.isError()) {
            throw new IllegalStateException(
                "Could not load /restaurant-food.jpg.", ambienceImage.getException());
        }

        ImageView imageView = new ImageView(ambienceImage);
        imageView.setPreserveRatio(false);
        imageView.setEffect(new ColorAdjust(0, 0.04, 0.12, 0));
        StackPane photo = new StackPane(imageView);
        Rectangle photoClip = new Rectangle();
        photoClip.widthProperty().bind(photo.widthProperty());
        photoClip.heightProperty().bind(photo.heightProperty());
        photoClip.setArcWidth(22);
        photoClip.setArcHeight(22);
        photo.setClip(photoClip);

        Runnable updateCrop = () -> updateImageCrop(
            imageView, ambienceImage, photo.getWidth(), photo.getHeight());
        photo.widthProperty().addListener((observable, oldWidth, newWidth) -> updateCrop.run());
        photo.heightProperty().addListener((observable, oldHeight, newHeight) -> updateCrop.run());
        photo.getStyleClass().add("photo-panel");
        return photo;
    }

    private void updateImageCrop(ImageView imageView, Image image, double panelWidth,
                                 double panelHeight) {
        if (panelWidth <= 0 || panelHeight <= 0) {
            return;
        }
        double imageWidth = image.getWidth();
        double imageHeight = image.getHeight();
        double panelRatio = panelWidth / panelHeight;
        double imageRatio = imageWidth / imageHeight;
        double cropWidth = imageWidth;
        double cropHeight = imageHeight;
        double cropX = 0;
        double cropY = 0;
        if (imageRatio > panelRatio) {
            cropWidth = imageHeight * panelRatio;
            cropX = (imageWidth - cropWidth) / 2;
        } else {
            cropHeight = imageWidth / panelRatio;
            cropY = (imageHeight - cropHeight) / 2;
        }
        imageView.setViewport(new Rectangle2D(cropX, cropY, cropWidth, cropHeight));
        imageView.setFitWidth(panelWidth);
        imageView.setFitHeight(panelHeight);
    }

    private VBox createAuthenticationForm() {
        Label title = new Label("RESTAURANT POS");
        title.getStyleClass().add("brand-title");
        Label subtitle = new Label("Welcome. Sign in or create a staff account.");
        subtitle.getStyleClass().add("form-subtitle");

        ToggleGroup modeGroup = new ToggleGroup();
        ToggleButton loginTab = new ToggleButton("Login");
        ToggleButton registerTab = new ToggleButton("Register");
        loginTab.setToggleGroup(modeGroup);
        registerTab.setToggleGroup(modeGroup);
        loginTab.setSelected(true);
        loginTab.getStyleClass().add("auth-mode-button");
        registerTab.getStyleClass().add("auth-mode-button");
        HBox modes = new HBox(8, loginTab, registerTab);
        modes.getStyleClass().add("auth-mode-row");
        loginTab.setMaxWidth(Double.MAX_VALUE);
        registerTab.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(loginTab, Priority.ALWAYS);
        HBox.setHgrow(registerTab, Priority.ALWAYS);

        Label usernameLabel = fieldLabel("Username");
        usernameField = new TextField();
        usernameField.setPromptText("Enter your username");
        usernameField.getStyleClass().add("login-input");
        Label passwordLabel = fieldLabel("Password");
        passwordField = new PasswordField();
        passwordField.setPromptText("Enter your password");
        passwordField.getStyleClass().add("login-input");
        confirmPasswordLabel = fieldLabel("Confirm password");
        confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Re-enter your password");
        confirmPasswordField.getStyleClass().add("login-input");
        confirmPasswordLabel.setManaged(false);
        confirmPasswordLabel.setVisible(false);
        confirmPasswordField.setManaged(false);
        confirmPasswordField.setVisible(false);

        messageLabel = new Label(" ");
        messageLabel.setWrapText(true);
        messageLabel.getStyleClass().add("message-label");
        submitButton = new Button("Login");
        submitButton.setMaxWidth(Double.MAX_VALUE);
        submitButton.getStyleClass().add("login-button");
        submitButton.setOnAction(event -> submit());
        passwordField.setOnAction(event -> submit());
        confirmPasswordField.setOnAction(event -> submit());
        loginTab.setOnAction(event -> setRegisterMode(false));
        registerTab.setOnAction(event -> setRegisterMode(true));

        VBox form = new VBox(15, title, subtitle, modes,
            usernameLabel, usernameField, passwordLabel, passwordField,
            confirmPasswordLabel, confirmPasswordField, messageLabel, submitButton);
        form.setPadding(new Insets(42));
        form.setMaxWidth(520);
        form.getStyleClass().add("login-card");
        return form;
    }

    private Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    private void setRegisterMode(boolean register) {
        registering = register;
        confirmPasswordLabel.setVisible(register);
        confirmPasswordLabel.setManaged(register);
        confirmPasswordField.setVisible(register);
        confirmPasswordField.setManaged(register);
        submitButton.setText(register ? "Create cashier account" : "Login");
        passwordField.setPromptText(register ? "Create a password" : "Enter your password");
        clearMessage();
    }

    private void submit() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText();
        if (username.isBlank() || password == null || password.isBlank()) {
            showMessage("Please enter both your username and password.", false);
            return;
        }
        if (registering) {
            String confirmation = confirmPasswordField.getText();
            if (confirmation == null || confirmation.isBlank()) {
                showMessage("Please confirm your password.", false);
                return;
            }
            if (!password.equals(confirmation)) {
                showMessage("The passwords do not match.", false);
                return;
            }
            register(username, password);
        } else {
            login(username, password);
        }
    }

    private void login(String username, String password) {
        try {
            User user = loginService.login(username, password);
            if (user == null) {
                showMessage("Those credentials didn't match. Please try again.", false);
                return;
            }
            openDashboard(user);
        } catch (SQLException exception) {
            showMessage("Unable to verify your login right now. Please try again.", false);
        }
    }

    private void register(String username, String password) {
        try {
            User user = loginService.registerCashier(username, password);
            showMessage("Staff account created. Opening the dashboard…", true);
            openDashboard(user);
        } catch (IllegalArgumentException exception) {
            showMessage(exception.getMessage(), false);
        } catch (SQLException exception) {
            showMessage("Unable to create the account right now. Please try again.", false);
        }
    }

    private void openDashboard(User user) {
        stage.setScene(new SalesDashboardView(stage, user).createScene());
    }

    private void showMessage(String text, boolean successful) {
        messageLabel.setText(text);
        messageLabel.getStyleClass().removeAll("error-message", "success-message");
        messageLabel.getStyleClass().add(successful ? "success-message" : "error-message");
    }

    private void clearMessage() {
        messageLabel.setText(" ");
        messageLabel.getStyleClass().removeAll("error-message", "success-message");
    }
}
