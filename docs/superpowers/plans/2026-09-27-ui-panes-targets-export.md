# Targets + Export Panes (Cycle B) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Targets and Export live in the shell content area; the sidebar stops ejecting there.

**Architecture:** `TargetsController` and `ExportController` implement `ShellContent` (shutdown on swap), lose their Back buttons (FXML + methods). `ShellController` gains a generic `setPane(view, wire, label)`; `showTargets()`/`showExport()` load panes, sidebar routes to panes. `MainApp.showTargets/showExport` stay untouched for cycle D. Results/Entry Points panes are cycle C, not this plan.

**Tech Stack:** Java 21, JavaFX 21, AtlantaFX 2.1.0, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-27-ui-shell-design.md` (migration row 2).

## Global Constraints

- Java 21, Maven; no new dependencies.
- `com.argus.core` keeps zero `javafx.*` imports.
- All UI updates via `Platform.runLater`; DAO work only on executors; no I/O in `initialize()`.
- FXML with fully-qualified `fx:controller`; `fx:id` camelCase matching `@FXML` fields; controllers created only by FXMLLoader.
- Styling via AtlantaFX classes + `application.css` only; never `setStyle` in controllers.
- ≤200 changed lines for the cycle; human runs all git commands (verify + report instead of commit steps).
- Gate per task: `mvn verify` green.

## Review Focus

- Deleting the Back button from FXML must go with deleting `onBack` — an `onAction="#onBack"` with no method fails the load.
- `onHidden` on a pane with in-flight DAO work must not strand the UI: one-shot loads either finish (harmless `runLater` on detached labels) or die on shutdown; no dialog may open from a dead pane.
- Sidebar Targets/Export while a dashboard scan runs must still warn (the `leavePane` guard keys on the outgoing dashboard, unchanged).
- `setPane` load failure must leave the previous pane displayed with a status-bar message, never a blank content area.
- Export's `FileChooser`/`Alert` owners come from the pane scene — detaching from the old scene must not null the owner (`window()` reads the live scene, works in any host).

---
### Task 1: Targets pane

**Files:**
- Modify: `src/main/java/com/argus/ui/controller/TargetsController.java` (implement `ShellContent`, `onHidden`, executor visibility, delete `onBack`)
- Modify: `src/main/resources/com/argus/ui/view/targets.fxml` (delete Back button)
- Test: create `src/test/java/com/argus/ui/controller/TargetsControllerTest.java`

**Interfaces:**
- Consumes: `ShellContent.onHidden` (exists); `setMain(MainApp)` unchanged.
- Produces: pane-ready `TargetsController` with released threads on swap; `ShellController` (Task 3) calls `setMain` + `onHidden` only.

- [ ] **Step 1: Write the failing test**

```java
package com.argus.ui.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pane swap-out releases the DAO thread (headless: no FX toolkit touched). */
class TargetsControllerTest {

    @Test
    void hiddenTargetsReleasesThreads() {
        TargetsController controller = new TargetsController();
        controller.onHidden();

        assertTrue(controller.executor.isShutdown());
    }

