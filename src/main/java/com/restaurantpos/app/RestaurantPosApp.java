package com.restaurantpos.app;

import com.restaurantpos.database.Database;
import com.restaurantpos.service.LoginService;
import com.restaurantpos.ui.LoginView;
import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import java.sql.SQLException;

public class RestaurantPosApp extends Application {

    @Override
    public void start(Stage stage) {
        try {
            Database.initialize();
            new LoginService().seedDemoAccounts();
        } catch (SQLException exception) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Database Error");
            alert.setHeaderText("Restaurant POS could not start.");
            alert.setContentText(exception.getMessage());
            alert.showAndWait();
            return;
        }

        stage.setTitle("Restaurant POS");
        stage.setScene(new LoginView(stage).createScene());
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
