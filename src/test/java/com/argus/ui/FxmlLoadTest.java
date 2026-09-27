package com.argus.ui;

import com.argus.ui.controller.AddApiKeyController;
import com.argus.ui.controller.DashboardController;
import com.argus.ui.controller.EntryPointsController;
import com.argus.ui.controller.ExportController;
import com.argus.ui.controller.ResultsController;
import com.argus.ui.controller.LockController;
import com.argus.ui.controller.LoginController;
import com.argus.ui.controller.TargetsController;
import com.argus.ui.controller.ShellController;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void markLoads() throws IOException {
        FXMLLoader loader = loader("mark");
        Parent root = loader.load();
        assertNotNull(root);
        assertEquals(3, root.lookupAll(".ag-mark-eye").size(),
                "the outer ellipse plus the two iris arcs");
        assertEquals(2, root.lookupAll(".ag-mark-pupil").size(),
                "one pupil per iris arc");
    }

    @Test
    void shellLoads() throws IOException {
        FXMLLoader loader = loader("shell");
        Parent root = loader.load();
        assertNotNull(root);
        assertTrue(loader.getController() instanceof ShellController);
    }

    @Test
    void dashboardLoads() throws IOException {
        FXMLLoader loader = loader("dashboard");
        Parent root = loader.load();
        assertNotNull(root);
        assertTrue(loader.getController() instanceof DashboardController);
    }

    @Test
    void dashboardUsesTokenClasses() throws IOException {
        FXMLLoader loader = loader("dashboard");
        // A ScrollPane keeps its content out of getChildren(), so lookup must
        // start at the content node, not the root.
        Parent pane = (Parent) ((ScrollPane) loader.load()).getContent();

        assertEquals(5, pane.lookupAll(".ag-tile").size(),
                "one tile per stat: scans, hosts, ports, KEV, VT");
        assertEquals(2, pane.lookupAll(".ag-chart").size(),
                "severity and top ports charts");
        assertNotNull(pane.lookup("#scanButton"), "scan command card lost its button");
        assertTrue(pane.lookup("#scanButton").getStyleClass().contains("ag-primary"),
                "the scan command is the view's one primary");
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

    @Test
    void resultsLoads() throws IOException {
        FXMLLoader loader = loader("results");
        Parent root = loader.load();
        assertNotNull(root);
        assertTrue(loader.getController() instanceof ResultsController);
    }

    @Test
    void targetsAndExportUseTokenClasses() throws IOException {
        FXMLLoader targets = loader("targets");
        Parent root = targets.load();

        assertEquals(1, root.lookupAll(".ag-inspector").size(),
                "one inspector, on the right of the table");
        assertEquals(1, root.lookupAll(".ag-table").size());
        assertNotNull(root.lookup("#deleteButton"), "the row level delete needs an id");
        assertTrue(root.lookup("#deleteButton").getStyleClass().contains("ag-danger"),
                "delete target is the row level destructive action");
        assertNotNull(root.lookup("#scopeList"), "the full CIDR list needs a home");

        FXMLLoader export = loader("export");
        Parent pane = export.load();
        assertTrue(pane.lookupAll(".ag-card").size() >= 1,
                "export is one card, not a floating button row");
    }

    @Test
    void resultsAndEntryPointsUseTokenClasses() throws IOException {
        FXMLLoader results = loader("results");
        Parent hosts = results.load();
        assertEquals(1, hosts.lookupAll(".ag-inspector").size());
        TableView<?> hostTable = (TableView<?>) hosts.lookup("#hostsTable");
        assertEquals(4, hostTable.getColumns().size(),
                "ASN and Org moved into the inspector");
        assertTrue(hosts.lookup("#deleteButton").getStyleClass().contains("ag-danger"),
                "delete scan is the scan level destructive action");

        FXMLLoader entry = loader("entrypoints");
        Parent points = entry.load();
        assertEquals(1, points.lookupAll(".ag-inspector").size());
        TableView<?> entryTable = (TableView<?>) points.lookup("#entryTable");
        assertEquals(7, entryTable.getColumns().size(),
                "entry points keeps all seven columns");
        // A TableColumn is not a Node, so it comes off the column list, not lookup.
        TableColumn<?, ?> kev = entryTable.getColumns().get(4);
        assertEquals("KEV", kev.getText());
        assertTrue(kev.getStyleClass().contains("ag-kev"),
                "the KEV column is monospace and truncating");
    }

    @Test
    void entryPointsLoads() throws IOException {
        FXMLLoader loader = loader("entrypoints");
        Parent root = loader.load();
        assertNotNull(root);
        assertTrue(loader.getController() instanceof EntryPointsController);
    }

    private static FXMLLoader loader(String name) {
        URL fxml = FxmlLoadTest.class.getResource("/com/argus/ui/view/" + name + ".fxml");
        assertNotNull(fxml, "missing resource: " + name + ".fxml");
        return new FXMLLoader(fxml);
    }
}
