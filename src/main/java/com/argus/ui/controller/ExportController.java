package com.argus.ui.controller;

import com.argus.core.export.ExportService;
import com.argus.core.model.Target;
import com.argus.db.Database;
import com.argus.db.TargetDAO;
import com.argus.ui.MainApp;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressIndicator;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Export scene: pick target + format, save through a FileChooser. The write
 * runs off the FX thread behind an application-modal progress dialog
 * (JAVAFX.md); completion is an INFORMATION alert.
 */
public final class ExportController {

    private static final String MARKDOWN = "Markdown";
    private static final String JSON = "JSON";

    @FXML
    private ComboBox<Target> targetCombo;
    @FXML
    private ComboBox<String> formatCombo;
    @FXML
    private Label statusLabel;

    private final ExportService service = new ExportService();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "export-worker");
        t.setDaemon(true);
        return t;
    });

    private MainApp main;

    @FXML
    private void initialize() {
        formatCombo.getItems().setAll(MARKDOWN, JSON);
        formatCombo.setValue(MARKDOWN);
        targetCombo.setCellFactory(c -> new ListCell<>() {
            @Override
            protected void updateItem(Target item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label() + " (" + item.domain() + ")");
            }
        });
        targetCombo.setConverter(new StringConverter<>() {
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

    /** Wired by MainApp after the FXML load; target list loads off-thread. */
    public void setMain(MainApp main) {
        this.main = main;
        worker.execute(() -> {
            try {
                List<Target> targets = new TargetDAO(Database.inUserHome()).list();
                Platform.runLater(() -> targetCombo.getItems().setAll(targets));
            } catch (SQLException e) {
                Platform.runLater(() -> statusLabel.setText("Database failure."));
            }
        });
    }

    @FXML
    private void onExport() {
        Target target = targetCombo.getValue();
        if (target == null) {
            new Alert(Alert.AlertType.WARNING, "Pick a target first.").showAndWait();
            return;
        }
        boolean markdown = MARKDOWN.equals(formatCombo.getValue());
        File file = chooser(markdown).showSaveDialog(window());
        if (file == null) {
            return;
        }
        Alert progress = progressDialog();
        progress.show();
        worker.execute(() -> write(target, markdown, file, progress));
    }

    private void write(Target target, boolean markdown, File file, Alert progress) {
        try {
            String content = markdown
                    ? service.markdown(target.domain(), null, main.currentUsername(), List.of(), List.of())
                    : service.json(target.domain(), null, List.of(), List.of());
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
            Platform.runLater(() -> {
                progress.close();
                new Alert(Alert.AlertType.INFORMATION, "Export complete: " + file.getName()).showAndWait();
                statusLabel.setText("Exported " + file.getName());
            });
        } catch (IOException e) {
            Platform.runLater(() -> {
                progress.close();
                new Alert(Alert.AlertType.ERROR, "Cannot write file: " + e.getMessage()).showAndWait();
            });
        }
    }

    private FileChooser chooser(boolean markdown) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export target package");
        String name = targetCombo.getValue().domain();
        chooser.setInitialFileName(name + (markdown ? ".md" : ".json"));
        if (markdown) {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown", "*.md"));
        } else {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        }
        return chooser;
    }

    private Alert progressDialog() {
        Alert progress = new Alert(Alert.AlertType.INFORMATION);
        progress.initModality(Modality.APPLICATION_MODAL);
        progress.initOwner(window());
        progress.setTitle("Export");
        progress.setHeaderText("Exporting…");
        progress.getDialogPane().setContent(new ProgressIndicator());
        return progress;
    }

    private Window window() {
        return targetCombo.getScene().getWindow();
    }

    @FXML
    private void onBack() {
        try {
            main.showDashboard();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Cannot open dashboard view.").showAndWait();
        }
    }
}
