package com.argus.ui.controller;

import com.argus.core.auth.LoginService;
import com.argus.db.AuditDAO;
import com.argus.db.Database;
import com.argus.db.OperatorDAO;
import com.argus.ui.MainApp;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Unlock after auto-lock: verify the same operator's password off-thread, hand the fresh vault key back. */
public final class LockController {

    @FXML
    private PasswordField passwordField;
    @FXML
    private Button unlockButton;
    @FXML
    private Label statusLabel;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "unlock-worker");
        t.setDaemon(true);
        return t;
    });

    private MainApp main;
    private LoginService service;

    public void setMain(MainApp main) {
        this.main = main;
        Database db = Database.inUserHome();
        this.service = new LoginService(new OperatorDAO(db), new AuditDAO(db));
    }

    @FXML
    private void onUnlock() {
        char[] password = passwordField.getText().toCharArray();
        if (password.length == 0) {
            statusLabel.setText("Password required.");
            return;
        }
        unlockButton.setDisable(true);
        statusLabel.setText("Verifying…");
        worker.execute(() -> {
            try {
                LoginService.LoginResult result = service.login(main.currentUsername(), password);
                Platform.runLater(() -> {
                    if (result.success()) {
                        main.onLoginSuccess(main.currentUsername(), result);
                    } else {
                        statusLabel.setText(result.message());
                        passwordField.clear();
                        unlockButton.setDisable(false);
                    }
                });
            } catch (SQLException e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Database failure.");
                    unlockButton.setDisable(false);
                });
            } finally {
                Arrays.fill(password, '\0');
            }
        });
    }
}
