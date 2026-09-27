# UI Shell Cycle 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Shell window with sidebar navigation hosting the unchanged dashboard as its first pane.

**Architecture:** New `shell.fxml` (`BorderPane`) + `ShellController` own navigation and content swapping. `MainApp` gains `showShell()`; login lands on the shell. Unmigrated views keep working through the existing `showX()` scene switches. Dashboard content rework (cards/stats/charts) is the next plan, not this one.

**Tech Stack:** Java 21, JavaFX 21, AtlantaFX 2.1.0 (`accent`, `flat` style classes), JUnit 5, FXML.

**Spec:** `docs/superpowers/specs/2026-09-27-ui-shell-design.md` — this plan implements its Section 1 plus the cycle-1 row of its migration table.

## Global Constraints

- Java 21, Maven; no new dependencies.
- `com.argus.core` keeps zero `javafx.*` imports.
- All UI updates via `Platform.runLater`; no DAO or network I/O on the FX thread, no I/O in `initialize()`.
- FXML rules: every main view is FXML with fully-qualified `fx:controller`; `fx:id` camelCase matching `@FXML` fields; controllers created only by FXMLLoader.
- Styling via AtlantaFX classes + `application.css` only; never `setStyle` in controllers.
- ≤200 changed lines for the whole cycle; human runs all git commands (no `git commit` steps in this plan — end each task with verify + report instead).
- Gate per task: `mvn verify` green.

## Review Focus

- Sidebar button for an unmigrated view must fall back to the existing `showX()` scene, never a dead button or blank pane.
- Swapping the dashboard pane must shut down the old controller's executors, or every visit leaks two threads.
- Navigating away mid-scan must not kill the scan and must not throw from stale label updates.
- Shell must render sane at 1100x700 minimum with no fixed scene size on the shell itself.
- A wrong style class name fails silently, so every task ends with a human visual glance, not just tests.

---
### Task 1: shell.fxml

**Files:**
- Create: `src/main/resources/com/argus/ui/view/shell.fxml`
- Test: `src/test/java/com/argus/ui/FxmlLoadTest.java` (add `shellLoads`)

**Interfaces:**
- Consumes: nothing (standalone FXML, controller in Task 2).
- Produces: `fx:id`s `navDashboard`, `navTargets`, `navResults`, `navEntryPoints`, `navExport` (Buttons), `keyStatusLabel` (Label), `lockButton`, `logoutButton` (Buttons), `contentArea` (StackPane), `statusBarLabel` (Label) — exact names Task 2 wires.

- [ ] **Step 1: Write the FXML**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.Separator?>
<?import javafx.scene.layout.BorderPane?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.StackPane?>
<?import javafx.scene.layout.VBox?>
<BorderPane fx:id="root"
            styleClass="root-pane"
            xmlns="http://javafx.com/javafx/21.0.8"
            xmlns:fx="http://javafx.com/fxml/1"
            fx:controller="com.argus.ui.controller.ShellController">
    <left>
        <VBox spacing="4.0" style="-fx-padding: 16 12 16 12;">
            <children>
                <Label styleClass="title-label" text="Argus"/>
                <Separator/>
                <Button fx:id="navDashboard" maxWidth="Infinity" mnemonicParsing="false"
                        onAction="#onNavDashboard" styleClass="flat" text="Dashboard"/>
                <Button fx:id="navTargets" maxWidth="Infinity" mnemonicParsing="false"
                        onAction="#onNavTargets" styleClass="flat" text="Targets"/>
                <Button fx:id="navResults" maxWidth="Infinity" mnemonicParsing="false"
                        onAction="#onNavResults" styleClass="flat" text="Results"/>
                <Button fx:id="navEntryPoints" maxWidth="Infinity" mnemonicParsing="false"
                        onAction="#onNavEntryPoints" styleClass="flat" text="Entry Points"/>
                <Button fx:id="navExport" maxWidth="Infinity" mnemonicParsing="false"
                        onAction="#onNavExport" styleClass="flat" text="Export"/>
                <Separator/>
                <Label fx:id="keyStatusLabel" styleClass="status-label" text=""/>
                <Button fx:id="lockButton" maxWidth="Infinity" mnemonicParsing="false"
                        onAction="#onLock" styleClass="flat" text="Lock"/>
                <Button fx:id="logoutButton" maxWidth="Infinity" mnemonicParsing="false"
                        onAction="#onLogout" styleClass="flat" text="Log out"/>
            </children>
        </VBox>
    </left>
    <center>
        <StackPane fx:id="contentArea"/>
    </center>
    <bottom>
        <HBox>
            <children>
                <Label fx:id="statusBarLabel" styleClass="status-label" text="Ready"/>
            </children>
        </HBox>
    </bottom>
