package com.argus.ui;

import atlantafx.base.theme.PrimerDark;
import com.argus.ui.controller.DashboardController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

/**
 * Argus HQ: owns the primary Stage and shared services,
 * and switches scenes. One Stage, many Scenes. Controllers are created
 * by FXMLLoader and receive this class through setMain — never by us.
 */
public final class MainApp extends Application {

    private Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;
        stage.setTitle("Argus");
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        showDashboard();
        stage.show();
    }

    /** Placeholder scene switch — login, targets, export and lock join later. */
    private void showDashboard() throws IOException {
        URL fxml = MainApp.class.getResource("/com/argus/ui/view/dashboard.fxml");
        FXMLLoader loader = new FXMLLoader(fxml);
        Scene scene = new Scene(loader.load(), 900, 640);
        DashboardController controller = loader.getController();
        controller.setMain(this);
        URL css = MainApp.class.getResource("/com/argus/ui/view/application.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        primaryStage.setScene(scene);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
