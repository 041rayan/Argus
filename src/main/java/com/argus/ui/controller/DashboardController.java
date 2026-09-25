package com.argus.ui.controller;

import com.argus.core.event.EventBus;
import com.argus.core.event.ScanEvent;
import com.argus.core.model.Target;
import com.argus.core.pipeline.ScanRunner;
import com.argus.db.ApiKeysDAO;
import com.argus.db.Database;
import com.argus.db.TargetDAO;
import com.argus.ui.MainApp;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.util.StringConverter;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Controller for the dashboard view (MVC pattern): navigation, key status, scans. */
public final class DashboardController {

    @FXML
    private Label statusLabel;

    @FXML
    private Button logoutButton;

    @FXML
    private Label apiKeysLabel;

    @FXML
    private ComboBox<Target> scanTargetCombo;

    @FXML
    private Button scanButton;

    @FXML
    private Label scanStatusLabel;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "apikey-status");
        t.setDaemon(true);
        return t;
    });

    private final ExecutorService coordinator = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "scan-coordinator");
        t.setDaemon(true);
        return t;
    });

    private MainApp main;
    private ScanRunner runner;
    private int hostCount;

    @FXML
    private void initialize() {
        scanTargetCombo.setCellFactory(c -> new ListCell<>() {
            @Override
            protected void updateItem(Target item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label() + " (" + item.domain() + ")");
            }
        });
        scanTargetCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Target t) {
                return t == null ? "" : t.label() + " (" + t.domain() + ")";
            }

            @Override
            public Target fromString(String s) {
                return null;
            }
        });
    }

    /** Wired by MainApp after the FXML load — data arrives through setters. */
    public void setMain(MainApp main) {
        this.main = main;
        if (statusLabel != null) {
            statusLabel.setText("Ready — pick a target and scan.");
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
        worker.execute(() -> {
            try {
                List<Target> targets = new TargetDAO(Database.inUserHome()).list();
                Platform.runLater(() -> {
                    scanTargetCombo.getItems().setAll(targets);
                    if (targets.isEmpty()) {
                        scanTargetCombo.setPromptText("No targets — add one in Targets");
                    }
                });
            } catch (SQLException e) {
                Platform.runLater(() -> scanTargetCombo.setPromptText("Database failure."));
            }
        });
    }

    @FXML
    private void onScan() {
        if (runner != null) {
            runner.cancel();
            scanStatusLabel.setText("Cancelling…");
            return;
        }
        Target target = scanTargetCombo.getValue();
        if (target == null) {
            new Alert(Alert.AlertType.WARNING, "Pick a target first.").showAndWait();
            return;
        }
        hostCount = 0;
        EventBus events = new EventBus();
        events.subscribe(this::onScanEvent);
        ScanRunner newRunner = new ScanRunner(target, main.operatorId(), events);
        runner = newRunner;
        scanButton.setText("Cancel");
        scanStatusLabel.setText("Scanning " + target.domain() + "…");
        coordinator.execute(newRunner::run);
    }

    /** Event-bus thread — hop to FX (JAVAFX.md). */
    private void onScanEvent(ScanEvent e) {
        Platform.runLater(() -> {
            if (e instanceof ScanEvent.ScanStarted s) {
                scanStatusLabel.setText("Scanning " + s.target() + "…");
            } else if (e instanceof ScanEvent.HostFound) {
                hostCount++;
                scanStatusLabel.setText(hostCount + " host(s) resolved");
            } else if (e instanceof ScanEvent.StageProgress p) {
                scanStatusLabel.setText(p.stage() + ": " + p.done() + " subdomains");
            } else if (e instanceof ScanEvent.ProviderDegraded d) {
                scanStatusLabel.setText(d.provider() + " degraded (" + d.status() + ") — continuing");
            } else if (e instanceof ScanEvent.ScanFinished f) {
                scanStatusLabel.setText(f.status() + " — " + f.message());
                scanButton.setText("Scan");
                if (runner != null) {
                    runner.close();
                    runner = null;
                }
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
