package com.argus.ui.controller;

import com.argus.core.model.ScanSummary;
import com.argus.db.Database;
import com.argus.db.HostDAO;
import com.argus.db.ScanDAO;
import com.argus.ui.MainApp;
import com.argus.ui.row.HostRow;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Results view: pick a scan (optionally filtered by date), hosts table with search. */
public final class ResultsController {

    @FXML
    private ComboBox<ScanSummary> scanCombo;
    @FXML
    private DatePicker dateFilter;
    @FXML
    private TextField searchField;
    @FXML
    private TableView<HostRow> hostsTable;
    @FXML
    private Button deleteButton;

    private final ObservableList<ScanSummary> scans = FXCollections.observableArrayList();
    private final ObservableList<HostRow> rows = FXCollections.observableArrayList();
    private final FilteredList<HostRow> filtered = new FilteredList<>(rows);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "results-worker");
        t.setDaemon(true);
        return t;
    });

    private MainApp main;
    private ScanDAO scanDao;
    private HostDAO hostDao;
    private List<ScanSummary> allScans = List.of();

    @FXML
    private void initialize() {
        hostsTable.setItems(filtered);
        scanCombo.setItems(scans);
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
                loadHosts(s.id());
            }
        });
        dateFilter.valueProperty().addListener((obs, old, date) -> filterScans(date));
        // no selection or a live run: deleting either is invalid
        deleteButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> {
                    ScanSummary s = scanCombo.getValue();
                    return s == null || s.status() == ScanSummary.Status.RUNNING;
                }, scanCombo.valueProperty()));
        searchField.textProperty().addListener((obs, old, query) ->
                filtered.setPredicate(row -> matches(row, query)));
    }

    /** Wired by MainApp after the FXML load; first loads happen here. */
    public void setMain(MainApp main) {
        this.main = main;
        this.scanDao = new ScanDAO(Database.inUserHome());
        this.hostDao = new HostDAO(Database.inUserHome());
        executor.execute(() -> {
            try {
                List<ScanSummary> fresh = scanDao.list();
                Platform.runLater(() -> {
                    allScans = fresh;
                    filterScans(dateFilter.getValue());
                });
            } catch (SQLException e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Database failure.").showAndWait());
            }
        });
    }

    /** DatePicker filter (JAVAFX.md history rule): keep scans started that day. */
    private void filterScans(LocalDate date) {
        List<ScanSummary> matching = date == null ? allScans : allScans.stream()
                .filter(s -> s.startedAt().atZone(ZoneId.systemDefault()).toLocalDate().equals(date))
                .toList();
        ScanSummary previous = scanCombo.getValue();
        scans.setAll(matching);
        if (matching.contains(previous)) {
            scanCombo.setValue(previous); // keep selection, no reload
        } else if (!matching.isEmpty()) {
            scanCombo.setValue(matching.get(0));
        } else {
            scanCombo.setValue(null);
            rows.clear();
        }
    }

    private void loadHosts(long scanId) {
        executor.execute(() -> {
            try {
                List<HostRow> fresh = hostDao.listByScan(scanId).stream().map(HostRow::new).toList();
                Platform.runLater(() -> rows.setAll(fresh));
            } catch (SQLException e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Database failure.").showAndWait());
            }
        });
    }

    @FXML
    private void onEntryPoints() {
        try {
            main.showEntryPoints();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Cannot open entry points view.").showAndWait();
        }
    }

    @FXML
    private void onBack() {
        try {
            main.showDashboard();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Cannot open dashboard view.").showAndWait();
        }
    }

    /**
     * Cascade delete (DB.md), off the FX thread. A RUNNING row is off-limits:
     * finishScan would re-insert it when the run ends — a deleted scan that
     * comes back as a zombie. The button is disabled for it anyway.
     */
    @FXML
    private void onDelete() {
        ScanSummary selected = scanCombo.getValue();
        if (selected == null || selected.status() == ScanSummary.Status.RUNNING) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete scan #" + selected.id() + " (" + selected.target() + ")? "
                        + "Hosts, ports and findings go with it.",
                ButtonType.OK, ButtonType.CANCEL);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        executor.execute(() -> {
            try {
                scanDao.deleteScan(selected.id());
                List<ScanSummary> fresh = scanDao.list();
                Platform.runLater(() -> {
                    allScans = fresh;
                    filterScans(dateFilter.getValue());
                });
            } catch (SQLException e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Database failure.").showAndWait());
            }
        });
    }

    private static boolean matches(HostRow row, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String q = query.toLowerCase();
        return row.getSubdomain().toLowerCase().contains(q) || row.getIp().toLowerCase().contains(q);
    }
}
