package com.argus.ui.controller;

import com.argus.core.export.ExportService;
import com.argus.core.export.ExportService.EntryPoint;
import com.argus.core.export.ExportService.FindingOut;
import com.argus.core.model.Finding;
import com.argus.core.model.PortResult;
import com.argus.core.model.ScanSummary;
import com.argus.db.Database;
import com.argus.db.FindingDAO;
import com.argus.db.PortDAO;
import com.argus.db.ScanDAO;
import com.argus.ui.MainApp;
import com.argus.ui.row.EntryPointsRow;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.util.Callback;
import javafx.util.StringConverter;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry Points view: pick a scan, ports ranked by priority score (CORE.md).
 * Reads off the FX thread; the controller only swaps lists (JAVAFX.md).
 */
public final class EntryPointsController implements ShellContent {

    @FXML
    private ComboBox<ScanSummary> scanCombo;
    @FXML
    private TableView<EntryPointsRow> entryTable;
    @FXML
    private TableColumn<EntryPointsRow, String> severityColumn;
    @FXML
    private TableColumn<EntryPointsRow, String> kevColumn;
    @FXML
    private Label inspectorTitle;
    @FXML
    private Label serviceValue;
    @FXML
    private Label severityValue;
    @FXML
    private Label scoreValue;
    @FXML
    private ListView<String> findingList;

