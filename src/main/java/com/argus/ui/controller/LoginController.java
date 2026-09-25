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
import javafx.scene.control.TextField;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Login and first-run account creation. PBKDF2 (~0.3 s) and all DAO work run
 * on a daemon worker; only the final UI update touches the FX thread. The
 * password char[] is wiped right after the service call.
 */
public final class LoginController {

    @FXML
    private Label modeLabel;
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button primaryButton;
    @FXML
    private Button switchModeButton;
    @FXML
    private Label statusLabel;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "login-worker");
        t.setDaemon(true);
        return t;
    });

    private MainApp main;
    private LoginService service;
    private boolean createMode;
    private boolean firstRun;

    /** Wired by MainApp after the FXML load; first-run check runs off-thread. */
    public void setMain(MainApp main) {
        this.main = main;
        Database db = Database.inUserHome();
        this.service = new LoginService(new OperatorDAO(db), new AuditDAO(db));
        worker.execute(() -> {
            try {
                boolean hasAccounts = service.hasAccounts();
                firstRun = !hasAccounts;
                Platform.runLater(() -> setCreateMode(firstRun));
            } catch (SQLException e) {
                Platform.runLater(() -> statusLabel.setText("Database failure."));
            }
        });
    }

    @FXML
    private void onSubmit() {
        String username = usernameField.getText().trim();
        char[] password = passwordField.getText().toCharArray();
        if (username.isEmpty() || password.length == 0) {
            statusLabel.setText("Username and password are required.");
            Arrays.fill(password, '\0');
            return;
        }
        primaryButton.setDisable(true);
        statusLabel.setText(createMode ? "Creating account…" : "Verifying…");
        worker.execute(() -> {
            try {
                if (createMode) {
                    service.createAccount(username, password);
                }
                LoginService.LoginResult result = service.login(username, password);
                Platform.runLater(() -> onResult(result));
            } catch (IllegalArgumentException e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Username already taken.");
                    primaryButton.setDisable(false);
                });
            } catch (SQLException e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Database failure.");
                    primaryButton.setDisable(false);
                });
            } finally {
                Arrays.fill(password, '\0');
            }
        });
    }

    private void onResult(LoginService.LoginResult result) {
        if (result.success()) {
            main.onLoginSuccess(usernameField.getText().trim(), result);
            return;
        }
        statusLabel.setText(result.message());
        passwordField.clear();
        primaryButton.setDisable(false);
    }

    private void setCreateMode(boolean create) {
        this.createMode = create;
        if (create) {
            modeLabel.setText(firstRun
                    ? "First run — create the operator account."
                    : "New operator — choose a username and password.");
            primaryButton.setText("Create account");
            switchModeButton.setText("Back to sign in");
        } else {
            modeLabel.setText("Sign in to continue.");
            primaryButton.setText("Sign in");
            switchModeButton.setText("Create account");
        }
    }

    @FXML
    private void onToggleMode() {
        setCreateMode(!createMode);
        usernameField.clear();
        passwordField.clear();
        statusLabel.setText("");
    }
}
