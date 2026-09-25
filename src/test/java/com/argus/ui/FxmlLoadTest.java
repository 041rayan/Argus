package com.argus.ui;

import com.argus.ui.controller.AddApiKeyController;
import com.argus.ui.controller.DashboardController;
import com.argus.ui.controller.ExportController;
import com.argus.ui.controller.LockController;
import com.argus.ui.controller.LoginController;
import com.argus.ui.controller.TargetsController;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loads every scene through FXMLLoader to catch FXML wiring errors
 * (bad fx:id, missing import, controller typo). Needs a display for the
 * JavaFX toolkit — local gate only (ADR-009).
 */
class FxmlLoadTest {

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // toolkit already running in this JVM
        }
    }

    @Test
    void dashboardLoads() throws IOException {
        FXMLLoader loader = loader("dashboard");
        Parent root = loader.load();
        assertNotNull(root);
        assertTrue(loader.getController() instanceof DashboardController);
    }

    @Test
    void targetsLoads() throws IOException {
        FXMLLoader loader = loader("targets");
        Parent root = loader.load();
        assertNotNull(root);
        assertTrue(loader.getController() instanceof TargetsController);
    }

    @Test
    void loginLoads() throws IOException {
        FXMLLoader loader = loader("login");
        Parent root = loader.load();
        assertNotNull(root);
        assertTrue(loader.getController() instanceof LoginController);
    }

    @Test
    void lockLoads() throws IOException {
        FXMLLoader loader = loader("lock");
        Parent root = loader.load();
        assertNotNull(root);
        assertTrue(loader.getController() instanceof LockController);
    }

    @Test
    void apiKeysLoads() throws IOException {
        FXMLLoader loader = loader("apikeys");
        Parent root = loader.load();
        assertNotNull(root);
        assertTrue(loader.getController() instanceof AddApiKeyController);
    }

    @Test
    void exportLoads() throws IOException {
        FXMLLoader loader = loader("export");
        Parent root = loader.load();
        assertNotNull(root);
        assertTrue(loader.getController() instanceof ExportController);
    }

    private static FXMLLoader loader(String name) {
        URL fxml = FxmlLoadTest.class.getResource("/com/argus/ui/view/" + name + ".fxml");
        assertNotNull(fxml, "missing resource: " + name + ".fxml");
        return new FXMLLoader(fxml);
    }
}