    private final ObservableList<ScanSummary> scans = FXCollections.observableArrayList();
    private final ObservableList<EntryPointsRow> rows = FXCollections.observableArrayList();
    private final ObservableList<String> findingLines = FXCollections.observableArrayList();
    /** Grouped once per scan, off the FX thread: a row click is a map lookup. */
    private Map<String, List<FindingOut>> byHostPort = Map.of();
    /* Package-visible for the shell swap-out test (test-seam precedent). */
    final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "entrypoints-worker");
        t.setDaemon(true);
        return t;
    });

    private PortDAO portDao;
    private FindingDAO findingDao;

    @FXML
    private void initialize() {
        entryTable.setItems(rows);
        scanCombo.setItems(scans);
        findingList.setItems(findingLines);
        kevColumn.getStyleClass().add("ag-kev");
        kevColumn.setCellFactory(kevCells());
        severityColumn.setCellFactory(severityCells());
        entryTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, row) -> showDetail(row));
        scanCombo.setCellFactory(c -> new ListCell<>() {
            @Override
            protected void updateItem(ScanSummary item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : ScanLabel.of(item));
            }
        });
        scanCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(ScanSummary s) {
                return s == null ? "" : ScanLabel.of(s);
            }

            @Override
            public ScanSummary fromString(String s) {
                return null;
            }
        });
        scanCombo.valueProperty().addListener((obs, old, s) -> {
            if (s != null) {
                loadEntries(s.id());
            }
        });
    }

    @Override
    public void onHidden() {
        worker.shutdownNow();
    }

    /** Uniform pane seam: this pane reads no session state, so it keeps no field. */
    public void setMain(MainApp main) {
        Database db = Database.inUserHome();
        this.portDao = new PortDAO(db);
        this.findingDao = new FindingDAO(db);
        worker.execute(() -> {
            try {
                List<ScanSummary> fresh = new ScanDAO(db).list();
                Platform.runLater(() -> {
                    scans.setAll(fresh);
                    if (!fresh.isEmpty()) {
                        scanCombo.setValue(fresh.get(0));
                    }
                });
            } catch (SQLException e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Database failure.").showAndWait());
            }
        });
    }

    /**
     * The ranking needs both lists anyway, so the findings are grouped by
     * host:port in the same off-FX hop and the selection is a map lookup
     * (THREAD.md: nothing on the FX thread re-parses detail_json).
     */
    private void loadEntries(long scanId) {
        worker.execute(() -> {
            try {
                List<PortResult> ports = portDao.listByScan(scanId);
                List<Finding> loaded = findingDao.listByScan(scanId);
                List<EntryPointsRow> fresh = ExportService.entryPoints(ports, loaded)
                        .stream().map(EntryPointsRow::new).toList();
                Map<String, List<FindingOut>> grouped = groupByHostPort(loaded);
                Platform.runLater(() -> {
                    byHostPort = grouped;
                    rows.setAll(fresh);
                    showDetail(null);
                });
            } catch (SQLException e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Database failure.").showAndWait());
            }
        });
    }

    /**
     * The scan's findings keyed by "host:port", reusing the shared contract.
     * A finding with an unreadable detail has no key and is dropped, which
     * costs the inspector one row, never the view.
     */
    static Map<String, List<FindingOut>> groupByHostPort(List<Finding> findings) {
        Map<String, List<FindingOut>> grouped = new LinkedHashMap<>();
        for (Finding f : findings) {
            String hostPort = ExportService.hostPortOf(f);
            if (hostPort != null) {
                grouped.computeIfAbsent(hostPort, k -> new ArrayList<>())
                        .add(ExportService.findingOut(f));
            }
        }
        return grouped;
    }

    private void showDetail(EntryPointsRow row) {
        if (row == null) {
            inspectorTitle.setText("Select an entry point");
            serviceValue.setText("-");
            severityValue.setText("-");
            scoreValue.setText("-");
            findingLines.clear();
            return;
        }
        EntryPoint entry = row.entry();
        inspectorTitle.setText(entry.hostPort());
        serviceValue.setText(entry.service());
        severityValue.setText(entry.severity());
        scoreValue.setText(String.valueOf(entry.score()));
        findingLines.setAll(byHostPort.getOrDefault(entry.hostPort(), List.of()).stream()
                .map(EntryPointsController::line)
                .toList());
    }

    /**
     * A table cell is handed the column's own value, never the row, so the
     * factories are typed to the String the PropertyValueFactory produces.
     * The KEV column fits one identifier: the cell drops the confidence word
     * (Severity already carries it) and counts any others, and the tooltip
     * keeps the full list.
     */
    static Callback<TableColumn<EntryPointsRow, String>, TableCell<EntryPointsRow, String>> kevCells() {
        return column -> new TableCell<>() {
            @Override
            protected void updateItem(String kev, boolean empty) {
                super.updateItem(kev, empty);
                String full = empty ? null : kev;
                setText(kevCellText(full));
                setTooltip(full == null || full.isBlank() || "-".equals(full)
                        ? null : new Tooltip(full));
            }
        };
    }

    /**
     * Display form of a KEV cell: the first identifier on its own, plus a
     * count of any others. Pure, so the transformation is unit tested.
     */
    static String kevCellText(String kev) {
        if (kev == null || kev.isBlank()) {
            return "";
        }
        String[] parts = kev.split(";");
        String first = parts[0].strip().replaceFirst("\\s+(CONFIRMED|CANDIDATE)$", "");
        return parts.length > 1 ? first + " +" + (parts.length - 1) : first;
    }

    /** Severity is coloured worst first; a placeholder "-" stays uncoloured. */
    static Callback<TableColumn<EntryPointsRow, String>, TableCell<EntryPointsRow, String>> severityCells() {
        return column -> new TableCell<>() {
            @Override
            protected void updateItem(String severity, boolean empty) {
                super.updateItem(severity, empty);
                String text = empty ? null : severity;
                setText(text);
                // one prefix, no literal list: a new status colour needs no edit here
                getStyleClass().removeIf(c -> c.startsWith("ag-status-"));
                if (text != null && !"-".equals(text)) {
                    getStyleClass().add(severityClass(text));
                }
            }
        };
    }

    static String line(FindingOut out) {
        return orDash(out.severity()) + "  " + orDash(out.type()) + "  " + orDash(out.detail());
    }

    /** A null or blank field renders as a dash, never the word "null". */
    private static String orDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    /** Severity to status colour, worst first (the sheet owns the palette). */
    static String severityClass(String severity) {
        return switch (severity) {
            case "CRITICAL", "HIGH" -> "ag-status-danger";
            case "MEDIUM" -> "ag-status-warn";
            default -> "ag-status-info";
        };
    }
}
