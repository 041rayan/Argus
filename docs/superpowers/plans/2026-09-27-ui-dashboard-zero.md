# Dashboard From Zero (Cycle A) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dashboard rebuilt as a card layout (scan command, stat cards, charts); legacy nav buttons gone; sidebar owns navigation.

**Architecture:** `dashboard.fxml` becomes a `ScrollPane` > `VBox`: command card (target picker, Scan/Cancel, progress, status), stat-cards row (5 labels), charts row (2 `BarChart`s). `DashboardController` keeps scan ownership, drops nav methods, loads stats/charts for the latest COMPLETED scan on a worker. API Keys stays reachable via a new sidebar button (shell owns nav now).

**Tech Stack:** Java 21, JavaFX 21 (`BarChart`, `CategoryAxis`, `NumberAxis`), AtlantaFX 2.1.0 (`accent`, `title-2`, `text-muted`), JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-27-ui-shell-design.md` (Section 2; migration row 1).

## Global Constraints

- Java 21, Maven; no new dependencies.
- `com.argus.core` keeps zero `javafx.*` imports.
- All UI updates via `Platform.runLater`; DAO work only on `worker`; no I/O in `initialize()`.
- FXML with fully-qualified `fx:controller`; `fx:id` camelCase matching `@FXML` fields; controllers created only by FXMLLoader.
- Styling via AtlantaFX classes + `application.css` only; never `setStyle` in controllers.
- ≤200 changed lines for the cycle; human runs all git commands (verify + report instead of commit steps).
- Gate per task: `mvn verify` green.

## Review Focus

- An empty database (no scans, no targets) must show zeroes and "No scans yet", never a stack trace or blank cards.
- A RUNNING scan must not be picked as the stats source; stats follow the latest COMPLETED scan only.
- Chart rebuilds happen on scan selection change and ScanFinished, never per scan event (100 ms batch rule spirit: no per-event chart churn).
- Deleting the legacy nav methods must not strand the API Keys dialog — the sidebar button is its new home, wired in Task 1.
- A wrong `fx:id` fails at load with a clear exception, but a wrong style class fails silently — the human visual glance per task is the gate for styling.

---
### Task 1: dashboard.fxml from zero + API Keys sidebar button

**Files:**
- Create (overwrite): `src/main/resources/com/argus/ui/view/dashboard.fxml`
- Modify: `src/main/resources/com/argus/ui/view/shell.fxml` (add `navApiKeys` button)
- Modify: `src/main/java/com/argus/ui/controller/ShellController.java` (add `onNavApiKeys`, include button in `select()`)
- Test: `src/test/java/com/argus/ui/FxmlLoadTest.java` (existing `dashboardLoads` + `shellLoads` cover it)

**Interfaces:**
- Consumes: `ShellController` fields from cycle 1; `MainApp.showAddApiKey()` (exists, used by dashboard today).
- Produces: `fx:id`s `scanTargetCombo`, `scanButton`, `scanProgress`, `scanStatusLabel`, `statScans`, `statHosts`, `statPorts`, `statKev`, `statVt`, `severityChart`, `portsChart` — exact names Task 2-4 wire.

- [ ] **Step 1: Write the new dashboard.fxml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.chart.BarChart?>
<?import javafx.scene.chart.CategoryAxis?>
<?import javafx.scene.chart.NumberAxis?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.ComboBox?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.ProgressBar?>
<?import javafx.scene.control.ScrollPane?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.VBox?>
<ScrollPane fitToWidth="true" styleClass="root-pane"
            xmlns="http://javafx.com/javafx/21.0.8"
            xmlns:fx="http://javafx.com/fxml/1"
            fx:controller="com.argus.ui.controller.DashboardController">
    <content>
        <VBox spacing="16.0" style="-fx-padding: 24;">
            <children>
                <Label styleClass="title-label" text="Dashboard"/>
                <VBox spacing="8.0">
                    <children>
                        <Label styleClass="text-muted" text="SCAN"/>
                        <HBox alignment="CENTER_LEFT" spacing="8.0">
                            <children>
                                <ComboBox fx:id="scanTargetCombo" prefHeight="32.0"
                                          prefWidth="300.0" promptText="Target"/>
                                <Button fx:id="scanButton" mnemonicParsing="false"
                                        onAction="#onScan" prefHeight="32.0"
                                        styleClass="accent" text="Scan"/>
                            </children>
                        </HBox>
                        <ProgressBar fx:id="scanProgress" maxWidth="Infinity"
                                     prefHeight="12.0" progress="0"/>
                        <Label fx:id="scanStatusLabel" styleClass="status-label" text=""/>
                    </children>
                </VBox>
                <HBox spacing="16.0">
                    <children>
                        <VBox spacing="2.0">
                            <children>
                                <Label styleClass="text-muted" text="SCANS"/>
                                <Label fx:id="statScans" styleClass="title-2" text="–"/>
                            </children>
                        </VBox>
                        <VBox spacing="2.0">
                            <children>
                                <Label styleClass="text-muted" text="ALIVE HOSTS"/>
                                <Label fx:id="statHosts" styleClass="title-2" text="–"/>
                            </children>
                        </VBox>
                        <VBox spacing="2.0">
                            <children>
                                <Label styleClass="text-muted" text="OPEN PORTS"/>
                                <Label fx:id="statPorts" styleClass="title-2" text="–"/>
                            </children>
                        </VBox>
                        <VBox spacing="2.0">
                            <children>
                                <Label styleClass="text-muted" text="KEV FINDINGS"/>
                                <Label fx:id="statKev" styleClass="title-2" text="–"/>
                            </children>
                        </VBox>
                        <VBox spacing="2.0">
                            <children>
                                <Label styleClass="text-muted" text="VT-FLAGGED"/>
                                <Label fx:id="statVt" styleClass="title-2" text="–"/>
                            </children>
                        </VBox>
                    </children>
                </HBox>
                <HBox spacing="16.0">
                    <children>
                        <BarChart fx:id="severityChart" animated="false" legendVisible="false"
                                  prefHeight="260.0" prefWidth="420.0" title="Findings by severity">
                            <xAxis>
                                <CategoryAxis label="Severity"/>
                            </xAxis>
                            <yAxis>
                                <NumberAxis label="Count"/>
                            </yAxis>
                        </BarChart>
                        <BarChart fx:id="portsChart" animated="false" legendVisible="false"
                                  prefHeight="260.0" prefWidth="420.0" title="Top ports">
                            <xAxis>
                                <CategoryAxis label="Port"/>
                            </xAxis>
                            <yAxis>
                                <NumberAxis label="Hosts"/>
                            </yAxis>
                        </BarChart>
                    </children>
                </HBox>
            </children>
        </VBox>
    </content>
</ScrollPane>
```

