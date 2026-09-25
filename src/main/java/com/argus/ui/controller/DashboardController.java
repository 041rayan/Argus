package com.argus.ui.controller;

import com.argus.db.ApiKeysDAO;
import com.argus.db.Database;
import com.argus.ui.MainApp;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Controller for the dashboard view (MVC pattern). */
public final class DashboardController {

    @FXML
    private Label statusLabel;

    @FXML
    private Button logoutButton;

    @FXML
    private Label apiKeysLabel;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "apikey-status");
        t.setDaemon(true);
        return t;
    });

    private MainApp main;

    /** Wired by MainApp after the FXML load — data arrives through setters. */
    public void setMain(MainApp main) {
        this.main = main;
        if (statusLabel != null) {
            statusLabel.setText("Argus skeleton ready — no scans yet.");
        }
        worker.execute(() -> {
            try {
                Set<String> providers =
                        new ApiKeysDAO(Database.inUserHome()).configuredProviders(main.operatorId());
                String line = "VirusTotal: " + (providers.contains("VirusTotal") ? "configured" : "not configured")
                        + " · Shodan: " + (providers.contains("Shodan") ? "configured" : "not configured");
                Platform.runLater(() -> apiKeysLabel.setText(line));
            } catch (SQLException e) {
                Platform.runLater(() -> apiKeysLabel.setText("Key status unavailable."));
            }
        });
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
    private void onApiKeys() {
        try {
            main.showAddApiKey();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Cannot open API keys dialog.").showAndWait();
        }
    }

    @FXML
    private void onExport() {
        try {
            main.showExport();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Cannot open export view.").showAndWait();
        }
    }

    @FXML
    private void onLogout() {
        main.onLogout();
    }
}
