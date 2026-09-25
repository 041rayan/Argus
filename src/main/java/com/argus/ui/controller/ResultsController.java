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
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Results view: pick a scan (optionally filtered by date), hosts table with search. */
public final class ResultsController {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    @FXML
    private ComboBox<ScanSummary> scanCombo;
    @FXML
    private DatePicker dateFilter;
    @FXML
    private TextField searchField;
    @FXML
    private TableView<HostRow> hostsTable;

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
                setText(empty || item == null ? null : label(item));
            }
        });
        scanCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(ScanSummary s) {
                return s == null ? "" : label(s);
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
    private void onBack() {
        try {
            main.showDashboard();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Cannot open dashboard view.").showAndWait();
        }
    }

    private static String label(ScanSummary s) {
        return s.target() + " · " + s.status() + " · " + STAMP.format(s.startedAt());
    }

    private static boolean matches(HostRow row, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String q = query.toLowerCase();
        return row.getSubdomain().toLowerCase().contains(q) || row.getIp().toLowerCase().contains(q);
    }
}
