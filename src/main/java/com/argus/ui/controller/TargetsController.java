package com.argus.ui.controller;

import com.argus.core.model.Target;
import com.argus.core.scanner.PortList;
import com.argus.db.Database;
import com.argus.db.TargetDAO;
import com.argus.ui.MainApp;
import com.argus.ui.row.TargetRow;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Targets form and list. DAO work runs off the FX thread; UI updates via Platform.runLater. */
public final class TargetsController implements ShellContent {

    @FXML
    private TextField labelField;
    @FXML
    private TextField domainField;
    @FXML
    private TextField scopeField;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> profileCombo;
    @FXML
    private TableView<TargetRow> targetsTable;
    @FXML
    private Label inspectorTitle;
    @FXML
    private Label domainValue;
    @FXML
    private Label profileValue;
    @FXML
    private Label createdValue;
    @FXML
    private ListView<String> scopeList;
    @FXML
    private Button deleteButton;
    @FXML
    private Label formTitle;
    @FXML
    private Button saveButton;
    @FXML
    private Button cancelButton;
    @FXML
    private VBox inspector;

    /** The row the form is editing, or null when the form inserts. */
    private Target editing;

    private final ObservableList<String> scopeLines = FXCollections.observableArrayList();

    private final ObservableList<TargetRow> rows = FXCollections.observableArrayList();
    private final FilteredList<TargetRow> filtered = new FilteredList<>(rows);
    /* Package-visible for the shell swap-out test (test-seam precedent). */
    final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "target-dao");
        t.setDaemon(true);
        return t;
    });

    private TargetDAO dao;

    /** Inspector timestamps use the same stamp as the scan labels. */
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    @FXML
    private void initialize() {
        targetsTable.setItems(filtered);
        profileCombo.setItems(FXCollections.observableArrayList("quick", "full", "custom"));
        profileCombo.setValue("quick");
        searchField.textProperty().addListener((obs, old, query) -> filtered.setPredicate(row -> matches(row, query)));
        scopeList.setItems(scopeLines);
        PaneLayout.bindInspectorWidth(inspector, 0.30, 260, 340);
        deleteButton.setDisable(true);
        targetsTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, row) -> showTarget(row));
    }

    /** The add form as data. Pure, so the parsing is unit tested. */
    public record FormInput(String label, String domain, List<String> scope, String profile) {
    }

    /**
     * Reads the add/update form. Empty means a required field is blank, which
     * the caller reports rather than writing a half row.
     */
    static Optional<FormInput> readForm(String label, String domain, String scope, String profile) {
        String trimmedLabel = label == null ? "" : label.trim();
        String trimmedDomain = domain == null ? "" : domain.trim();
        if (trimmedLabel.isEmpty() || trimmedDomain.isEmpty()) {
            return Optional.empty();
        }
        List<String> cidrs = scope == null ? List.of()
                : Arrays.stream(scope.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return Optional.of(new FormInput(trimmedLabel, trimmedDomain, cidrs,
                profile == null || profile.isBlank() ? "quick" : profile));
    }

    /**
     * The record to write. A null id is an insert and keeps the given
     * timestamp; an id is an update and must keep the row's own created_at.
     */
    static Optional<Target> toTarget(FormInput form, Long id, Instant createdAt) {
        return id == null
                ? Optional.of(new Target(null, form.label(), form.domain(), form.scope(),
                        form.profile(), createdAt))
                : Optional.of(new Target(id, form.label(), form.domain(), form.scope(),
                        form.profile(), createdAt));
    }

    /** Uniform pane seam: this pane reads no session state, so it keeps no field. */
    public void setMain(MainApp main) {
        this.dao = new TargetDAO(Database.inUserHome());
        reload();
    }

    /** The right hand inspector: the selected target's full detail (JAVAFX.md). */
    private void showTarget(TargetRow row) {
        if (row == null) {
            inspectorTitle.setText("Select a target");
            domainValue.setText("-");
            profileValue.setText("-");
            createdValue.setText("-");
            scopeLines.clear();
            deleteButton.setDisable(true);
            return;
        }
        Target target = row.target();
        editing = target;
        inspectorTitle.setText(target.label());
        domainValue.setText(target.domain());
        profileValue.setText(target.profile());
        createdValue.setText(STAMP.format(target.createdAt()));
        scopeLines.setAll(target.scopeCidrs());
        deleteButton.setDisable(false);
        loadIntoForm(target);
    }

    /** Selecting a row loads it into the form, which is how an update starts. */
    private void loadIntoForm(Target target) {
        labelField.setText(target.label());
        domainField.setText(target.domain());
        scopeField.setText(String.join(", ", target.scopeCidrs()));
        profileCombo.setValue(target.profile());
        formTitle.setText("EDIT TARGET");
        saveButton.setText("Update target");
        cancelButton.setVisible(true);
    }

    /**
     * One button, two writes. With no row loaded it inserts; with a row
     * loaded it updates that row, keeping the row's own created_at.
     */
    @FXML
    private void onAdd() {
        Optional<FormInput> form = readForm(labelField.getText(), domainField.getText(),
                scopeField.getText(), profileCombo.getValue());
        if (form.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Label and domain are required.").showAndWait();
            return;
        }
        if ("custom".equals(form.get().profile()) && !editCustomPorts()) {
            return;
        }
        boolean update = editing != null;
        Target target = toTarget(form.get(), editing == null ? null : editing.id(),
                editing == null ? Instant.now() : editing.createdAt()).orElseThrow();
        executor.execute(() -> {
            try {
                if (update) {
                    dao.update(target);
                } else {
                    dao.insert(target);
                }
                Platform.runLater(() -> {
                    clearForm();
                    reload();
                });
            } catch (IllegalArgumentException e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.WARNING, "Domain already exists.").showAndWait());
            } catch (SQLException e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Database failure.").showAndWait());
            }
        });
    }

    /** Leave edit mode without writing, back to a blank insert form. */
    @FXML
    private void onCancelEdit() {
        clearForm();
    }

    /** Custom profile: collect the port list, save it, false cancels the target save. */
    private boolean editCustomPorts() {
        TextInputDialog dialog = new TextInputDialog(PortList.customText());
        dialog.setHeaderText("Custom port list — comma-separated, 1–65535");
        Optional<String> answer = dialog.showAndWait();
        if (answer.isEmpty()) {
            return false;
        }
        List<Integer> ports = Arrays.stream(answer.get().split("[,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Integer.parseInt(s);
                    } catch (NumberFormatException e) {
                        return -1;
                    }
                })
                .filter(p -> p >= 1 && p <= 65535)
                .distinct()
                .toList();
        if (ports.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "No valid ports — list unchanged.").showAndWait();
            return false;
        }
        try {
            PortList.saveCustom(ports);
            return true;
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Cannot write custom port list.").showAndWait();
            return false;
        }
    }

    @FXML
    private void onDelete() {
        TargetRow selected = targetsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.WARNING, "Select a target first.").showAndWait();
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete target " + selected.getDomain() + "?", ButtonType.OK, ButtonType.CANCEL);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        long id = selected.getId();
        executor.execute(() -> {
            try {
                dao.delete(id);
                Platform.runLater(this::reload);
            } catch (SQLException e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Database failure.").showAndWait());
            }
        });
    }

    @Override
    public void onHidden() {
        executor.shutdownNow();
    }

    private void reload() {
        executor.execute(() -> {
            try {
                List<TargetRow> fresh = dao.list().stream().map(TargetRow::new).toList();
                Platform.runLater(() -> rows.setAll(fresh));
            } catch (SQLException e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Database failure.").showAndWait());
            }
        });
    }

    private void clearForm() {
        editing = null;
        labelField.clear();
        domainField.clear();
        scopeField.clear();
        profileCombo.setValue("quick");
        formTitle.setText("NEW TARGET");
        saveButton.setText("Add target");
        cancelButton.setVisible(false);
    }

    private static boolean matches(TargetRow row, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String q = query.toLowerCase();
        return row.getLabel().toLowerCase().contains(q) || row.getDomain().toLowerCase().contains(q);
    }
}
