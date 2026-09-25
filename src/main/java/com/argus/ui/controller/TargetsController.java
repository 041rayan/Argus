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
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Targets form and list. DAO work runs off the FX thread; UI updates via Platform.runLater. */
public final class TargetsController {

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

    private final ObservableList<TargetRow> rows = FXCollections.observableArrayList();
    private final FilteredList<TargetRow> filtered = new FilteredList<>(rows);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "target-dao");
        t.setDaemon(true);
        return t;
    });

    private MainApp main;
    private TargetDAO dao;

    @FXML
    private void initialize() {
        targetsTable.setItems(filtered);
        profileCombo.setItems(FXCollections.observableArrayList("quick", "full", "custom"));
        profileCombo.setValue("quick");
        searchField.textProperty().addListener((obs, old, query) -> filtered.setPredicate(row -> matches(row, query)));
    }

    /** Wired by MainApp after the FXML load; first list load happens here. */
    public void setMain(MainApp main) {
        this.main = main;
        this.dao = new TargetDAO(Database.inUserHome());
        reload();
    }

    @FXML
    private void onAdd() {
        String label = labelField.getText().trim();
        String domain = domainField.getText().trim();
        if (label.isEmpty() || domain.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Label and domain are required.").showAndWait();
            return;
        }
        String profile = profileCombo.getValue();
        if ("custom".equals(profile) && !editCustomPorts()) {
            return;
        }
        List<String> scope = Arrays.stream(scopeField.getText().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        Target target = new Target(null, label, domain, scope, profile, Instant.now());
        executor.execute(() -> {
            try {
                dao.insert(target);
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

    @FXML
    private void onBack() {
        try {
            main.showDashboard();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Cannot open dashboard view.").showAndWait();
        }
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
        labelField.clear();
        domainField.clear();
        scopeField.clear();
        profileCombo.setValue("quick");
    }

    private static boolean matches(TargetRow row, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String q = query.toLowerCase();
        return row.getLabel().toLowerCase().contains(q) || row.getDomain().toLowerCase().contains(q);
    }
}