    @Test
    void hiddenTwiceStaysSilent() {
        TargetsController controller = new TargetsController();
        controller.onHidden();

        controller.onHidden(); // second swap: no throw, still shut down
        assertTrue(controller.executor.isShutdown());
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn test -Dtest='TargetsControllerTest'`
Expected: FAIL — compile error (`onHidden` undefined; `executor` is private). Correct RED.

- [ ] **Step 3: Minimal implementation**

In `TargetsController.java`: class declaration → `implements ShellContent`
(same package, no import). Field → package-visible seam:

```java
    /* Package-visible for the shell swap-out test (test-seam precedent). */
    final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
```

Add:

```java
    @Override
    public void onHidden() {
        executor.shutdownNow();
    }
```

Delete `onBack()` entirely:

```java
    @FXML
    private void onBack() {
        try {
            main.showDashboard();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Cannot open dashboard view.").showAndWait();
        }
    }
```

In `targets.fxml`, delete exactly:

```xml
        <Button mnemonicParsing="false" onAction="#onBack" text="Back">
            <AnchorPane.leftAnchor>24.0</AnchorPane.leftAnchor>
            <AnchorPane.bottomAnchor>20.0</AnchorPane.bottomAnchor>
        </Button>
```

(`IOException` import stays — `editCustomPorts` still catches it.)

- [ ] **Step 4: Run to verify it passes**

Run: `mvn test -Dtest='TargetsControllerTest'`
Expected: PASS 1/1.

---
### Task 2: Export pane

**Files:**
- Modify: `src/main/java/com/argus/ui/controller/ExportController.java` (implement `ShellContent`, `onHidden`, worker visibility, delete `onBack`)
- Modify: `src/main/resources/com/argus/ui/view/export.fxml` (delete Back button)
- Test: create `src/test/java/com/argus/ui/controller/ExportControllerTest.java`

**Interfaces:**
- Consumes: `ShellContent.onHidden`; `setMain(MainApp)` unchanged (target list still loads there).
- Produces: pane-ready `ExportController`; progress dialog/FileChooser owners resolve from the live pane scene.

- [ ] **Step 1: Write the failing test**

```java
package com.argus.ui.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pane swap-out releases the worker (headless: no FX toolkit touched). */
class ExportControllerTest {

    @Test
    void hiddenExportReleasesThreads() {
        ExportController controller = new ExportController();
        controller.onHidden();

        assertTrue(controller.worker.isShutdown());
    }

    @Test
    void hiddenTwiceStaysSilent() {
        ExportController controller = new ExportController();
        controller.onHidden();

        controller.onHidden(); // second swap: no throw, still shut down
        assertTrue(controller.worker.isShutdown());
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn test -Dtest='ExportControllerTest'`
Expected: FAIL — compile error (`onHidden` undefined; `worker` is private). Correct RED.

- [ ] **Step 3: Minimal implementation**

In `ExportController.java`: class declaration → `implements ShellContent`.
Field → package-visible seam:

```java
    /* Package-visible for the shell swap-out test (test-seam precedent). */
    final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
```

Add:

```java
    @Override
    public void onHidden() {
        worker.shutdownNow();
    }
```

Delete `onBack()` entirely (same shape as Targets, message
"Cannot open dashboard view."). In `export.fxml`, delete exactly:

```xml
        <Button mnemonicParsing="false" onAction="#onBack" text="Back">
            <AnchorPane.leftAnchor>24.0</AnchorPane.leftAnchor>
            <AnchorPane.bottomAnchor>20.0</AnchorPane.bottomAnchor>
        </Button>
```

(`IOException` import stays — `write()` still catches it.)

- [ ] **Step 4: Run to verify it passes**

Run: `mvn test -Dtest='TargetsControllerTest,ExportControllerTest'`
Expected: PASS 2/2.

---
### Task 3: ShellController hosts both panes

**Files:**
- Modify: `src/main/java/com/argus/ui/controller/ShellController.java` (generic `setPane`, `showTargets`, `showExport`, sidebar routes to panes)
- Test: suite + visual (navigation is eye-verified; FxmlLoadTest covers both FXMLs).

**Interfaces:**
- Consumes: `TargetsController.setMain(MainApp)`, `ExportController.setMain(MainApp)`, `ShellContent.onHidden` (Tasks 1-2); `leavePane()` (exists).
- Produces: sidebar Targets/Export stay inside the shell. `MainApp.showTargets/showExport` go idle (cycle D deletes them).

- [ ] **Step 1: Generalize the swap, add the panes**

Replace `swapDashboard()` with:

```java
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
```

(`PaneWire` is a plain nested interface — no static-record capture problem.)

Rewrite `showDashboard()` body after the guard to:

```java
        select(navDashboard);
        setPane("dashboard", loader -> {
            DashboardController controller = loader.getController();
            controller.setMain(main);
        }, "dashboard");
```

Add:

```java
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
```

Route the sidebar to panes (drop the `throws`, the eject, and the checked exception):

```java
    @FXML
    private void onNavTargets() {
        showTargets();
    }
```

```java
    @FXML
    private void onNavExport() {
        showExport();
    }
```

(`IOException` import stays — `onNavApiKeys` still catches it.)

- [ ] **Step 2: Run the gate**

Run: `mvn verify 2>&1 | tail -3`
Expected: BUILD SUCCESS, 122+ tests (2 new).

- [ ] **Step 3: Human visual glance + report for commit**

Restart, log in: click Targets → pane inside the shell, sidebar stays,
highlight moves; add/delete a target works. Click Export → pane, run an
export, completion alert appears. Start a scan, click Targets → guard
dialog still warns. Report the diff for human commit.

---
### After this plan

Cycle C (Results + Entry Points panes, KEV tooltip, detail strip) and
cycle D (delete legacy `showX` scene-switches) each get their own plan.
This plan is done when Task 3's gate + visual check pass.
