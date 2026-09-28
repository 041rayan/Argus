package com.argus.ui.controller;

import com.argus.core.model.Finding;
import com.argus.core.model.Host;
import com.argus.core.model.PortResult;
import com.argus.core.model.ScanSummary;
import com.argus.db.Database;
import com.argus.db.FindingDAO;
import com.argus.db.HostDAO;
import com.argus.db.PortDAO;
import com.argus.db.ScanDAO;
import com.argus.ui.HostDetail;
import com.argus.ui.MainApp;
import com.argus.ui.row.HostRow;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Results view: pick a scan (optionally filtered by date), hosts table with search. */
public final class ResultsController implements ShellContent {

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
    @FXML
    private Label scanMetaLabel;
    @FXML
    private Label inspectorTitle;
    @FXML
    private Label ipValue;
    @FXML
    private Label asnValue;
    @FXML
    private Label orgValue;
    @FXML
    private Label countryValue;
    @FXML
    private ListView<String> portList;
    @FXML
    private ListView<String> findingList;
    @FXML
    private VBox inspector;

    private final ObservableList<String> portLines = FXCollections.observableArrayList();
    private final ObservableList<String> findingLines = FXCollections.observableArrayList();
    private Map<String, HostDetail> details = Map.of();

    private final ObservableList<ScanSummary> scans = FXCollections.observableArrayList();
    private final ObservableList<HostRow> rows = FXCollections.observableArrayList();
    private final FilteredList<HostRow> filtered = new FilteredList<>(rows);
    /* Package-visible for the shell swap-out test (test-seam precedent). */
    final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "results-worker");
        t.setDaemon(true);
        return t;
    });

    private ScanDAO scanDao;
    private HostDAO hostDao;
    private PortDAO portDao;
    private FindingDAO findingDao;
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
        portList.setItems(portLines);
        findingList.setItems(findingLines);
        PaneLayout.bindInspectorWidth(inspector, 0.30, 260, 340);
        hostsTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, row) -> showDetail(row));
    }

    @Override
    public void onHidden() {
        executor.shutdownNow();
    }

    /** Uniform pane seam: this pane reads no session state, so it keeps no field. */
    public void setMain(MainApp main) {
        Database db = Database.inUserHome();
        this.scanDao = new ScanDAO(db);
        this.hostDao = new HostDAO(db);
        this.portDao = new PortDAO(db);
        this.findingDao = new FindingDAO(db);
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

    /**
     * One off-FX load brings the table and its inspector data together
     * (THREAD.md): no row selection ever touches the database. The
     * inspector is reset in the same hop that replaces the rows, so a
     * previous scan's ports can never outlive its table.
     */
    private void loadHosts(long scanId) {
        executor.execute(() -> {
            try {
                List<Host> hosts = hostDao.listByScan(scanId);
                List<PortResult> ports = portDao.listByScan(scanId);
                List<Finding> findings = findingDao.listByScan(scanId);
                List<HostRow> fresh = hosts.stream().map(HostRow::new).toList();
                Map<String, HostDetail> grouped = HostDetail.byHost(ports, findings);
                Platform.runLater(() -> {
                    rows.setAll(fresh);
                    details = grouped;
                    showDetail(null);
                    scanMetaLabel.setText(metaFor(hosts.size(), ports, findings));
                });
            } catch (SQLException e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Database failure.").showAndWait());
            }
        });
    }

    private static String metaFor(int hostCount, List<PortResult> ports, List<Finding> findings) {
        long open = ports.stream().filter(PortResult::open).count();
        return hostCount + " hosts · " + open + " open ports · " + findings.size() + " findings";
    }

    private void showDetail(HostRow row) {
        if (row == null) {
            inspectorTitle.setText("Select a host");
            ipValue.setText("-");
            asnValue.setText("-");
            orgValue.setText("-");
            countryValue.setText("-");
            portLines.clear();
            findingLines.clear();
            return;
        }
        HostDetail detail = details.getOrDefault(row.getSubdomain(),
                HostDetail.empty(row.getSubdomain()));
        inspectorTitle.setText(row.getSubdomain());
        ipValue.setText(orDash(row.getIp()));
        asnValue.setText(orDash(row.getAsn()));
        orgValue.setText(orDash(row.getOrg()));
        countryValue.setText(orDash(row.getCountry()));
        portLines.setAll(detail.openPorts().stream()
                .map(ResultsController::portLine).toList());
        findingLines.setAll(detail.findings().stream()
                .map(ResultsController::findingLine).toList());
    }

    /** Pure, so the line formats are unit tested without a toolkit. */
    static String portLine(PortResult p) {
        return p.port() + "/" + p.protocol() + "  " + orDash(p.service()) + "  " + orDash(p.version());
    }

    static String findingLine(Finding f) {
        return orDash(f.severity()) + "  " + orDash(f.type());
    }

    private static String orDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
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
        return row.getSubdomain().toLowerCase().contains(q) || row.getIp().toLowerCase().contains(q)
                || row.getCountry().toLowerCase().contains(q) || row.getAsn().toLowerCase().contains(q)
                || row.getOrg().toLowerCase().contains(q);
    }
}
