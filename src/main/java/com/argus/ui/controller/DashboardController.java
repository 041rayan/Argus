package com.argus.ui.controller;

import com.argus.core.event.EventBus;
import com.argus.core.event.ScanEvent;
import com.argus.core.model.Finding;
import com.argus.core.model.Host;
import com.argus.core.model.PortResult;
import com.argus.core.model.ScanSummary;
import com.argus.core.model.Target;
import com.argus.core.pipeline.ScanRunner;
import com.argus.db.ApiKeysDAO;
import com.argus.db.Database;
import com.argus.db.FindingDAO;
import com.argus.db.HostDAO;
import com.argus.db.PortDAO;
import com.argus.db.ScanDAO;
import com.argus.db.TargetDAO;
import com.argus.ui.MainApp;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Controller for the dashboard view (MVC pattern): navigation, key status, scans. */
public final class DashboardController implements ShellContent {

    private static final Logger LOG = LoggerFactory.getLogger(DashboardController.class);

    @FXML
    private ComboBox<Target> scanTargetCombo;

    @FXML
    private Button scanButton;

    @FXML
    private ProgressBar scanProgress;

    @FXML
    private Label scanStatusLabel;

    @FXML
    private Label statScans;
    @FXML
    private Label statHosts;
    @FXML
    private Label statPorts;
    @FXML
    private Label statKev;
    @FXML
    private Label statVt;

    @FXML
    private BarChart<String, Number> severityChart;
    @FXML
    private BarChart<String, Number> portsChart;

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
        scanStatusLabel.setText("Ready — pick a target and scan.");
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
        refreshStats();
    }

    /** Latest COMPLETED scan's cards + charts, off the FX thread. */
    void refreshStats() {
        worker.execute(() -> {
            try {
                Database db = Database.inUserHome();
                List<ScanSummary> scans = new ScanDAO(db).list();
                ScanSummary latest = latestCompleted(scans);
                if (latest == null || latest.id() == null) {
                    Platform.runLater(this::showEmptyStats);
                    return;
                }
                DashboardStats stats = summarize(
                        new HostDAO(db).listByScan(latest.id()),
                        new PortDAO(db).listByScan(latest.id()),
                        new FindingDAO(db).listByScan(latest.id()));
                Platform.runLater(() -> showStats(scans.size(), stats));
            } catch (SQLException e) {
                LOG.warn("dashboard stats failed: {}", e.getMessage());
                Platform.runLater(this::showEmptyStats);
            }
        });
    }

    /** Pure summary of one scan for the stat cards and charts (headless-testable). */
    record DashboardStats(int aliveHosts, int openPorts, int kev, int vt,
                          Map<String, Integer> bySeverity,
                          Map<Integer, Set<String>> topPorts) {
    }

    static DashboardStats summarize(List<Host> hosts, List<PortResult> ports,
                                    List<Finding> findings) {
        int alive = 0;
        for (Host h : hosts) {
            if (h.alive()) {
                alive++;
            }
        }
        int kev = 0;
        int vt = 0;
        Map<String, Integer> bySev = new TreeMap<>();
        for (Finding f : findings) {
            if ("KEV_MATCH".equals(f.type())) {
                kev++;
            } else if ("VT_FLAGGED".equals(f.type())) {
                vt++;
            }
            bySev.merge(f.severity(), 1, Integer::sum);
        }
        Map<Integer, Set<String>> byPort = new HashMap<>();
        for (PortResult p : ports) {
            if (p.open()) {
                byPort.computeIfAbsent(p.port(), k -> new HashSet<>()).add(p.host());
            }
        }
        return new DashboardStats(alive, byPort.values().stream()
                .mapToInt(Set::size).sum(), kev, vt, bySev, byPort);
    }

    private void feedCharts(DashboardStats stats) {
        XYChart.Series<String, Number> sev = new XYChart.Series<>();
        for (var e : stats.bySeverity().entrySet()) {
            sev.getData().add(new XYChart.Data<>(e.getKey(), e.getValue()));
        }
        severityChart.setData(FXCollections.observableArrayList(sev));

        XYChart.Series<String, Number> ports = new XYChart.Series<>();
        stats.topPorts().entrySet().stream()
                .sorted(Map.Entry.<Integer, Set<String>>comparingByValue(
                        Comparator.comparingInt(Set::size)).reversed())
                .limit(8)
                .forEach(e -> ports.getData().add(new XYChart.Data<>(
                        String.valueOf(e.getKey()), e.getValue().size())));
        portsChart.setData(FXCollections.observableArrayList(ports));
    }

    private static ScanSummary latestCompleted(List<ScanSummary> scans) {
        for (ScanSummary s : scans) {
            if (s.status() == ScanSummary.Status.COMPLETED) {
                return s;
            }
        }
        return null;
    }

    private void showEmptyStats() {
        statScans.setText("0");
        statHosts.setText("0");
        statPorts.setText("0");
        statKev.setText("0");
        statVt.setText("0");
        scanStatusLabel.setText("No scans yet — pick a target and scan.");
    }

    private void showStats(int scanCount, DashboardStats stats) {
        statScans.setText(String.valueOf(scanCount));
        statHosts.setText(String.valueOf(stats.aliveHosts()));
        statPorts.setText(String.valueOf(stats.openPorts()));
        statKev.setText(String.valueOf(stats.kev()));
        statVt.setText(String.valueOf(stats.vt()));
        feedCharts(stats);
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
        scanProgress.setProgress(-1);
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
                scanProgress.setProgress(1);
                if (runner != null) {
                    runner.close();
                    runner = null;
                }
                if (hidden) {
                    coordinator.shutdown();
                }
                refreshStats();
            }
        });
    }
}