- [ ] **Step 2: Sidebar API Keys button**

In `shell.fxml`, after the Export button add:

```xml
                <Button fx:id="navApiKeys" maxWidth="Infinity" mnemonicParsing="false"
                        onAction="#onNavApiKeys" styleClass="flat" text="API Keys"/>
```

In `ShellController.java`: add `@FXML private Button navApiKeys;`, add it to the `select()` list, add:

```java
    @FXML
    private void onNavApiKeys() {
        try {
            main.showAddApiKey();
        } catch (IOException e) {
            statusBarLabel.setText("Cannot open API keys dialog.");
        }
    }
```

(`main.showAddApiKey()` and `IOException` already exist in that file.)

- [ ] **Step 3: Run the gate**

Run: `mvn verify 2>&1 | tail -3`
Expected: BUILD SUCCESS. `dashboardLoads` + `shellLoads` prove the FXML wires (right controller, valid `fx:id`s). Controller still references deleted `fx:id`s (`statusLabel`, `apiKeysLabel`, `logoutButton`) — unused `@FXML` fields stay null, harmless; Task 2 removes them.

- [ ] **Step 4: Human visual glance + report for commit**

Restart, log in: card layout visible, sidebar has API Keys, old nav buttons gone. Report the diff for human commit.

---
### Task 2: Controller — drop legacy chrome, load stats

