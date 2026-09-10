package com.argus.ui.controller;

import com.argus.ui.MainApp;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controller for the placeholder dashboard view (MVC pattern).
 * FXMLLoader creates it; MainApp passes itself through {@link #setMain}.
 */
public final class DashboardController {

    @FXML
    private Label statusLabel;

    private MainApp main;

    /** Wired by MainApp after the FXML load — data arrives through setters. */
    public void setMain(MainApp main) {
        this.main = main;
        if (statusLabel != null) {
            statusLabel.setText("Argus skeleton ready — no scans yet.");
        }
    }

    @FXML
    private void initialize() {
        // Only cell value factories go here; no I/O, no business logic.
    }
}