</BorderPane>
```

- [ ] **Step 2: Add the load test (fails — no controller class yet)**

```java
    @Test
    void shellLoads() throws IOException {
        FXMLLoader loader = loader("shell");
        Parent root = loader.load();
        assertNotNull(root);
        assertTrue(loader.getController() instanceof ShellController);
    }
```

Plus import: `import com.argus.ui.controller.ShellController;`

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn test -Dtest=FxmlLoadTest#shellLoads`
Expected: FAIL (controller class does not exist; load throws).

- [ ] **Step 4: Verify + report for human commit**

Run: `mvn test -Dtest=FxmlLoadTest 2>&1 | tail -3`
Expected: all other FXML tests still PASS; only `shellLoads` fails. Report the diff for human commit.

---
### Task 2: ShellController + content-pane contract

**Files:**
- Create: `src/main/java/com/argus/ui/controller/ShellController.java`
- Create: `src/main/java/com/argus/ui/controller/ShellContent.java`
- Modify: `src/main/java/com/argus/ui/controller/DashboardController.java` (implement `ShellContent`, add `shutdown()`)

**Interfaces:**
- Consumes: `MainApp` via `setMain(MainApp)` (same pattern as every controller); Task 1 `fx:id`s.
- Produces: `ShellContent.onHidden()` (called when a pane leaves the content area); `ShellController.showDashboard()` used by Task 3; nav handlers for unmigrated views delegate to `MainApp.showTargets/showResults/showEntryPoints/showExport`.

- [ ] **Step 1: Write the contract**

```java
package com.argus.ui.controller;

/** A view hosted in the shell content area. Called when the pane is swapped out. */
public interface ShellContent {

    /** Release threads, subscriptions, listeners. Default no-op for stateless panes. */
    default void onHidden() {
    }
}
```

- [ ] **Step 2: Write the controller**

```java
package com.argus.ui.controller;

import com.argus.ui.MainApp;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
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
        select(navDashboard);
        setPane("dashboard", new DashboardWire());
    }

    @FXML
    private void onNavDashboard() {
        showDashboard();
    }

    @FXML
    private void onNavTargets() throws IOException {
        main.showTargets();
    }

    @FXML
    private void onNavResults() throws IOException {
        main.showResults();
    }

    @FXML
    private void onNavEntryPoints() throws IOException {
        main.showEntryPoints();
    }

    @FXML
    private void onNavExport() throws IOException {
        main.showExport();
    }

    @FXML
    private void onLock() throws IOException {
        main.showLock();
    }

    @FXML
    private void onLogout() {
        main.onLogout();
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

    private interface PaneWire {
        void wire(FXMLLoader loader);
    }

    private record DashboardWire() implements PaneWire {
        @Override
        public void wire(FXMLLoader loader) {
            DashboardController controller = loader.getController();
            controller.setMain(mainHolder());
        }

        private MainApp mainHolder() {
            return ShellController.this.main;
        }
    }

    private void setPane(String view, PaneWire wire) {
        if (currentController instanceof ShellContent hidden) {
            hidden.onHidden();
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/argus/ui/view/" + view + ".fxml"));
            Parent pane = loader.load();
            wire.wire(loader);
            currentController = loader.getController();
            contentArea.getChildren().setAll(pane);
            statusBarLabel.setText("Ready");
        } catch (IOException e) {
            statusBarLabel.setText("Cannot open " + view + ".");
        }
    }

    /** Key status line, called from MainApp after login (same text as dashboard today). */
    public void setKeyStatus(String line) {
        keyStatusLabel.setText(line);
    }
}
```

- [ ] **Step 3: DashboardController implements ShellContent with shutdown**

Add to the class declaration: `implements ShellContent` (same package, no import).
Add the method (executor fields `worker` and `coordinator` already exist):