**Files:**
- Modify: `src/main/java/com/argus/ui/controller/DashboardController.java`
- Test: none new (wiring + worker loads; suite + visual gate). The stats math it feeds is Task 3's tested unit.

**Interfaces:**
- Consumes: Task 1 `fx:id`s; `ShellController`/`ShellContent`/`MainApp`/`EventBus`/`ScanRunner` (unchanged); `ScanDAO.list()`, `HostDAO/PortDAO/FindingDAO.listByScan(long)` (all exist).
- Produces: `refreshStats()` (Task 4 calls it on ScanFinished); `DashboardStats` + `summarize()` (Task 3).

- [ ] **Step 1: Replace the field block**

```java
    @FXML
    private ComboBox<Target> scanTargetCombo;

    @FXML
    private Button scanButton;

    @FXML
    private ProgressBar scanProgress;

    @FXML
    private Label scanStatusLabel;

    @FXML
    private Label statScans;
    @FXML
    private Label statHosts;
    @FXML
    private Label statPorts;
    @FXML
    private Label statKev;
    @FXML
    private Label statVt;

    @FXML
    private BarChart<String, Number> severityChart;
    @FXML
    private BarChart<String, Number> portsChart;
```

Delete: `statusLabel`, `logoutButton`, `apiKeysLabel` fields. Delete methods
`onShowTargets`, `onApiKeys`, `onShowResults`, `onExport`, `onLogout` entirely.

- [ ] **Step 2: setMain loads targets + stats, no key line**

```java
    /** Wired by MainApp after the FXML load — data arrives through setters. */
    public void setMain(MainApp main) {
        this.main = main;
        scanStatusLabel.setText("Ready — pick a target and scan.");
        worker.execute(() -> {
            try {
                List<Target> targets = new TargetDAO(Database.inUserHome()).list();
                Platform.runLater(() -> {
                    scanTargetCombo.getItems().setAll(targets);
                    if (targets.isEmpty()) {
                        scanTargetCombo.setPromptText("No targets — add one in Targets");
                    }
                });
            } catch (SQLException e) {
                Platform.runLater(() -> scanTargetCombo.setPromptText("Database failure."));
            }
        });
        refreshStats();
    }

    /** Latest COMPLETED scan's cards + charts, off the FX thread. */
    void refreshStats() {
        worker.execute(() -> {
            try {
                Database db = Database.inUserHome();
                List<ScanSummary> scans = new ScanDAO(db).list();
                ScanSummary latest = latestCompleted(scans);
                if (latest == null || latest.id() == null) {
                    Platform.runLater(this::showEmptyStats);
                    return;
                }
                DashboardStats stats = summarize(
                        new HostDAO(db).listByScan(latest.id()),
                        new PortDAO(db).listByScan(latest.id()),
                        new FindingDAO(db).listByScan(latest.id()));
                Platform.runLater(() -> showStats(scans.size(), stats));
            } catch (SQLException e) {
                LOG.warn("dashboard stats failed: {}", e.getMessage());
                Platform.runLater(this::showEmptyStats);
            }
        });
    }

    private static ScanSummary latestCompleted(List<ScanSummary> scans) {
        for (ScanSummary s : scans) {
            if (s.status() == ScanSummary.Status.COMPLETED) {
                return s;
            }
        }
        return null;
    }

    private void showEmptyStats() {
        statScans.setText("0");
        statHosts.setText("0");
        statPorts.setText("0");
        statKev.setText("0");
        statVt.setText("0");
        scanStatusLabel.setText("No scans yet — pick a target and scan.");
    }

    private void showStats(int scanCount, DashboardStats stats) {
        statScans.setText(String.valueOf(scanCount));
        statHosts.setText(String.valueOf(stats.aliveHosts()));
        statPorts.setText(String.valueOf(stats.openPorts()));
        statKev.setText(String.valueOf(stats.kev()));
        statVt.setText(String.valueOf(stats.vt()));
        feedCharts(stats);
    }
```

