package com.argus.ui;

import atlantafx.base.theme.PrimerDark;
import com.argus.ui.controller.DashboardController;
import com.argus.ui.controller.TargetsController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

/**
 * Argus HQ: owns the primary Stage and shared services, and switches
 * scenes. One Stage, many Scenes. Controllers are created by FXMLLoader
 * and receive this class through setMain — never by us.
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

    public void showDashboard() throws IOException {
        FXMLLoader l = loader("dashboard");
        Scene scene = new Scene(l.load(), 900, 640);
        applyCss(scene);
        DashboardController controller = l.getController();
        controller.setMain(this);
        primaryStage.setScene(scene);
    }

    public void showTargets() throws IOException {
        FXMLLoader l = loader("targets");
        Scene scene = new Scene(l.load(), 900, 640);
        applyCss(scene);
        TargetsController controller = l.getController();
        controller.setMain(this);
        primaryStage.setScene(scene);
    }

    private FXMLLoader loader(String name) {
        return new FXMLLoader(MainApp.class.getResource("/com/argus/ui/view/" + name + ".fxml"));
    }

    private void applyCss(Scene scene) {
        URL css = MainApp.class.getResource("/com/argus/ui/view/application.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
