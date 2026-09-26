package com.argus.ui.controller;

import com.argus.core.api.VtKeyVerifier;
import com.argus.db.ApiKeysDAO;
import com.argus.db.AuditDAO;
import com.argus.db.Database;
import com.argus.ui.MainApp;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Add API Key" dialog: secondary Stage, window-modal, holds its Stage and
 * closes it. Keys are encrypted with the session vault key before storage and
 * never rendered back — status shows configured/not configured only. A saved
 * VirusTotal key is verified with one live call; the verdict shows in the
 * message line and an invalid key keeps the dialog open.
 */
public final class AddApiKeyController {

    private static final String VIRUSTOTAL = "VirusTotal";
    private static final String[] PROVIDERS = {VIRUSTOTAL};

    @FXML
    private Label statusLabel;
    @FXML
    private ComboBox<String> providerCombo;
    @FXML
    private PasswordField keyField;
    @FXML
    private Label messageLabel;
    @FXML
    private Button saveButton;
    @FXML
    private Button removeButton;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "apikey-worker");
        t.setDaemon(true);
        return t;
    });

    private Stage stage;
    private MainApp main;
    private ApiKeysDAO dao;
    private AuditDAO audit;
    private volatile Set<String> configured = Set.of();

    /** Wired by MainApp right after the FXML load — never FXMLLoader. */
    public void init(Stage stage, MainApp main) {
        this.stage = stage;
        this.main = main;
        Database db = Database.inUserHome();
        this.dao = new ApiKeysDAO(db);
        this.audit = new AuditDAO(db);
        providerCombo.getItems().setAll(PROVIDERS);
        providerCombo.setValue(PROVIDERS[0]);
        providerCombo.valueProperty().addListener((obs, old, v) -> refreshStatus());
        refreshStatus();  // initial load (listener already fired on setValue)
    }

    @FXML
    private void onSave() {
        String provider = providerCombo.getValue();
        byte[] key = keyField.getText().getBytes(StandardCharsets.UTF_8);
        if (key.length == 0) {
            messageLabel.setText("Key required.");
            return;
        }
        if (main.vaultKey() == null) {
            messageLabel.setText("Session locked — sign in again.");
            return;
        }
        worker.execute(() -> {
            try {
                dao.upsert(main.operatorId(), provider, key, main.vaultKey());
                audit.write(main.operatorId(), "KEY_ADDED", provider);
                if (VIRUSTOTAL.equals(provider)) {
                    Platform.runLater(() -> messageLabel.setText("Saved. Verifying key…"));
                    int status = new VtKeyVerifier().check(new String(key, StandardCharsets.UTF_8));
                    Platform.runLater(() -> onVerified(status));
                } else {
                    Platform.runLater(() -> {
                        messageLabel.setText("Saved.");
                        keyField.clear();
                        close();
                    });
                }
            } catch (SQLException e) {
                Platform.runLater(() -> messageLabel.setText("Database failure."));
            } finally {
                Arrays.fill(key, (byte) 0);
            }
        });
    }

    /** Verdict after the save — an invalid key keeps the dialog open for a re-add. */
    private void onVerified(int status) {
        keyField.clear();
        switch (status) {
            case 200 -> {
                messageLabel.setText("Saved. VirusTotal key verified.");
                close();
            }
            case 429 -> {
                messageLabel.setText("Saved. Key accepted (rate limited).");
                close();
            }
            case 401, 403 -> messageLabel.setText(
                    "Saved, but VirusTotal rejected the key (HTTP " + status + "). Re-add a valid one.");
            case -1 -> {
                messageLabel.setText("Saved. Could not verify — provider unreachable.");
                close();
            }
            default -> {
                messageLabel.setText("Saved. Verification status " + status + " — key kept as-is.");
                close();
            }
        }
    }

    @FXML
    private void onRemove() {
        String provider = providerCombo.getValue();
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Remove the stored " + provider + " key?", ButtonType.OK, ButtonType.CANCEL);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        worker.execute(() -> {
            try {
                if (dao.delete(main.operatorId(), provider)) {
                    audit.write(main.operatorId(), "KEY_REMOVED", provider);
                }
                Platform.runLater(this::close);
            } catch (SQLException e) {
                Platform.runLater(() -> messageLabel.setText("Database failure."));
            }
        });
    }

    @FXML
    private void onClose() {
        close();
    }

    private void close() {
        worker.shutdown();
        stage.close();
    }

    private void refreshStatus() {
        String provider = providerCombo.getValue();
        worker.execute(() -> {
            try {
                boolean has = dao.configuredProviders(main.operatorId()).contains(provider);
                Platform.runLater(() -> {
                    statusLabel.setText(provider + ": " + (has ? "configured (masked)" : "not configured"));
                    removeButton.setDisable(!has);
                });
            } catch (SQLException e) {
                Platform.runLater(() -> statusLabel.setText("Database failure."));
            }
        });
    }
}
