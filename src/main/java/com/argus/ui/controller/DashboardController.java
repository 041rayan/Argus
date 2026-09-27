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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Controller for the dashboard view (MVC pattern): navigation, key status, scans. */
public final class DashboardController implements ShellContent {

    private static final Logger LOG = LoggerFactory.getLogger(DashboardController.class);

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

    /* Package-visible for the shell swap-out test (test-seam precedent). */
    final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "apikey-status");
        t.setDaemon(true);
        return t;
    });

    /* Package-visible for the shell swap-out test (test-seam precedent). */
    final ExecutorService coordinator = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "scan-coordinator");
        t.setDaemon(true);
        return t;
    });

    private MainApp main;
    private volatile ScanRunner runner;
    /** True while the coordinator thread is still constructing the runner. */
    volatile boolean starting;
    /** Set by onHidden: the pane is gone, release the coordinator when the run ends. */
    private volatile boolean hidden;
    private int hostCount;
    private int portCount;

    private String countsText() {
        return hostCount + " host(s) resolved · " + portCount + " open port(s)";
    }

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
                String line = "VirusTotal: "
                        + (providers.contains("VirusTotal") ? "configured" : "not configured");
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
        if (starting) {
            return; // runner not constructed yet — a second click has nothing to cancel
        }
        Target target = scanTargetCombo.getValue();
        if (target == null) {
            new Alert(Alert.AlertType.WARNING, "Pick a target first.").showAndWait();
            return;
        }
        hostCount = 0;
        portCount = 0;
        EventBus events = new EventBus();
        events.subscribe(this::onScanEvent);
        starting = true;
        scanButton.setText("Cancel");
        scanStatusLabel.setText("Scanning " + target.domain() + "…");
        coordinator.execute(() -> {
            try {
                // construction may refresh the KEV catalog (network) — FX thread stays free
                ScanRunner newRunner = new ScanRunner(target, main.operatorId(), events,
                        vtKey());
                runner = newRunner;
                newRunner.run();
            } catch (RuntimeException e) {
                events.publish(new ScanEvent.ScanFinished("FAILED",
                        e.getMessage() == null ? "scan start failed" : e.getMessage()));
            } finally {
                starting = false;
            }
        });
    }

    /**
     * Shell swap-out: the status thread always stops. The coordinator stops
     * only when no scan owns it — a running scan keeps its thread until
     * the ScanFinished handler below releases it.
     */
    @Override
    public void onHidden() {
        hidden = true;
        worker.shutdownNow();
        if (!isScanning()) {
            coordinator.shutdownNow();
        }
    }

    /**
     * Decrypted VirusTotal key for the scan, or null when there is none.
     * Runs on the coordinator thread, so the vault read stays off FX.
     * A lookup failure costs VT enrichment, never the scan (API.md no-key law).
     */
    private byte[] vtKey() {
        byte[] vault = main.vaultKey();
        if (vault == null) {
            return null;
        }
        try {
            return new ApiKeysDAO(Database.inUserHome())
                    .find(main.operatorId(), "VirusTotal", vault).orElse(null);
        } catch (SQLException e) {
            LOG.warn("vt key lookup failed, scanning without reputation: {}", e.getMessage());
            return null;
        }
    }

    /** True while a scan run owns the coordinator thread. */
    boolean isScanning() {
        return starting || runner != null;
    }

    /** Event-bus thread — hop to FX (JAVAFX.md). */
    private void onScanEvent(ScanEvent e) {
        Platform.runLater(() -> {
            if (e instanceof ScanEvent.ScanStarted s) {
                scanStatusLabel.setText("Scanning " + s.target() + "…");
            } else if (e instanceof ScanEvent.HostFound) {
                hostCount++;
                scanStatusLabel.setText(countsText());
            } else if (e instanceof ScanEvent.PortFound) {
                portCount++;
                scanStatusLabel.setText(countsText());
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
                if (hidden) {
                    coordinator.shutdown();
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
    private void onShowResults() {
        try {
            main.showResults();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Cannot open results view.").showAndWait();
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
