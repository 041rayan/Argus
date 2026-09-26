package com.argus.ui.controller;

import com.argus.core.export.ExportService;
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
import javafx.scene.control.ListCell;
import javafx.scene.control.TableView;
import javafx.util.StringConverter;

import java.io.IOException;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry Points view: pick a scan, ports ranked by priority score (CORE.md).
 * Reads off the FX thread; the controller only swaps lists (JAVAFX.md).
 */
public final class EntryPointsController {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    @FXML
    private ComboBox<ScanSummary> scanCombo;
    @FXML
    private TableView<EntryPointsRow> entryTable;

    private final ObservableList<ScanSummary> scans = FXCollections.observableArrayList();
    private final ObservableList<EntryPointsRow> rows = FXCollections.observableArrayList();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "entrypoints-worker");
        t.setDaemon(true);
        return t;
    });

    private MainApp main;
    private PortDAO portDao;
    private FindingDAO findingDao;

    @FXML
    private void initialize() {
        entryTable.setItems(rows);
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
                loadEntries(s.id());
            }
        });
    }

    /** Wired by MainApp after the FXML load; first loads happen here. */
    public void setMain(MainApp main) {
        this.main = main;
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

    private void loadEntries(long scanId) {
        worker.execute(() -> {
            try {
                List<EntryPointsRow> fresh = ExportService
                        .entryPoints(portDao.listByScan(scanId), findingDao.listByScan(scanId))
                        .stream().map(EntryPointsRow::new).toList();
                Platform.runLater(() -> rows.setAll(fresh));
            } catch (SQLException e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Database failure.").showAndWait());
            }
        });
    }

    @FXML
    private void onBack() {
        try {
            main.showResults();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Cannot open results view.").showAndWait();
        }
    }

    private static String label(ScanSummary s) {
        return s.target() + " · " + s.status() + " · " + STAMP.format(s.startedAt());
    }
}
