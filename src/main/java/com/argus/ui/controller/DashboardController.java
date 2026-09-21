package com.argus.ui.controller;

import com.argus.ui.MainApp;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import java.io.IOException;

/** Controller for the dashboard view (MVC pattern). */
public final class DashboardController {

    @FXML
    private Label statusLabel;

    @FXML
    private Button logoutButton;

    private MainApp main;

    /** Wired by MainApp after the FXML load — data arrives through setters. */
    public void setMain(MainApp main) {
        this.main = main;
        if (statusLabel != null) {
            statusLabel.setText("Argus skeleton ready — no scans yet.");
        }
    }

    @FXML
    private void onShowTargets() {
        try {
            main.showTargets();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Cannot open targets view.").showAndWait();
        }
    }

    @FXML
    private void onLogout() {
        main.onLogout();
    }
}
