package com.argus.ui.controller;

import com.argus.ui.MainApp;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.util.List;
/** Sidebar shell: nav buttons swap panes in the content area (spec 2026-09-27). */
public final class ShellController {

    @FXML
    private Button navDashboard;
    @FXML
    private Button navTargets;
    @FXML
    private Button navResults;
    @FXML
    private Button navEntryPoints;
    @FXML
    private Button navExport;
    @FXML
    private Button navApiKeys;
    @FXML
    private Label keyStatusLabel;
    @FXML
    private Label statusBarLabel;
    @FXML
    private Label sessionLabel;
    @FXML
    private StackPane contentArea;

    private MainApp main;
    private Object currentController;

    public void setMain(MainApp main) {
        this.main = main;
    }

    /** Wired by MainApp after the FXML load; first pane goes here. */
    public void showDashboard() {
        if (!leavePane()) {
            return;
        }
        select(navDashboard);
        setPane("dashboard", loader -> {
            DashboardController controller = loader.getController();
            controller.setMain(main);
        }, "dashboard");
    }

    public void showTargets() {
        if (!leavePane()) {
            return;
        }
        select(navTargets);
        setPane("targets", loader -> {
            TargetsController controller = loader.getController();
            controller.setMain(main);
        }, "targets");
    }

    public void showExport() {
        if (!leavePane()) {
            return;
        }
        select(navExport);
        setPane("export", loader -> {
            ExportController controller = loader.getController();
            controller.setMain(main);
        }, "export");
    }

    public void showResults() {
        if (!leavePane()) {
            return;
        }
        select(navResults);
        setPane("results", loader -> {
            ResultsController controller = loader.getController();
            controller.setMain(main);
        }, "results");
    }

    public void showEntryPoints() {
        if (!leavePane()) {
            return;
        }
        select(navEntryPoints);
        setPane("entrypoints", loader -> {
            EntryPointsController controller = loader.getController();
            controller.setMain(main);
        }, "entry points");
    }

    /**
     * Sidebar leave: warn on a live scan, release the outgoing pane.
     * False = stay where we are. Leaving never stops the scan itself.
     */
    private boolean leavePane() {
        if (currentController instanceof DashboardController dash && dash.isScanning()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "A scan is running. Leave it running in the background?");
            confirm.setHeaderText(null);
            var answer = confirm.showAndWait();
            if (answer.isEmpty() || answer.get() != ButtonType.OK) {
                return false;
            }
        }
        if (currentController instanceof ShellContent hidden) {
            hidden.onHidden();
        }
        return true;
    }

    private interface PaneWire {
        void wire(FXMLLoader loader);
    }

    private void setPane(String view, PaneWire wire, String label) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/argus/ui/view/" + view + ".fxml"));
            Parent pane = loader.load();
            wire.wire(loader);
            currentController = loader.getController();
            contentArea.getChildren().setAll(pane);
            statusBarLabel.setText("Ready");
        } catch (IOException e) {
            statusBarLabel.setText("Cannot open " + label + ".");
        }
    }

    @FXML
    private void onNavDashboard() {
        showDashboard();
    }

    @FXML
    private void onNavTargets() {
        showTargets();
    }

    @FXML
    private void onNavResults() {
        showResults();
    }

    @FXML
    private void onNavEntryPoints() {
        showEntryPoints();
    }

    @FXML
    private void onNavExport() {
        showExport();
    }

    @FXML
    private void onNavApiKeys() {
        try {
            main.showAddApiKey();
        } catch (IOException e) {
            statusBarLabel.setText("Cannot open API keys dialog.");
        }
    }

    @FXML
    private void onLock() throws IOException {
        if (leavePane()) {
            main.showLock();
        }
    }

    @FXML
    private void onLogout() {
        if (leavePane()) {
            main.onLogout();
        }
    }

    /** One class carries the selected state; the token sheet owns the rest. */
    private void select(Button active) {
        for (Button b : List.of(navDashboard, navTargets, navResults,
                navEntryPoints, navExport, navApiKeys)) {
            b.getStyleClass().remove("ag-nav-selected");
        }
        if (!active.getStyleClass().contains("ag-nav-selected")) {
            active.getStyleClass().add("ag-nav-selected");
        }
    }

    /** Status bar right side: who is at the keyboard. */
    public void setSession(String username) {
        sessionLabel.setText(username + " · vault unlocked");
    }

    /** Key status line, called from MainApp after login (same text as dashboard today). */
    public void setKeyStatus(String line) {
        keyStatusLabel.setText(line);
    }
}