`feedCharts` is Task 4. Add imports: `com.argus.core.model.ScanSummary`,
`com.argus.db.FindingDAO`, `com.argus.db.HostDAO`, `com.argus.db.PortDAO`,
`com.argus.db.ScanDAO`, `javafx.scene.chart.BarChart`,
`javafx.scene.control.ProgressBar`; remove `com.argus.db.TargetDAO`
only if unused — it is still used (targets load above), keep it. Remove
`javafx.scene.control.ListCell` and `javafx.util.StringConverter` (delete the
`initialize()` cell-factory/converter block entirely — the combo uses default
rendering now; empty-list prompt is set in `setMain`).

- [ ] **Step 3: Run the gate**

Run: `mvn verify 2>&1 | tail -3`
Expected: COMPILE FAILURE (`feedCharts` undefined) — correct RED for Task 4's arrival. If anything else fails, fix now.

- [ ] **Step 4: Report (no commit — Task 4 completes the unit)**

Report the RED as Expected; do not commit a broken tree.

---
### Task 3: summarize() + headless tests (TDD)

**Files:**
- Modify: `src/main/java/com/argus/ui/controller/DashboardController.java` (add record + function)
- Test: `src/test/java/com/argus/ui/controller/DashboardControllerTest.java` (append)

**Interfaces:**
- Consumes: `Host`, `PortResult`, `Finding` records (core model: `alive()`, `open()`, `port()`, `host()`, `type()`, `severity()`).
- Produces: `DashboardStats` + `summarize()` used by Task 2's `showStats`.

- [ ] **Step 1: Write the failing tests**

```java
    @Test
    void summarizeCountsCardsAndCharts() {
        var hosts = List.of(
                new Host(1, 7, "a.example.com", "203.0.113.7", true, "", "", ""),
                new Host(2, 7, "dead.example.com", "", false, "", "", ""));
        var ports = List.of(
                new PortResult("a.example.com", 80, "tcp", "http", "", "", "", true),
                new PortResult("a.example.com", 443, "tcp", "https", "", "", "", true),
                new PortResult("a.example.com", 22, "tcp", "ssh", "", "", "", false));
        var findings = List.of(
                new Finding(0, null, "kev", "KEV_MATCH", "HIGH", "{}"),
                new Finding(0, null, "virustotal", "VT_FLAGGED", "MEDIUM", "{}"));

        DashboardController.DashboardStats stats =
                DashboardController.summarize(hosts, ports, findings);

        assertEquals(1, stats.aliveHosts());
        assertEquals(2, stats.openPorts(), "closed port excluded");
        assertEquals(1, stats.kev());
        assertEquals(1, stats.vt());
        assertEquals(Map.of("HIGH", 1, "MEDIUM", 1), stats.bySeverity());
        assertEquals(Set.of("a.example.com"), stats.topPorts().get(80));
    }

    @Test
    void summarizeEmptyScanIsZeroes() {
        DashboardController.DashboardStats stats =
                DashboardController.summarize(List.of(), List.of(), List.of());

        assertEquals(0, stats.aliveHosts());
        assertEquals(0, stats.openPorts());
        assertTrue(stats.bySeverity().isEmpty());
        assertTrue(stats.topPorts().isEmpty());
    }
```

Host constructor: verify against `src/main/java/com/argus/core/model/Host.java`
before writing (field order). Imports to add in the test: `com.argus.core.model.Finding`,
`com.argus.core.model.Host`, `com.argus.core.model.PortResult`, `java.util.List`,
`java.util.Map`, `java.util.Set`, `assertEquals`, `assertTrue`.

- [ ] **Step 2: Run to verify they fail**

Run: `mvn test -Dtest='DashboardControllerTest'`
Expected: FAIL — compile error (`summarize`/`DashboardStats` do not exist). Correct RED.

- [ ] **Step 3: Add the unit**