```java
    @Override
    public void onHidden() {
        worker.shutdownNow();
        coordinator.shutdownNow();
    }
```

- [ ] **Step 4: Run the tests**

Run: `mvn test -Dtest=FxmlLoadTest`
Expected: PASS including `shellLoads`. (`onHidden`/`select` are exercised live in Task 5.)

- [ ] **Step 5: Verify + report for human commit**

Run: `mvn verify 2>&1 | tail -3`
Expected: BUILD SUCCESS. Report the diff for human commit.

---
### Task 3: MainApp.showShell, login lands on shell

**Files:**
- Modify: `src/main/java/com/argus/ui/MainApp.java` (add `showShell()`, call it from `onLoginSuccess` instead of `showDashboard()`)
- Modify: `src/main/java/com/argus/ui/controller/DashboardController.java` (logout path already calls `main.logout()`; no change needed — verify by reading)

**Interfaces:**
- Consumes: `ShellController.showDashboard()`, `ShellController.setKeyStatus(String)` from Task 2.
- Produces: shell as the post-login root scene; `showDashboard()` stays for no one — leave it (dead-code removal is cycle 3 with the other old paths).

- [ ] **Step 1: Add showShell, keep the key-status loading**

```java
    public void showShell() throws IOException {
        FXMLLoader l = loader("shell");
        Scene scene = new Scene(l.load(), 1280, 800);
        applyCss(scene);
        ShellController controller = l.getController();
        controller.setMain(this);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(700);
        controller.showDashboard();
        refreshShellKeyStatus(controller);
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
```

`MainApp` already imports `Database`, `Platform`, `SQLException`; add `java.util.Set` and `com.argus.db.ApiKeysDAO`. Thread naming follows the existing `startup-reconcile` pattern.

- [ ] **Step 2: Route login at the shell**

In `onLoginSuccess`, replace `showDashboard()` with `showShell()` (inside the existing try/catch that already shows an ERROR alert on `IOException`).

- [ ] **Step 3: Run the gate**

Run: `mvn verify 2>&1 | tail -3`
Expected: BUILD SUCCESS, 117+ tests (FxmlLoadTest covers the new FXML).

- [ ] **Step 4: Human visual check + report for commit**

Restart (`mvn javafx:run`), log in: sidebar visible, dashboard pane loads in content area, key status line present, window resizes down to 1100x700 minimum. Report the diff for human commit.

---
### Task 4: Mid-scan navigation guard

**Files:**
- Modify: `src/main/java/com/argus/ui/controller/ShellController.java` (confirm dialog when leaving dashboard mid-scan)
- Modify: `src/main/java/com/argus/ui/controller/DashboardController.java` (expose `isScanning()`)

**Interfaces:**
- Consumes: DashboardController scan state.
- Produces: no dialog when idle; CONFIRMATION dialog when a scan runs (existing JAVAFX.md dialog table: CONFIRMATION for cancel mid-scan).

- [ ] **Step 1: Expose scan state**

In `DashboardController`, the `starting` flag plus non-null `runner` mean a scan is live. Add:

```java
    /** True while a scan run owns the coordinator thread. */
    boolean isScanning() {
        return starting || runner != null;
    }
```

(Package-private: same package as `ShellController`. Fields `starting` and `runner` already exist.)

- [ ] **Step 2: Guard the pane swap**

In `ShellController.setPane`, before swapping away from a `DashboardController`:

```java
        if (currentController instanceof DashboardController dash && dash.isScanning()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "A scan is running. Leave it running in the background?");
            confirm.setHeaderText(null);
            var answer = confirm.showAndWait();
            if (answer.isEmpty() || answer.get() != ButtonType.OK) {
                return;
            }
        }
```

Imports to add in `ShellController`: `javafx.scene.control.Alert`, `javafx.scene.control.ButtonType`. Note: leaving does NOT stop the scan — same as today's scene switch; the monitor window (cycle 4) makes it visible.

- [ ] **Step 3: Run the gate**

Run: `mvn verify 2>&1 | tail -3`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Human visual check + report for commit**

Start a scan, click Targets in the sidebar: CONFIRMATION appears; OK navigates (scan continues), Cancel stays. Idle navigation shows no dialog. Report the diff for human commit.
