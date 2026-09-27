package com.argus.ui;

import atlantafx.base.theme.PrimerDark;
import com.argus.core.auth.LoginService;
import com.argus.core.concurrency.IdleLockMonitor;
import com.argus.db.AuditDAO;
import com.argus.db.ApiKeysDAO;
import com.argus.db.Database;
import com.argus.db.ScanDAO;
import com.argus.ui.controller.AddApiKeyController;
import com.argus.ui.controller.LockController;
import com.argus.ui.controller.LoginController;
import com.argus.ui.controller.ShellController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Set;

/**
 * Argus HQ: owns the primary Stage, the session (operator id, vault key,
 * idle monitor) and the scene switches — login, dashboard, targets, lock.
 * One Stage, many Scenes. Controllers are created by FXMLLoader and
 * receive this class through setMain — never by us.
 */
public final class MainApp extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(MainApp.class);

    /** Every main window opens here. The spec fixes these four numbers. */
    static final int WINDOW_WIDTH = 1280;
    static final int WINDOW_HEIGHT = 720;
    static final int MIN_WIDTH = 1100;
    static final int MIN_HEIGHT = 640;

    private Stage primaryStage;

    // session state is written on the FX thread and read by the monitor/worker threads
    private volatile String currentUsername = "";
    private volatile long operatorId = -1;
    private volatile byte[] vaultKey;
    private volatile IdleLockMonitor idleMonitor;

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;
        stage.setTitle("Argus");
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        reconcileStaleScans();
        showLogin();
        stage.show();
    }

    /**
     * Startup sweep, off the FX thread: a row still RUNNING outlived its
     * session (single instance — DB.md), so mark it FAILED with the reason.
     * Never blocks or fails startup.
     */
    private void reconcileStaleScans() {
        Thread t = new Thread(() -> {
            try {
                int fixed = new ScanDAO(Database.inUserHome()).reconcileStale();
                if (fixed > 0) {
                    LOG.info("marked {} interrupted scan(s) failed", fixed);
                }
            } catch (SQLException e) {
                LOG.warn("stale scan reconcile failed: {}", e.getMessage());
            }
        }, "startup-reconcile");
        t.setDaemon(true);
        t.start();
    }

    /** Login is the entry scene — every start goes through authentication. */
    public void showLogin() throws IOException {
        FXMLLoader l = loader("login");
        Scene scene = new Scene(l.load(), WINDOW_WIDTH, WINDOW_HEIGHT);
        applyCss(scene);
        LoginController controller = l.getController();
        controller.setMain(this);
        applyGeometry(scene);
    }

    /** Sidebar shell: the content area hosts every pane, dashboard first. */
    public void showShell() throws IOException {
        FXMLLoader l = loader("shell");
        Scene scene = new Scene(l.load(), WINDOW_WIDTH, WINDOW_HEIGHT);
        applyCss(scene);
        ShellController controller = l.getController();
        controller.setMain(this);
        applyGeometry(scene);
        controller.setSession(currentUsername);
        controller.showDashboard();
        refreshShellKeyStatus(controller);
    }

    /** The single place window geometry is set, so no scene can drift. */
    private void applyGeometry(Scene scene) {
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(MIN_WIDTH);
        primaryStage.setMinHeight(MIN_HEIGHT);
    }

    private void refreshShellKeyStatus(ShellController controller) {
        Thread t = new Thread(() -> {
            try {
                Set<String> providers =
                        new ApiKeysDAO(Database.inUserHome()).configuredProviders(operatorId);
                String line = "VirusTotal: "
                        + (providers.contains("VirusTotal") ? "configured" : "not configured");
                Platform.runLater(() -> controller.setKeyStatus(line));
            } catch (SQLException e) {
                Platform.runLater(() -> controller.setKeyStatus("Key status unavailable."));
            }
        }, "shell-key-status");
        t.setDaemon(true);
        t.start();
    }

    /** Shown by the idle monitor callback (always via Platform.runLater). */
    public void showLock() throws IOException {
        FXMLLoader l = loader("lock");
        Scene scene = new Scene(l.load(), WINDOW_WIDTH, WINDOW_HEIGHT);
        applyCss(scene);
        LockController controller = l.getController();
        controller.setMain(this);
        applyGeometry(scene);
    }

    /** Called by LoginController and LockController on a successful verify. */
    public void onLoginSuccess(String username, LoginService.LoginResult result) {
        currentUsername = username;
        operatorId = result.operatorId();
        vaultKey = result.vaultKey();
        try {
            showShell();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Cannot open dashboard view.").showAndWait();
            return;
        }
        startIdleMonitor();
    }

    /** Logout: wipe the vault key, stop the monitor, back to login. */
    public void onLogout() {
        endSession();
        try {
            showLogin();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Cannot open login view.").showAndWait();
        }
    }

    public String currentUsername() {
        return currentUsername;
    }

    public long operatorId() {
        return operatorId;
    }

    /** Null only while locked or logged out. */
    public byte[] vaultKey() {
        return vaultKey;
    }

    /** Secondary Stage: owner set, window-modal; controller holds it and closes it (JAVAFX.md). */
    public void showAddApiKey() throws IOException {
        FXMLLoader l = loader("apikeys");
        Parent root = l.load();
        Stage dialog = new Stage();
        dialog.initOwner(primaryStage);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("API Keys");
        dialog.setScene(new Scene(root));
        applyCss(dialog.getScene());
        AddApiKeyController controller = l.getController();
        controller.init(dialog, this);
        dialog.show();
    }

    private void startIdleMonitor() {
        if (idleMonitor != null) {
            idleMonitor.interrupt();
        }
        idleMonitor = new IdleLockMonitor(this::onIdleLock);
        idleMonitor.start();
    }

    /** IdleLockMonitor callback — runs on the monitor thread, hop to FX. */
    private void onIdleLock() {
        if (vaultKey != null) {
            Arrays.fill(vaultKey, (byte) 0);
            vaultKey = null;
        }
        if (operatorId != -1) {
            try {
                new AuditDAO(Database.inUserHome()).write(operatorId, "LOCKED", "idle timeout");
            } catch (Exception e) {
                // audit failure must not stop the lock; nothing sensitive here
            }
        }
        Platform.runLater(() -> {
            try {
                showLock();
            } catch (IOException e) {
                new Alert(Alert.AlertType.ERROR, "Cannot open lock view.").showAndWait();
            }
        });
    }

    private void endSession() {
        if (idleMonitor != null) {
            idleMonitor.interrupt();
            idleMonitor = null;
        }
        if (vaultKey != null) {
            Arrays.fill(vaultKey, (byte) 0);
            vaultKey = null;
        }
        operatorId = -1;
        currentUsername = "";
    }

    private FXMLLoader loader(String name) {
        return new FXMLLoader(MainApp.class.getResource("/com/argus/ui/view/" + name + ".fxml"));
    }

    private void applyCss(Scene scene) {
        URL css = MainApp.class.getResource("/com/argus/ui/view/application.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        // scene-level activity filter feeds the idle monitor
        scene.addEventFilter(Event.ANY, e -> {
            if (idleMonitor != null) {
                idleMonitor.touch();
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
