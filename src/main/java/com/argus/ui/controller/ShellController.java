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
    private Label keyStatusLabel;
    @FXML
    private Label statusBarLabel;
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
        swapDashboard();
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

    private void swapDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/argus/ui/view/dashboard.fxml"));
            Parent pane = loader.load();
            DashboardController controller = loader.getController();
            controller.setMain(main);
            currentController = controller;
            contentArea.getChildren().setAll(pane);
            statusBarLabel.setText("Ready");
        } catch (IOException e) {
            statusBarLabel.setText("Cannot open dashboard.");
        }
    }

    @FXML
    private void onNavDashboard() {
        showDashboard();
    }

    @FXML
    private void onNavTargets() throws IOException {
        if (leavePane()) {
            main.showTargets();
        }
    }

    @FXML
    private void onNavResults() throws IOException {
        if (leavePane()) {
            main.showResults();
        }
    }

    @FXML
    private void onNavEntryPoints() throws IOException {
        if (leavePane()) {
            main.showEntryPoints();
        }
    }

    @FXML
    private void onNavExport() throws IOException {
        if (leavePane()) {
            main.showExport();
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

    private void select(Button active) {
        for (Button b : List.of(navDashboard, navTargets, navResults,
                navEntryPoints, navExport)) {
            b.getStyleClass().remove("accent");
            if (!b.getStyleClass().contains("flat")) {
                b.getStyleClass().add("flat");
            }
        }
        active.getStyleClass().remove("flat");
        if (!active.getStyleClass().contains("accent")) {
            active.getStyleClass().add("accent");
        }
    }

    /** Key status line, called from MainApp after login (same text as dashboard today). */
    public void setKeyStatus(String line) {
        keyStatusLabel.setText(line);
    }
}