```java
    /** Pure summary of one scan for the stat cards and charts (headless-testable). */
    record DashboardStats(int aliveHosts, int openPorts, int kev, int vt,
                          Map<String, Integer> bySeverity,
                          Map<Integer, Set<String>> topPorts) {
    }

    static DashboardStats summarize(List<Host> hosts, List<PortResult> ports,
                                    List<Finding> findings) {
        int alive = 0;
        for (Host h : hosts) {
            if (h.alive()) {
                alive++;
            }
        }
        int kev = 0;
        int vt = 0;
        Map<String, Integer> bySev = new TreeMap<>();
        for (Finding f : findings) {
            if ("KEV_MATCH".equals(f.type())) {
                kev++;
            } else if ("VT_FLAGGED".equals(f.type())) {
                vt++;
            }
            bySev.merge(f.severity(), 1, Integer::sum);
        }
        Map<Integer, Set<String>> byPort = new HashMap<>();
        for (PortResult p : ports) {
            if (p.open()) {
                byPort.computeIfAbsent(p.port(), k -> new HashSet<>()).add(p.host());
            }
        }
        return new DashboardStats(alive, byPort.values().stream()
                .mapToInt(Set::size).sum(), kev, vt, bySev, byPort);
    }
```

Add imports: `com.argus.core.model.Finding`, `com.argus.core.model.Host`,
`com.argus.core.model.PortResult`, `java.util.HashMap`, `java.util.HashSet`,
`java.util.Map`, `java.util.TreeMap` (`List`/`Set` already imported).

- [ ] **Step 4: Run to verify they pass**

Run: `mvn test -Dtest='DashboardControllerTest'`
Expected: PASS (4/4: 2 swap-out + 2 summarize). If the Host constructor
guesses were wrong, fix field order from the compile error and re-run.

---
### Task 4: Charts feed, progress bar, finish refresh

**Files:**
- Modify: `src/main/java/com/argus/ui/controller/DashboardController.java`
- Test: suite + visual (chart pixels are eye-verified).

**Interfaces:**
- Consumes: `showStats()` (Task 2), `DashboardStats.topPorts/bySeverity` (Task 3).

- [ ] **Step 1: Feed the charts (axes/labels are FXML attrs from Task 1)**

```java
    private void feedCharts(DashboardStats stats) {
        XYChart.Series<String, Number> sev = new XYChart.Series<>();
        for (var e : stats.bySeverity().entrySet()) {
            sev.getData().add(new XYChart.Data<>(e.getKey(), e.getValue()));
        }
        severityChart.setData(FXCollections.observableArrayList(sev));

        XYChart.Series<String, Number> ports = new XYChart.Series<>();
        stats.topPorts().entrySet().stream()
                .sorted(Map.Entry.<Integer, Set<String>>comparingByValue(
                        Comparator.comparingInt(Set::size)).reversed())
                .limit(8)
                .forEach(e -> ports.getData().add(new XYChart.Data<>(
                        String.valueOf(e.getKey()), e.getValue().size())));
        portsChart.setData(FXCollections.observableArrayList(ports));
    }
```

Add imports: `javafx.scene.chart.XYChart`, `javafx.collections.FXCollections`,
`java.util.Comparator`.

- [ ] **Step 2: Progress bar follows the scan**

In `onScan()`, after `scanButton.setText("Cancel")` add
`scanProgress.setProgress(-1);` (indeterminate). In the `ScanFinished`
branch of `onScanEvent`, after `scanButton.setText("Scan")` add
`scanProgress.setProgress(1);` then call `refreshStats();` (stats follow
the finished scan). PortFound/HostFound branches stay text-only (no
per-event chart churn per Review Focus).

- [ ] **Step 3: Run the gate**

Run: `mvn verify 2>&1 | tail -3`
Expected: BUILD SUCCESS, 120+ tests (2 new summarize tests).

- [ ] **Step 4: Human visual glance + report for commit**

Restart, log in: command card with working Scan, 5 stat cards with real
numbers from the latest COMPLETED scan, both charts drawn, progress bar
indeterminate during a scan and full after. Empty-DB case (fresh profile)
shows zeroes + "No scans yet". Report the diff for human commit.

---
### After this plan

Cycles B (Targets + Export panes), C (Results + Entry Points panes),
D (delete legacy scene-switches) each get their own plan. This plan is
done when Task 4's gate + visual check pass.