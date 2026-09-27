# UI Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every main window opens at 1280x720, every view is rebuilt on one
`ag-*` token sheet, and the Results, Entry points and Targets panes get a
detail inspector, with buttons in the same place in every view.

**Architecture:** One CSS token layer replaces the 20 line
`application.css`. Every FXML is rebuilt on `BorderPane`/`VBox`/`HBox`
containers using `ag-*` classes, never inline `style=` and never
`AnchorPane` coordinates. Controllers keep their logic; the only new
Java is two pure helpers (`HostDetail`, two `ExportService` methods) so
no controller parses `detail_json` and both are unit testable headless.
`MainApp.applyGeometry` is the single place that sets window size.

**Tech Stack:** Java 21, JavaFX 21.0.8, FXMLLoader, AtlantaFX Primer Dark
2.1.0, JUnit 5.10.2, Maven. No new dependency.

**Spec:** `docs/superpowers/specs/2026-09-27-ui-overhaul-design.md`

**Baseline:** `mvn verify` is green on `main` at commit `3fc8394` before
Task 1. Every task ends with `mvn verify` green.

## Global Constraints

- Window geometry: 1280x720, minimum 1100x640, resizable. One place:
  `MainApp.applyGeometry(Scene)`. The API Keys window sizes to content.
- Every Argus style class is namespaced `ag-` and lives in
  `application.css`. No `setStyle` in controllers, no `style=` in FXML.
- No new Maven dependency. `com.argus.core` keeps zero `javafx.*` imports.
- UI updates only through `Platform.runLater`. Workers stay daemon
  threads with purpose-based names (`results-worker`, `target-dao`).
- SLF4J only, no `System.out`, no secrets logged.
- SQL through existing DAOs only. No schema change, no new query.
- Prepaint copy is authored already uppercase in the FXML text
  ("USERNAME", "NETWORK"): JavaFX CSS has no `text-transform`.
- JavaFX CSS has no letter-spacing. The wordmark and status bar use
  `-fx-font-family: monospace` instead.
- Panes that read no session state keep the `setMain(MainApp)`
  parameter as the uniform seam and drop the field.
- **Git actions belong to the human (SESSION.md section 7).** No task
  commits. Each task ends with the commit message to hand over.

## Review Focus

Five input classes the spec implies but no view test exercises. The
first three are pinned by a test in the task that owns the code; the
last two are layout and are pinned by the manual click path, because a
headless layout assertion cannot see a clipped node.

1. **A finding with null or corrupt `detail_json`.** The Entry points
   inspector must show the finding, not throw. Pinned by
   `findingOutsForHandlesUnreadableDetail` in Task 6.
2. **A scan whose hosts have no ports and no findings** (fresh scan, or
   every host dead). The Results inspector shows placeholders and an
   empty port and finding list, never a stale host's rows. Pinned by
   `emptyDetailHasNothingToShow` in Task 6.
3. **Switching scan while a row is selected.** The inspector must not
   keep the previous scan's detail. Pinned by clearing the inspector in
   the same `runLater` that replaces the rows, reviewed in Task 6.
4. **The window narrowed to the 1100x640 minimum.** No clipped content,
   the table still has height. Pinned by `LayoutFloorTest` in Task 7
   plus the manual click path in every task.
5. **A `ag-*` class typo in an FXML.** JavaFX drops the rule silently and
   the control renders unstyled. Pinned by `CssContractTest` in Task 1
   (both directions: every used class defined, every defined class used).

## File Structure

| File | Responsibility after this plan |
|---|---|
| `ui/view/application.css` | The token sheet. Every `ag-*` rule, nothing else |
| `ui/view/mark.fxml` | The eye mark: three shapes, no controller |
| `ui/view/{login,lock}.fxml` | Auth split frame |
| `ui/view/shell.fxml` | Sidebar, content StackPane, status bar |
| `ui/view/{dashboard,targets,export,results,entrypoints}.fxml` | Shell panes |
| `ui/view/apikeys.fxml` | Content sized dialog |
| `ui/MainApp.java` | Stage, session, `applyGeometry`, scene switches only |
| `ui/controller/ShellController.java` | Nav selection and pane swapping only |
| `ui/controller/ResultsController.java` | Scan/host table, inspector from `HostDetail` |
| `ui/controller/EntryPointsController.java` | Ranked ports, inspector from `ExportService` |
| `ui/controller/TargetsController.java` | Add form, table, target inspector |
| `ui/HostDetail.java` (new) | Pure: ports and findings grouped per host subdomain |
| `core/export/ExportService.java` | Plus `hostPortOf` and `findingOutsFor` |
| `test/java/com/argus/ui/CssContractTest.java` (new) | FXML and Java `ag-*` usage versus CSS definitions |
| `test/java/com/argus/ui/WindowGeometryTest.java` (new) | The four geometry constants |
| `test/java/com/argus/ui/LayoutFloorTest.java` (new) | Every pane laid out at 880x612 still has height |
| `test/java/com/argus/ui/HostDetailTest.java` (new) | Grouping, empty inputs, line formatting |
| `test/java/com/argus/ui/FxmlLoadTest.java` | All ten FXMLs load, plus the `onFx` helper |

---

### Task 1: Design token sheet, class contract, mark

The whole overhaul rests on this task: if the token sheet and the
contract test are wrong, every later FXML inherits the error.

**Files:**
- Modify: `src/main/resources/com/argus/ui/view/application.css` (rewrite, currently 20 lines)
- Create: `src/main/resources/com/argus/ui/view/mark.fxml`
- Test: `src/test/java/com/argus/ui/CssContractTest.java` (new)
- Test: `src/test/java/com/argus/ui/FxmlLoadTest.java` (add `markLoads`)

**Interfaces:**
- Consumes: nothing. First task.
- Produces: the 38 `ag-*` class names below. Every later task must use
  exactly these names, no others. The inventory is
  `ag-content ag-pane ag-scroll ag-chrome ag-brand-panel ag-card
  ag-inspector ag-tile ag-statusbar ag-rule ag-view-title ag-wordmark
  ag-tagline ag-sub ag-muted ag-faint ag-field-label ag-mono
  ag-stat-value ag-stat-label ag-key ag-value ag-placeholder
  ag-status-ok ag-status-warn ag-status-danger ag-status-info ag-primary
  ag-secondary ag-danger ag-link ag-nav ag-nav-selected ag-field
  ag-search ag-table ag-kev ag-list ag-chart ag-series ag-mark-eye
  ag-mark-pupil`. Plus the pre-existing `root-pane`, which stays.
  The contract test fails the build on a name outside this list, so
  adding a class means adding it here, in the CSS, and using it.

- [ ] **Step 1: Write the failing contract test**

Create `src/test/java/com/argus/ui/CssContractTest.java`:

```java
package com.argus.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ag-* namespace is a contract, not a convention (spec
 * 2026-09-27-ui-overhaul-design.md). A CSS class typo fails silently at
 * runtime, so both directions are checked: nothing may use a class the
 * sheet does not define, and the sheet carries no dead rule.
 */
class CssContractTest {

    /** The ten FXMLs the app ships. Listed, not scanned: a jar hides a directory. */
    private static final List<String> FXML = List.of(
            "mark", "login", "lock", "shell", "dashboard",
            "targets", "export", "results", "entrypoints", "apikeys");

    private static final Pattern AG = Pattern.compile("\\b(ag-[a-z0-9-]+)\\b");

    @Test
    void everyUsedClassIsDefined() throws IOException {
        Set<String> defined = cssClasses();
        Set<String> used = usedClasses();

        Set<String> missing = new LinkedHashSet<>(used);
        missing.removeAll(defined);

        assertEquals(Set.of(), missing, "ag-* class used but not in application.css");
    }

    @Test
    void everyDefinedClassIsUsed() throws IOException {
        Set<String> used = usedClasses();
        Set<String> dead = new LinkedHashSet<>(cssClasses());
        dead.removeAll(used);

        assertEquals(Set.of(), dead, "ag-* class in application.css but never used");
    }

    @Test
    void theSheetIsNotEmpty() throws IOException {
        assertTrue(cssClasses().size() >= 38,
                "the token sheet lost classes, expected at least 38, got " + cssClasses());
    }

    /** Every .ag-* selector in the sheet. */
    private static Set<String> cssClasses() throws IOException {
        URL css = MainApp.class.getResource("/com/argus/ui/view/application.css");
        assertTrue(css != null, "application.css is missing from the resources");
        return matches(Files.readString(Path.of(css.toURI())));
    }

    /** Every ag-* token used by an FXML or by a ui controller. */
    private static Set<String> usedClasses() throws IOException {
        Set<String> used = new LinkedHashSet<>();
        for (String name : FXML) {
            used.addAll(matches(resource("/com/argus/ui/view/" + name + ".fxml")));
        }
        Path ui = Path.of("src/main/java/com/argus/ui");
        assertTrue(Files.isDirectory(ui), "ui sources not found at " + ui.toAbsolutePath());
        try (var walk = Files.walk(ui)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            used.addAll(matches(Files.readString(p)));
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    });
        }
        return used;
    }

    private static Set<String> matches(String text) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = AG.matcher(text);
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    private static String resource(String path) throws IOException {
        URL url = MainApp.class.getResource(path);
        assertTrue(url != null, "missing resource: " + path);
        return new String(url.openStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `mvn -q test -Dtest=CssContractTest`
Expected: FAIL. `theSheetIsNotEmpty` fails (the current sheet has no `ag-*`
rule) and `everyDefinedClassIsUsed` cannot even run yet.

- [ ] **Step 3: Write the token sheet**

Replace `src/main/resources/com/argus/ui/view/application.css` with:

```css
/*
 * application.css - Argus design tokens.
 *
 * Layered after the AtlantaFX Primer Dark user-agent stylesheet, so it
 * overrides the theme rather than replacing it. Every Argus rule is
 * namespaced ag-* : CssContractTest proves the FXMLs and the ui
 * controllers use nothing else and that nothing here is dead.
 *
 * House rules (spec 2026-09-27-ui-overhaul-design.md):
 *   no setStyle in controllers, no style= in FXML
 *   one primary per view, destructive never adjacent to the primary
 *   6 radius on controls, 8 on cards, 34 tall in the shell, 44 in auth
 */

.root-pane { -fx-background-color: #0d1117; }

/* ---------- chrome ---------- */
.ag-content { -fx-background-color: #0d1117; }
.ag-pane { -fx-background-color: #0d1117; -fx-padding: 26 26 22 26; }
.ag-scroll {
    -fx-background-color: #0d1117;
    -fx-background-radius: 0;
    -fx-border-color: transparent;
    -fx-padding: 0;
}
.ag-chrome { -fx-background-color: #10151c; }
.ag-brand-panel {
    -fx-background-color: #10151c;
    -fx-border-color: transparent #21262d transparent transparent;
}
.ag-card {
    -fx-background-color: #11161d;
    -fx-border-color: #21262d;
    -fx-border-radius: 8;
    -fx-padding: 18;
}
.ag-inspector {
    -fx-background-color: #11161d;
    -fx-border-color: #21262d;
    -fx-border-radius: 8;
    -fx-padding: 18;
}
.ag-tile {
    -fx-background-color: #11161d;
    -fx-border-color: #21262d;
    -fx-border-radius: 8;
    -fx-padding: 14 16 14 16;
}
.ag-statusbar {
    -fx-background-color: #10151c;
    -fx-border-color: #21262d transparent transparent transparent;
    -fx-padding: 0 26 0 26;
}
.ag-rule {
    -fx-background-color: #21262d;
    -fx-max-height: 1;
    -fx-pref-height: 1;
    -fx-opacity: 1;
}

/* ---------- type ---------- */
.ag-view-title { -fx-font-size: 20px; -fx-font-weight: 600; -fx-text-fill: #e6edf3; }
.ag-wordmark {
    -fx-font-family: monospace;
    -fx-font-size: 15px;
    -fx-font-weight: 700;
    -fx-text-fill: #e6edf3;
}
.ag-tagline { -fx-font-size: 15px; -fx-text-fill: #8b949e; }
.ag-sub { -fx-font-size: 13px; -fx-text-fill: #8b949e; }
.ag-muted { -fx-font-size: 12.5px; -fx-text-fill: #8b949e; }
.ag-faint { -fx-font-size: 11px; -fx-text-fill: #6e7681; }
.ag-field-label { -fx-font-size: 11px; -fx-text-fill: #8b949e; }
.ag-mono {
    -fx-font-family: monospace;
    -fx-font-size: 11.5px;
    -fx-text-fill: #6e7681;
}
.ag-stat-value { -fx-font-size: 22px; -fx-font-weight: 600; -fx-text-fill: #e6edf3; }
.ag-stat-label { -fx-font-size: 11px; -fx-text-fill: #6e7681; }
.ag-key { -fx-font-family: monospace; -fx-font-size: 12px; }
.ag-value { -fx-font-size: 12.5px; -fx-text-fill: #c9d1d9; }
.ag-placeholder { -fx-font-size: 12.5px; -fx-font-style: italic; -fx-text-fill: #6e7681; }

/* ---------- status ---------- */
.ag-status-ok { -fx-text-fill: #3fb950; }
.ag-status-warn { -fx-text-fill: #d29922; }
.ag-status-danger { -fx-text-fill: #ff7b72; }
.ag-status-info { -fx-text-fill: #58a6ff; }

/* ---------- buttons ---------- */
.ag-primary {
    -fx-background-color: #238636;
    -fx-border-color: #2ea043;
    -fx-background-radius: 6;
    -fx-border-radius: 6;
    -fx-text-fill: white;
    -fx-font-size: 14px;
    -fx-font-weight: 600;
    -fx-cursor: hand;
}
.ag-primary:disabled { -fx-opacity: 0.45; }
.ag-secondary {
    -fx-background-color: #161b22;
    -fx-border-color: #30363d;
    -fx-background-radius: 6;
    -fx-border-radius: 6;
    -fx-text-fill: #c9d1d9;
    -fx-font-size: 13px;
    -fx-cursor: hand;
}
.ag-secondary:disabled { -fx-opacity: 0.45; }
.ag-danger {
    -fx-background-color: #b62324;
    -fx-border-color: #d04545;
    -fx-background-radius: 6;
    -fx-border-radius: 6;
    -fx-text-fill: white;
    -fx-font-size: 13px;
    -fx-font-weight: 600;
    -fx-cursor: hand;
}
.ag-danger:disabled { -fx-opacity: 0.45; }
.ag-link {
    -fx-background-color: transparent;
    -fx-border-color: transparent;
    -fx-text-fill: #58a6ff;
    -fx-font-size: 13px;
    -fx-cursor: hand;
}
.ag-nav {
    -fx-background-color: transparent;
    -fx-border-color: transparent;
    -fx-background-radius: 6;
    -fx-text-fill: #8b949e;
    -fx-font-size: 13.5px;
    -fx-alignment: center-left;
    -fx-cursor: hand;
}
.ag-nav:hover { -fx-background-color: #161b22; }
.ag-nav-selected {
    -fx-background-color: #1b2430;
    -fx-text-fill: #e6edf3;
    -fx-font-weight: 500;
    -fx-border-color: transparent transparent transparent #58a6ff;
    -fx-border-width: 0 0 0 2;
}
.ag-nav-selected:hover { -fx-background-color: #1b2430; }

/* ---------- inputs ---------- */
.ag-field { -fx-font-size: 13px; -fx-text-fill: #e6edf3; -fx-pref-height: 38; }
.ag-search { -fx-font-size: 13px; -fx-text-fill: #e6edf3; -fx-pref-height: 34; }

/* ---------- table ---------- */
.ag-table {
    -fx-background-color: #0d1117;
    -fx-border-color: #21262d;
    -fx-table-cell-border-color: transparent;
}
.ag-table .column-header-background { -fx-background-color: #10151c; }
.ag-table .column-header {
    -fx-border-color: transparent transparent #21262d transparent;
    -fx-size: 30;
}
.ag-table .column-header .label {
    -fx-font-size: 10.5px;
    -fx-text-fill: #6e7681;
    -fx-alignment: center-left;
}
.ag-table .table-cell { -fx-border-color: transparent; -fx-cell-size: 34; }
.ag-table .table-row-cell {
    -fx-font-size: 12.5px;
    -fx-text-fill: #c9d1d9;
    -fx-border-color: transparent transparent #161b22 transparent;
}
.ag-table .table-row-cell:filled:selected,
.ag-table .table-row-cell:selected { -fx-background-color: #1b2430; }
.ag-kev {
    -fx-font-family: monospace;
    -fx-font-size: 11px;
    -fx-text-fill: #8b949e;
}

/* ---------- list ---------- */
.ag-list {
    -fx-background-color: transparent;
    -fx-border-color: #21262d;
    -fx-border-radius: 6;
    -fx-padding: 4;
}
.ag-list .list-cell {
    -fx-font-family: monospace;
    -fx-font-size: 11.5px;
    -fx-text-fill: #c9d1d9;
    -fx-background-color: transparent;
    -fx-padding: 4 6 4 6;
}
.ag-list .list-cell:filled:selected { -fx-background-color: #1b2430; }

/* ---------- charts ---------- */
.ag-chart {
    -fx-background-color: #11161d;
    -fx-border-color: #21262d;
    -fx-border-radius: 8;
    -fx-padding: 12;
}
.ag-chart .chart-title { -fx-font-size: 13px; -fx-text-fill: #e6edf3; }
.ag-chart .axis { -fx-tick-label-fill: #8b949e; -fx-font-size: 11px; }
.ag-chart .axis-label { -fx-text-fill: #6e7681; -fx-font-size: 11px; }
.ag-series { -fx-bar-fill: #58a6ff; }

/* ---------- the mark ---------- */
.ag-mark-eye { -fx-fill: transparent; -fx-stroke: #58a6ff; -fx-stroke-width: 1.7; }
.ag-mark-pupil { -fx-fill: #58a6ff; -fx-stroke: transparent; }
```

- [ ] **Step 4: Create the mark**

Create `src/main/resources/com/argus/ui/view/mark.fxml`. Two stroked
circle arcs inside a stroked outer ellipse read as an eye, and two
filled circles inside those read as the iris. No controller, no
dependency (the alternative, an icon font or javafx-svg, would need an
ADR).

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.layout.StackPane?>
<?import javafx.scene.shape.Circle?>
<?import javafx.scene.shape.Ellipse?>
<StackPane prefHeight="29.0" prefWidth="46.0"
            xmlns="http://javafx.com/javafx/21.0.8"
            xmlns:fx="http://javafx.com/fxml/1">
    <children>
        <Ellipse focusTraversable="false" radiusX="20.0" radiusY="12.0"
                 styleClass="ag-mark-eye"/>
        <Circle focusTraversable="false" radius="5.5" styleClass="ag-mark-eye"
                translateY="-9.0"/>
        <Circle focusTraversable="false" radius="5.5" styleClass="ag-mark-eye"
                translateY="9.0"/>
        <Circle focusTraversable="false" radius="2.0" styleClass="ag-mark-pupil"
                translateY="-9.0"/>
        <Circle focusTraversable="false" radius="2.0" styleClass="ag-mark-pupil"
                translateY="9.0"/>
    </children>
</StackPane>
```

- [ ] **Step 5: Add `markLoads` to FxmlLoadTest**

In `src/test/java/com/argus/ui/FxmlLoadTest.java` add:

```java
    @Test
    void markLoads() throws IOException {
        FXMLLoader loader = loader("mark");
        Parent root = loader.load();
        assertNotNull(root);
        assertEquals(5, root.lookupAll(".ag-mark-eye, .ag-mark-pupil").size(),
                "the mark is five shapes: one ellipse and two iris/pupil pairs");
    }
```

and add `import static org.junit.jupiter.api.Assertions.assertEquals;` to
the file's static imports.

- [ ] **Step 6: Run the whole suite**

Run: `mvn -q test -Dtest=CssContractTest+markLoads`
Expected: `markLoads` PASS. `CssContractTest` still FAILS on
`everyDefinedClassIsUsed`, because no FXML or controller uses an `ag-*`
class yet. That is the expected mid-plan state: Tasks 2 to 7 consume the
classes. Do not weaken the test. Note the failing set in the DIARY entry
so the next task knows the target.

- [ ] **Step 7: DIARY entry and hand-off commit message**

Append to `DIARY.md`:

```markdown
## 2026-09-27 - UI overhaul cycle 1a: token sheet, class contract, mark
- **CHANGED:** `application.css` rewritten from 20 lines to the full `ag-*` token sheet (chrome, type, status, four button variants, nav, inputs, table, list, charts, mark); `mark.fxml` (new, five shapes, no controller, no dependency); `CssContractTest` (new, both directions: every `ag-*` used in FXML or a ui controller is defined, every defined class is used, sheet holds at least 38); `FxmlLoadTest.markLoads`.
- **VERIFIED:** `markLoads` green. `CssContractTest.theSheetIsNotEmpty` green. `everyDefinedClassIsUsed` still red by design until cycles 1b-6 consume the classes; the failing set is the task list.
- **OPEN:** no view uses a token yet. Window geometry is untouched.
```

Commit message for the human: `feat(ui): ag-* design token sheet, CSS class contract test, eye mark`

---

### Task 2: Window geometry and the shell frame

**Files:**
- Modify: `src/main/java/com/argus/ui/MainApp.java:56-145` (geometry)
- Modify: `src/main/resources/com/argus/ui/view/shell.fxml`
- Modify: `src/main/java/com/argus/ui/controller/ShellController.java:169-181` (nav selection)
- Test: `src/test/java/com/argus/ui/WindowGeometryTest.java` (new)

**Interfaces:**
- Consumes: the `ag-*` classes from Task 1.
- Produces: `MainApp.WINDOW_WIDTH`, `MainApp.WINDOW_HEIGHT`,
  `MainApp.MIN_WIDTH`, `MainApp.MIN_HEIGHT` (package visible, 1280, 720,
  1100, 640) and `private void applyGeometry(Scene)`.
  `ShellController.setSession(String)` for the status bar right side.
  `shell.fxml` gains `fx:id="sessionLabel"`.

- [ ] **Step 1: Write the failing geometry test**

Create `src/test/java/com/argus/ui/WindowGeometryTest.java`:

```java
package com.argus.ui;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The spec fixes the geometry in one place so no view can drift. The
 * numbers are the contract, so they are pinned here rather than left to
 * a screenshot.
 */
class WindowGeometryTest {

    @Test
    void windowsOpenAt1280x720() throws Exception {
        assertEquals(1280, intConstant("WINDOW_WIDTH"));
        assertEquals(720, intConstant("WINDOW_HEIGHT"));
    }

    @Test
    void theMinimumFitsTheInspectorAndTheTable() throws Exception {
        assertEquals(1100, intConstant("MIN_WIDTH"));
        assertEquals(640, intConstant("MIN_HEIGHT"));
    }

    private static int intConstant(String name) throws Exception {
        Field field = MainApp.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(null);
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `mvn -q test -Dtest=WindowGeometryTest`
Expected: FAIL with `NoSuchFieldException: WINDOW_WIDTH`.

- [ ] **Step 3: Add the geometry to MainApp**

In `MainApp`, add the constants after `private static final Logger LOG`:

```java
    /** Every main window opens here. The spec fixes these four numbers. */
    static final int WINDOW_WIDTH = 1280;
    static final int WINDOW_HEIGHT = 720;
    static final int MIN_WIDTH = 1100;
    static final int MIN_HEIGHT = 640;
```

Replace every `new Scene(l.load(), W, H)` in `showLogin`, `showDashboard`,
`showShell`, `showTargets`, `showExport`, `showResults`, `showEntryPoints`
and `showLock` with `new Scene(l.load(), WINDOW_WIDTH, WINDOW_HEIGHT)`.

Replace `clearStageMin()` and all its call sites with one method:

```java
    /** The single place window size is set, so no scene can drift (JAVAFX.md). */
    private void applyGeometry(Scene scene, Stage stage) {
        stage.setScene(scene);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
    }
```

Every shower then ends with `applyGeometry(scene, primaryStage);` instead
of `primaryStage.setScene(scene)`. Delete `clearStageMin` and its seven
call sites. In `showShell`, delete the two
`primaryStage.setMinWidth/setMinHeight` lines and add, after
`controller.showDashboard();`:

```java
        controller.setSession(currentUsername);
```

- [ ] **Step 4: Rebuild shell.fxml on the tokens**

Replace `src/main/resources/com/argus/ui/view/shell.fxml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.Separator?>
<?import javafx.scene.control.StackPane?>
<?import javafx.scene.layout.BorderPane?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.Region?>
<?import javafx.scene.layout.VBox?>
<BorderPane prefHeight="720.0" prefWidth="1280.0" styleClass="root-pane"
            xmlns="http://javafx.com/javafx/21.0.8"
            xmlns:fx="http://javafx.com/fxml/1"
            fx:controller="com.argus.ui.controller.ShellController">
    <left>
        <VBox alignment="TOP_LEFT" prefWidth="220.0" spacing="4.0"
              styleClass="ag-chrome">
            <children>
                <HBox alignment="CENTER_LEFT" spacing="9.0">
                    <padding>
                        <Insets bottom="18.0" left="18.0" right="14.0" top="20.0"/>
                    </padding>
                    <children>
                        <fx:include source="mark.fxml"/>
                        <Label styleClass="ag-wordmark" text="ARGUS"/>
                    </children>
                </HBox>
                <Separator styleClass="ag-rule"/>
                <Button fx:id="navDashboard" maxWidth="Infinity" mnemonicParsing="false"
                        onAction="#onNavDashboard" prefHeight="34.0"
                        styleClass="ag-nav" text="Dashboard"/>
                <Button fx:id="navTargets" maxWidth="Infinity" mnemonicParsing="false"
                        onAction="#onNavTargets" prefHeight="34.0"
                        styleClass="ag-nav" text="Targets"/>
                <Button fx:id="navResults" maxWidth="Infinity" mnemonicParsing="false"
                        onAction="#onNavResults" prefHeight="34.0"
                        styleClass="ag-nav" text="Results"/>
                <Button fx:id="navEntryPoints" maxWidth="Infinity" mnemonicParsing="false"
                        onAction="#onNavEntryPoints" prefHeight="34.0"
                        styleClass="ag-nav" text="Entry points"/>
                <Button fx:id="navExport" maxWidth="Infinity" mnemonicParsing="false"
                        onAction="#onNavExport" prefHeight="34.0"
                        styleClass="ag-nav" text="Export"/>
                <Button fx:id="navApiKeys" maxWidth="Infinity" mnemonicParsing="false"
                        onAction="#onNavApiKeys" prefHeight="34.0"
                        styleClass="ag-nav" text="API keys"/>
                <Separator styleClass="ag-rule"/>
                <Label fx:id="keyStatusLabel" maxWidth="Infinity" styleClass="ag-mono"
                       text="" wrapText="true">
                    <padding>
                        <Insets left="14.0" top="4.0"/>
                    </padding>
                </Label>
                <Region VBox.vgrow="ALWAYS"/>
                <Button fx:id="lockButton" maxWidth="Infinity" mnemonicParsing="false"
                        onAction="#onLock" prefHeight="34.0"
                        styleClass="ag-nav" text="Lock"/>
                <Button fx:id="logoutButton" maxWidth="Infinity" mnemonicParsing="false"
                        onAction="#onLogout" prefHeight="34.0"
                        styleClass="ag-nav" text="Log out"/>
            </children>
        </VBox>
    </left>
    <center>
        <StackPane fx:id="contentArea" styleClass="ag-content"/>
    </center>
    <bottom>
        <HBox alignment="CENTER_LEFT" prefHeight="28.0" styleClass="ag-statusbar">
            <children>
                <Label fx:id="statusBarLabel" styleClass="ag-mono" text="Ready"/>
                <Region HBox.hgrow="ALWAYS"/>
                <Label fx:id="sessionLabel" styleClass="ag-mono" text=""/>
            </children>
        </HBox>
    </bottom>
</BorderPane>
```

Add the missing import `<?import javafx.geometry.Insets?>` after the
version line. `Insets` is needed for the padding property.

- [ ] **Step 5: Nav selection through one class**

In `ShellController`, add the `sessionLabel` field and replace `select`:

```java
    @FXML
    private Label sessionLabel;
```

```java
    /** One class carries the selected state; the tokens own the rest. */
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
```

- [ ] **Step 6: Verify**

Run: `mvn -q test -Dtest=WindowGeometryTest+shellLoads`
Expected: PASS both. Then run `mvn -q verify` and confirm no other test
regressed (the suite was green at `3fc8394`).

- [ ] **Step 7: Manual click path**

`mvn javafx:run`, create an account on first run, sign in. Confirm: the
window opens 1280x720; the sidebar shows the mark, six nav items and the
provider line; Lock and Log out sit at the bottom of the sidebar; the
status bar shows "Ready" left and "kasumi · vault unlocked" right. Drag
the window down to the minimum: nothing clips. Navigate Dashboard,
Targets, Export: each swaps in place and the sidebar selection follows.

- [ ] **Step 8: DIARY entry and hand-off commit message**

```markdown
## 2026-09-27 - UI overhaul cycle 1b: window geometry and shell frame
- **CHANGED:** `MainApp` - `WINDOW_WIDTH/HEIGHT` (1280x720) and `MIN_WIDTH/MIN_HEIGHT` (1100x640) as the only geometry numbers, one `applyGeometry(scene, stage)` replacing `setScene` plus `clearStageMin` (7 call sites gone); all eight showers at 1280x720; `showShell` seeds the status bar with the operator. `shell.fxml` rebuilt on tokens: 220 sidebar, `fx:include mark.fxml`, six `ag-nav` items, `ag-rule` separators, monospace provider line, spacer, Lock/Log out pinned, 28px `ag-statusbar` with a new `sessionLabel`. `ShellController.select` toggles the single `ag-nav-selected` class; new `setSession`.
- **VERIFIED:** `mvn verify` green. New `WindowGeometryTest` (2) pins the four numbers. Manual: window opens 1280x720, nothing clips at the 1100x640 minimum, three panes swap with the selection following.
- **OPEN:** pane FXMLs still carry the old classes (`striped`, `accent`, `title-label`); cycles 2-6 replace them.
```

Commit message for the human: `feat(ui): 1280x720 geometry in one place, sidebar shell on the token sheet`

---

### Task 3: Auth screens on the split frame

**Files:**
- Modify: `src/main/resources/com/argus/ui/view/login.fxml`
- Modify: `src/main/resources/com/argus/ui/view/lock.fxml`
- Modify: `src/main/java/com/argus/ui/controller/LoginController.java:27-32,111-124`
- Test: `src/test/java/com/argus/ui/controller/LoginControllerTest.java` (new)

**Interfaces:**
- Consumes: `ag-*` from Task 1, `applyGeometry` from Task 2.
- Produces: `LoginController.AuthCopy(String heading, String sub, String
  primary, String link)` and `static AuthCopy copyFor(boolean createMode,
  boolean firstRun)`. `LockController` is unchanged: `lock.fxml` keeps
  the ids `passwordField`, `unlockButton`, `statusLabel`.

- [ ] **Step 1: Write the failing copy test**

Create `src/test/java/com/argus/ui/controller/LoginControllerTest.java`:

```java
package com.argus.ui.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The auth screen is one 1280x720 frame in two modes. The copy is pure
 * so it can be pinned without a toolkit (headless gate).
 */
class LoginControllerTest {

    @Test
    void signInModeCopy() {
        var copy = LoginController.copyFor(false, false);

        assertEquals("Sign in", copy.heading());
        assertEquals("Unlock your vault key.", copy.sub());
        assertEquals("Sign in", copy.primary());
        assertEquals("Create account", copy.link());
    }

    @Test
    void firstRunOpensOnAccountCreation() {
        var copy = LoginController.copyFor(true, true);

        assertEquals("Create your account", copy.heading());
        assertTrue(copy.sub().startsWith("First run"),
                "first run must say so, got: " + copy.sub());
        assertEquals("Create account", copy.primary());
        assertEquals("Back to sign in", copy.link());
    }

    @Test
    void aReturningOperatorCreatesWithoutTheFirstRunWording() {
        var copy = LoginController.copyFor(true, false);

        assertEquals("Create an account", copy.heading());
        assertTrue(copy.sub().startsWith("New operator"),
                "got: " + copy.sub());
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `mvn -q test -Dtest=LoginControllerTest`
Expected: compile FAIL, `cannot find symbol: method copyFor`.

- [ ] **Step 3: Add the copy helper to LoginController**

Replace the `modeLabel` field with two labels and add the record. In
`LoginController`:

```java
    @FXML
    private Label headingLabel;
    @FXML
    private Label subLabel;
```

```java
    /** Copy for the two auth modes. Pure, so it is unit tested without a toolkit. */
    public record AuthCopy(String heading, String sub, String primary, String link) {
    }

    static AuthCopy copyFor(boolean createMode, boolean firstRun) {
        if (createMode) {
            return new AuthCopy(
                    firstRun ? "Create your account" : "Create an account",
                    firstRun ? "First run. Pick an operator name and password."
                             : "New operator. Pick a name and password.",
                    "Create account", "Back to sign in");
        }
        return new AuthCopy("Sign in", "Unlock your vault key.", "Sign in", "Create account");
    }
```

Replace `setCreateMode` with:

```java
    private void setCreateMode(boolean create) {
        this.createMode = create;
        AuthCopy copy = copyFor(create, firstRun);
        headingLabel.setText(copy.heading());
        subLabel.setText(copy.sub());
        primaryButton.setText(copy.primary());
        switchModeButton.setText(copy.link());
    }
```

- [ ] **Step 4: Rebuild login.fxml**

Replace `src/main/resources/com/argus/ui/view/login.fxml`. The FXML text
matches `copyFor(false, false)` so there is no flash before the first
run check answers.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.PasswordField?>
<?import javafx.scene.control.TextField?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.StackPane?>
<?import javafx.scene.layout.VBox?>
<StackPane prefHeight="720.0" prefWidth="1280.0" styleClass="root-pane"
           xmlns="http://javafx.com/javafx/21.0.8"
           xmlns:fx="http://javafx.com/fxml/1"
           fx:controller="com.argus.ui.controller.LoginController">
    <children>
        <HBox alignment="CENTER_LEFT">
            <children>
                <VBox alignment="TOP_LEFT" prefWidth="47.0%" spacing="0.0"
                      HBox.hgrow="NEVER" styleClass="ag-brand-panel">
                    <padding>
                        <Insets bottom="56.0" left="62.0" right="40.0" top="70.0"/>
                    </padding>
                    <children>
                        <HBox alignment="CENTER_LEFT" spacing="14.0">
                            <children>
                                <fx:include source="mark.fxml"/>
                                <Label styleClass="ag-wordmark" text="ARGUS"/>
                            </children>
                        </HBox>
                        <Label maxWidth="340.0" styleClass="ag-tagline" text="Reconnaissance console. Targets you are authorized to assess." wrapText="true">
                            <padding>
                                <Insets top="34.0"/>
                            </padding>
                        </Label>
                        <Region VBox.vgrow="ALWAYS"/>
                        <Label styleClass="ag-mono" text="v0.1.0 · local vault"/>
                    </children>
                </VBox>
                <StackPane HBox.hgrow="ALWAYS" styleClass="root-pane">
                    <children>
                        <VBox alignment="TOP_LEFT" maxWidth="430.0" spacing="14.0"
                              styleClass="ag-card">
                            <children>
                                <Label fx:id="headingLabel" styleClass="ag-view-title" text="Sign in"/>
                                <Label fx:id="subLabel" styleClass="ag-sub" text="Unlock your vault key."/>
                                <Label styleClass="ag-field-label" text="USERNAME">
                                    <padding>
                                        <Insets top="8.0"/>
                                    </padding>
                                </Label>
                                <TextField fx:id="usernameField" maxWidth="Infinity"
                                           prefHeight="44.0" promptText="kasumi"
                                           styleClass="ag-field"/>
                                <Label styleClass="ag-field-label" text="PASSWORD"/>
                                <PasswordField fx:id="passwordField" maxWidth="Infinity"
                                               prefHeight="44.0" promptText="Password"
                                               styleClass="ag-field"/>
                                <Button fx:id="primaryButton" maxWidth="Infinity"
                                        mnemonicParsing="false" onAction="#onSubmit"
                                        prefHeight="44.0" styleClass="ag-primary"
                                        text="Sign in"/>
                                <Label fx:id="statusLabel" maxWidth="Infinity"
                                       styleClass="ag-muted" text="" wrapText="true"/>
                                <Button fx:id="switchModeButton" maxWidth="Infinity"
                                        mnemonicParsing="false" onAction="#onToggleMode"
                                        prefHeight="30.0" styleClass="ag-link"
                                        text="Create account"/>
                            </children>
                        </VBox>
                    </children>
                </StackPane>
            </children>
        </HBox>
    </children>
</StackPane>
```

Add the `<?import javafx.scene.layout.Region?>` import: the brand panel
uses `<Region VBox.vgrow="ALWAYS"/>` to push the version line down.

- [ ] **Step 5: Rebuild lock.fxml**

Replace `src/main/resources/com/argus/ui/view/lock.fxml`. Same frame,
same ids as today (`passwordField`, `unlockButton`, `statusLabel`), so
`LockController` needs no change at all:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.PasswordField?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.Region?>
<?import javafx.scene.layout.StackPane?>
<?import javafx.scene.layout.VBox?>
<StackPane prefHeight="720.0" prefWidth="1280.0" styleClass="root-pane"
           xmlns="http://javafx.com/javafx/21.0.8"
           xmlns:fx="http://javafx.com/fxml/1"
           fx:controller="com.argus.ui.controller.LockController">
    <children>
        <HBox alignment="CENTER_LEFT">
            <children>
                <VBox alignment="TOP_LEFT" prefWidth="47.0%" spacing="0.0"
                      HBox.hgrow="NEVER" styleClass="ag-brand-panel">
                    <padding>
                        <Insets bottom="56.0" left="62.0" right="40.0" top="70.0"/>
                    </padding>
                    <children>
                        <HBox alignment="CENTER_LEFT" spacing="14.0">
                            <children>
                                <fx:include source="mark.fxml"/>
                                <Label styleClass="ag-wordmark" text="ARGUS"/>
                            </children>
                        </HBox>
                        <Label maxWidth="340.0" styleClass="ag-tagline" text="Session idle. Re-enter your password to unlock the vault." wrapText="true">
                            <padding>
                                <Insets top="34.0"/>
                            </padding>
                        </Label>
                        <Region VBox.vgrow="ALWAYS"/>
                        <Label styleClass="ag-mono" text="v0.1.0 · local vault"/>
                    </children>
                </VBox>
                <StackPane HBox.hgrow="ALWAYS" styleClass="root-pane">
                    <children>
                        <VBox alignment="TOP_LEFT" maxWidth="430.0" spacing="14.0"
                              styleClass="ag-card">
                            <children>
                                <Label styleClass="ag-view-title" text="Argus locked"/>
                                <Label styleClass="ag-sub" text="The vault key stays wiped until you unlock."/>
                                <Label styleClass="ag-field-label" text="PASSWORD">
                                    <padding>
                                        <Insets top="8.0"/>
                                    </padding>
                                </Label>
                                <PasswordField fx:id="passwordField" maxWidth="Infinity"
                                               prefHeight="44.0" promptText="Password"
                                               styleClass="ag-field"/>
                                <Button fx:id="unlockButton" maxWidth="Infinity"
                                        mnemonicParsing="false" onAction="#onUnlock"
                                        prefHeight="44.0" styleClass="ag-primary"
                                        text="Unlock"/>
                                <Label fx:id="statusLabel" maxWidth="Infinity"
                                       styleClass="ag-muted" text="" wrapText="true"/>
                            </children>
                        </VBox>
                    </children>
                </StackPane>
            </children>
        </HBox>
    </children>
</StackPane>
```

- [ ] **Step 6: Verify**

Run: `mvn -q test -Dtest=LoginControllerTest+loginLoads+lockLoads`
Expected: PASS all. Then `mvn -q verify` clean.

- [ ] **Step 7: Manual click path**

`mvn javafx:run`. Fresh database: the screen opens already in create mode
with "Create your account" and the first run line. Create the account,
you land in the shell. Log out: sign in mode with the link offering
"Create account". Toggle to create mode and back, both fields clear. Lock
from the sidebar: the lock screen uses the same frame. Unlock with the
right password, and with a wrong one (status line, no dialog).

- [ ] **Step 8: DIARY entry and hand-off commit message**

```markdown
## 2026-09-27 - UI overhaul cycle 2: auth screens on the split frame
- **CHANGED:** `login.fxml` + `lock.fxml` rebuilt to the approved split (brand panel 47% with the mark, one line of copy, version line; form card 430 right, 44 inputs, full width primary, status line, text link toggle). `LoginController`: `modeLabel` split into `headingLabel`/`subLabel`, new pure `copyFor(createMode, firstRun)` returning `AuthCopy`, `setCreateMode` applies it. `LockController` untouched, `lock.fxml` kept its three ids.
- **VERIFIED:** `mvn verify` green. New `LoginControllerTest` (3) pins the copy in both modes plus the first run wording. FXML prepaint text equals `copyFor(false,false)` so no flash. Manual: create, sign in, log out, toggle, lock, wrong password.
- **OPEN:** `onToggleMode` still clears the status label on toggle (kept, correct).
```

Commit message for the human: `feat(ui): auth screens on the split frame, copy extracted for testing`

---

### Task 4: Dashboard pane

**Files:**
- Modify: `src/main/resources/com/argus/ui/view/dashboard.fxml`
- Modify: `src/main/java/com/argus/ui/controller/DashboardController.java` (chart series class only)
- Test: `src/test/java/com/argus/ui/FxmlLoadTest.java` (add `dashboardUsesTokenClasses`)

**Interfaces:**
- Consumes: the `ag-*` classes. Keeps every existing `fx:id`
  (`scanTargetCombo`, `scanButton`, `scanProgress`, `scanStatusLabel`,
  `statScans`, `statHosts`, `statPorts`, `statKev`, `statVt`,
  `severityChart`, `portsChart`) so `DashboardController` keeps compiling
  untouched.
- Produces: `DashboardController` adds `"ag-series"` to each chart series
  it feeds, which is the only Java change.

- [ ] **Step 1: Write the failing layout test**

Add to `FxmlLoadTest`:

```java
    @Test
    void dashboardUsesTokenClasses() throws IOException {
        FXMLLoader loader = loader("dashboard");
        Parent root = loader.load();

        assertEquals(5, root.lookupAll(".ag-tile").size(),
                "one tile per stat: scans, hosts, ports, KEV, VT");
        assertEquals(2, root.lookupAll(".ag-chart").size(),
                "severity and top ports charts");
        assertNotNull(root.lookup("#scanButton"), "scan command card lost its button");
        assertTrue(root.lookup("#scanButton").getStyleClass().contains("ag-primary"),
                "the scan command is the view's one primary");
    }
```

- [ ] **Step 2: Run it and watch it fail**

Run: `mvn -q test -Dtest=FxmlLoadTest#dashboardUsesTokenClasses`
Expected: FAIL, `expected 5 but was 0` (the current dashboard has no
`ag-tile`).

- [ ] **Step 3: Rebuild dashboard.fxml**

Replace `src/main/resources/com/argus/ui/view/dashboard.fxml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.geometry.Insets?>
<?import javafx.scene.chart.BarChart?>
<?import javafx.scene.chart.CategoryAxis?>
<?import javafx.scene.chart.NumberAxis?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.ComboBox?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.ProgressBar?>
<?import javafx.scene.control.ScrollPane?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.Region?>
<?import javafx.scene.layout.VBox?>
<ScrollPane fitToWidth="true" styleClass="ag-scroll"
            xmlns="http://javafx.com/javafx/21.0.8"
            xmlns:fx="http://javafx.com/fxml/1"
            fx:controller="com.argus.ui.controller.DashboardController">
    <content>
        <VBox spacing="16.0" styleClass="ag-pane">
            <children>
                <Label styleClass="ag-view-title" text="Dashboard"/>

                <VBox spacing="12.0" styleClass="ag-card">
                    <children>
                        <Label styleClass="ag-field-label" text="SCAN"/>
                        <HBox alignment="CENTER_LEFT" spacing="12.0">
                            <children>
                                <ComboBox fx:id="scanTargetCombo" maxWidth="Infinity"
                                          prefHeight="38.0" promptText="Target"
                                          styleClass="ag-field" HBox.hgrow="ALWAYS"/>
                                <Button fx:id="scanButton" mnemonicParsing="false"
                                        onAction="#onScan" prefHeight="38.0"
                                        styleClass="ag-primary" text="Scan"/>
                            </children>
                        </HBox>
                        <ProgressBar fx:id="scanProgress" maxWidth="Infinity"
                                     prefHeight="8.0" progress="0"/>
                        <Label fx:id="scanStatusLabel" maxWidth="Infinity"
                               styleClass="ag-muted" text="" wrapText="true"/>
                    </children>
                </VBox>

                <HBox spacing="12.0">
                    <children>
                        <VBox spacing="2.0" styleClass="ag-tile" HBox.hgrow="ALWAYS">
                            <children>
                                <Label styleClass="ag-stat-label" text="SCANS"/>
                                <Label fx:id="statScans" styleClass="ag-stat-value" text="-"/>
                            </children>
                        </VBox>
                        <VBox spacing="2.0" styleClass="ag-tile" HBox.hgrow="ALWAYS">
                            <children>
                                <Label styleClass="ag-stat-label" text="ALIVE HOSTS"/>
                                <Label fx:id="statHosts" styleClass="ag-stat-value" text="-"/>
                            </children>
                        </VBox>
                        <VBox spacing="2.0" styleClass="ag-tile" HBox.hgrow="ALWAYS">
                            <children>
                                <Label styleClass="ag-stat-label" text="OPEN PORTS"/>
                                <Label fx:id="statPorts" styleClass="ag-stat-value" text="-"/>
                            </children>
                        </VBox>
                        <VBox spacing="2.0" styleClass="ag-tile" HBox.hgrow="ALWAYS">
                            <children>
                                <Label styleClass="ag-stat-label" text="KEV FINDINGS"/>
                                <Label fx:id="statKev" styleClass="ag-stat-value" text="-"/>
                            </children>
                        </VBox>
                        <VBox spacing="2.0" styleClass="ag-tile" HBox.hgrow="ALWAYS">
                            <children>
                                <Label styleClass="ag-stat-label" text="VT FLAGGED"/>
                                <Label fx:id="statVt" styleClass="ag-stat-value" text="-"/>
                            </children>
                        </VBox>
                    </children>
                </HBox>

                <HBox spacing="16.0">
                    <children>
                        <BarChart fx:id="severityChart" animated="false"
                                  legendVisible="false" styleClass="ag-chart"
                                  title="Findings by severity" HBox.hgrow="ALWAYS">
                            <xAxis>
                                <CategoryAxis label="Severity"/>
                            </xAxis>
                            <yAxis>
                                <NumberAxis label="Count"/>
                            </yAxis>
                        </BarChart>
                        <BarChart fx:id="portsChart" animated="false"
                                  legendVisible="false" styleClass="ag-chart"
                                  title="Top ports" HBox.hgrow="ALWAYS">
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

Delete the `<?import javafx.geometry.Insets?>` line if unused: this file
sets padding through `.ag-pane` and `.ag-card`, so remove it.

- [ ] **Step 4: Colour the chart series from the sheet**

In `DashboardController`, wherever it builds a series for
`severityChart` or `portsChart`, add the class so the fill comes from
`.ag-series` rather than the theme default:

```java
        series.getStyleClass().add("ag-series");
```

- [ ] **Step 5: Verify**

Run: `mvn -q test -Dtest=FxmlLoadTest`
Expected: all ten FXML tests PASS, including the new one. Then
`mvn -q verify` clean, which also re-runs `DashboardControllerTest` (its
four tests must stay green: no controller logic changed).

- [ ] **Step 6: Manual click path**

Sign in, land on Dashboard. Confirm: five equal tiles, the scan card
with a full width target combo and a green Scan, both charts side by side
on a card background. Run a scan: progress bar fills, status line
updates, tiles and charts refresh on finish. Navigate away mid scan, the
CONFIRMATION dialog still appears.

- [ ] **Step 7: DIARY entry and hand-off commit message**

```markdown
## 2026-09-27 - UI overhaul cycle 3: dashboard pane
- **CHANGED:** `dashboard.fxml` rebuilt on tokens: `.ag-pane` VBox inside an `ag-scroll` ScrollPane, scan command card (`.ag-card`, growing target combo, green Scan, 8px progress, status line), five equal `.ag-tile` stat tiles, two `.ag-chart` cards side by side. Every `fx:id` kept, so `DashboardController` compiles untouched. Controller change is one line per chart series: `getStyleClass().add("ag-series")` so the bar fill comes from the sheet.
- **VERIFIED:** `mvn verify` green. New `FxmlLoadTest.dashboardUsesTokenClasses` (5 tiles, 2 charts, scan button is `ag-primary`). The 4 existing `DashboardControllerTest` tests unchanged and green. Manual: tiles equal width, charts side by side, scan progress and refresh on finish.
- **OPEN:** chart series colours are one accent for both charts; a per severity ramp is a future item, not in this spec.
```

Commit message for the human: `feat(ui): dashboard pane on the token sheet`

---

### Task 5: Targets and Export panes

**Files:**
- Modify: `src/main/resources/com/argus/ui/view/targets.fxml`
- Modify: `src/main/resources/com/argus/ui/view/export.fxml`
- Modify: `src/main/java/com/argus/ui/controller/TargetsController.java:33-71,140-160`
- Modify: `src/main/java/com/argus/ui/controller/ExportController.java` (unused `main` field only)
- Test: `src/test/java/com/argus/ui/FxmlLoadTest.java` (add `targetsAndExportUseTokenClasses`)

**Interfaces:**
- Consumes: the `ag-*` classes.
- Produces: `targets.fxml` gains `fx:id` `inspectorTitle`, `domainValue`,
  `profileValue`, `createdValue`, `scopeList`, and reuses the existing
  `deleteButton`? No: the delete button keeps its handler but moves into
  the inspector footer, so it stays `onAction="#onDelete"` with the same
  `Button` (it has no `fx:id` today, so give it `fx:id="deleteButton"`).
  `export.fxml` keeps `targetCombo`, `formatCombo`, `statusLabel`.

- [ ] **Step 1: Write the failing layout test**

Add to `FxmlLoadTest`:

```java
    @Test
    void targetsAndExportUseTokenClasses() throws IOException {
        FXMLLoader targets = loader("targets");
        Parent root = targets.load();

        assertEquals(1, root.lookupAll(".ag-inspector").size(),
                "one inspector, on the right of the table");
        assertEquals(1, root.lookupAll(".ag-table").size());
        assertTrue(root.lookup("#deleteButton").getStyleClass().contains("ag-danger"),
                "delete target is the row level destructive action");
        assertNotNull(root.lookup("#scopeList"), "the full CIDR list needs a home");

        FXMLLoader export = loader("export");
        Parent pane = export.load();
        assertTrue(pane.lookupAll(".ag-card").size() >= 1,
                "export is one card, not a floating button row");
    }
```

- [ ] **Step 2: Run it and watch it fail**

Run: `mvn -q test -Dtest=FxmlLoadTest#targetsAndExportUseTokenClasses`
Expected: FAIL, `No Node found for #deleteButton`.

- [ ] **Step 3: Rebuild targets.fxml**

Replace `src/main/resources/com/argus/ui/view/targets.fxml`. The add form
becomes a card of labelled rows, the Delete button moves into the
inspector footer (row level, so it belongs there per the spec's rule 4):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.ComboBox?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.ListView?>
<?import javafx.scene.control.Separator?>
<?import javafx.scene.control.TableColumn?>
<?import javafx.scene.control.TableView?>
<?import javafx.scene.control.TextField?>
<?import javafx.scene.control.cell.PropertyValueFactory?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.Region?>
<?import javafx.scene.layout.VBox?>
<VBox spacing="16.0" styleClass="ag-pane"
      xmlns="http://javafx.com/javafx/21.0.8"
      xmlns:fx="http://javafx.com/fxml/1"
      fx:controller="com.argus.ui.controller.TargetsController">
    <children>
        <Label styleClass="ag-view-title" text="Targets"/>

        <VBox spacing="12.0" styleClass="ag-card">
            <children>
                <Label styleClass="ag-field-label" text="NEW TARGET"/>
                <HBox alignment="BOTTOM_LEFT" spacing="12.0">
                    <children>
                        <VBox spacing="6.0" HBox.hgrow="ALWAYS">
                            <children>
                                <Label styleClass="ag-field-label" text="LABEL"/>
                                <TextField fx:id="labelField" maxWidth="Infinity"
                                           prefHeight="38.0" promptText="Acme"
                                           styleClass="ag-field"/>
                            </children>
                        </VBox>
                        <VBox spacing="6.0" HBox.hgrow="ALWAYS">
                            <children>
                                <Label styleClass="ag-field-label" text="DOMAIN"/>
                                <TextField fx:id="domainField" maxWidth="Infinity"
                                           prefHeight="38.0" promptText="example.com"
                                           styleClass="ag-field"/>
                            </children>
                        </VBox>
                        <VBox spacing="6.0" HBox.hgrow="ALWAYS">
                            <children>
                                <Label styleClass="ag-field-label" text="SCOPE CIDRS"/>
                                <TextField fx:id="scopeField" maxWidth="Infinity"
                                           prefHeight="38.0" promptText="203.0.113.0/24, comma separated"
                                           styleClass="ag-field"/>
                            </children>
                        </VBox>
                        <VBox spacing="6.0">
                            <children>
                                <Label styleClass="ag-field-label" text="PROFILE"/>
                                <ComboBox fx:id="profileCombo" prefHeight="38.0"
                                          prefWidth="140.0" styleClass="ag-field"/>
                            </children>
                        </VBox>
                        <Button mnemonicParsing="false" onAction="#onAdd"
                                prefHeight="38.0" styleClass="ag-primary" text="Add"/>
                    </children>
                </HBox>
            </children>
        </VBox>

        <TextField fx:id="searchField" maxWidth="Infinity" promptText="Search label or domain"
                   styleClass="ag-search"/>

        <HBox spacing="16.0" VBox.vgrow="ALWAYS">
            <children>
                <TableView fx:id="targetsTable" maxWidth="Infinity"
                           styleClass="ag-table" HBox.hgrow="ALWAYS" VBox.vgrow="ALWAYS">                    <columns>
                        <TableColumn prefWidth="180.0" text="Label">
                            <cellValueFactory>
                                <PropertyValueFactory property="label"/>
                            </cellValueFactory>
                        </TableColumn>
                        <TableColumn prefWidth="200.0" text="Domain">
                            <cellValueFactory>
                                <PropertyValueFactory property="domain"/>
                            </cellValueFactory>
                        </TableColumn>
                        <TableColumn prefWidth="120.0" text="Profile">
                            <cellValueFactory>
                                <PropertyValueFactory property="profile"/>
                            </cellValueFactory>
                        </TableColumn>
                        <TableColumn prefWidth="260.0" text="Scope CIDRs">
                            <cellValueFactory>
                                <PropertyValueFactory property="scopeCidrs"/>
                            </cellValueFactory>
                        </TableColumn>
                    </columns>
                </TableView>

                <VBox alignment="TOP_LEFT" maxWidth="300.0" minWidth="300.0"
                      prefWidth="300.0" spacing="10.0" styleClass="ag-inspector"
                      HBox.hgrow="NEVER">
                    <children>
                        <Label fx:id="inspectorTitle" maxWidth="Infinity"
                               styleClass="ag-value" text="Select a target"/>
                        <Separator styleClass="ag-rule"/>
                        <Label styleClass="ag-field-label" text="DOMAIN"/>
                        <Label fx:id="domainValue" maxWidth="Infinity" styleClass="ag-value" text="-"/>
                        <Label styleClass="ag-field-label" text="PROFILE"/>
                        <Label fx:id="profileValue" maxWidth="Infinity" styleClass="ag-value" text="-"/>
                        <Label styleClass="ag-field-label" text="CREATED"/>
                        <Label fx:id="createdValue" maxWidth="Infinity" styleClass="ag-value" text="-"/>
                        <Label styleClass="ag-field-label" text="SCOPE"/>
                        <ListView fx:id="scopeList" maxWidth="Infinity" prefHeight="150.0"
                                  styleClass="ag-list" VBox.vgrow="ALWAYS"/>
                        <Button fx:id="deleteButton" maxWidth="Infinity" mnemonicParsing="false"
                                onAction="#onDelete" prefHeight="38.0" styleClass="ag-danger"
                                text="Delete target"/>
                    </children>
                </VBox>
            </children>
        </HBox>
    </children>
</VBox>
```

The Delete button now carries `fx:id="deleteButton"` so
`CssContractTest` and the layout test can find it; `onDelete` is
unchanged and still reads the table selection.

- [ ] **Step 4: Wire the targets inspector**

In `TargetsController`, replace the `main` field with the uniform seam
and add the inspector fields:

```java
    @FXML
    private Label inspectorTitle;
    @FXML
    private Label domainValue;
    @FXML
    private Label profileValue;
    @FXML
    private Label createdValue;
    @FXML
    private ListView<String> scopeList;
    @FXML
    private Button deleteButton;

    private final ObservableList<String> scopeLines = FXCollections.observableArrayList();
```

```java
    /** Uniform pane seam. This pane reads no session state, so no field. */
    public void setMain(MainApp main) {
        this.dao = new TargetDAO(Database.inUserHome());
        scopeList.setItems(scopeLines);
        targetsTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, row) -> showTarget(row));
        reload();
    }
```

```java
    private void showTarget(TargetRow row) {
        if (row == null) {
            inspectorTitle.setText("Select a target");
            domainValue.setText("-");
            profileValue.setText("-");
            createdValue.setText("-");
            scopeLines.clear();
            deleteButton.setDisable(true);
            return;
        }
        Target target = row.target();
        inspectorTitle.setText(target.label());
        domainValue.setText(target.domain());
        profileValue.setText(target.profile());
        createdValue.setText(STAMP.format(target.createdAt()));
        scopeLines.setAll(target.scopeCidrs());
        deleteButton.setDisable(false);
    }
```

with the formatter as a constant, next to the other statics:

```java
    /** Inspector timestamps use the same stamp as the scan labels. */
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
```

and imports `java.time.ZoneId`, `java.time.format.DateTimeFormatter`,
`javafx.scene.control.Button`, `javafx.scene.control.ListView`. Delete
the now unused `private MainApp main;` field and its assignment. The
existing `onDelete` already reads the table selection, so it needs no
change; `clearForm` and the validation dialogs stay.

- [ ] **Step 5: Rebuild export.fxml**

Replace `src/main/resources/com/argus/ui/view/export.fxml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.ComboBox?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.layout.Region?>
<?import javafx.scene.layout.VBox?>
<VBox spacing="16.0" styleClass="ag-pane"
      xmlns="http://javafx.com/javafx/21.0.8"
      xmlns:fx="http://javafx.com/fxml/1"
      fx:controller="com.argus.ui.controller.ExportController">
    <children>
        <Label styleClass="ag-view-title" text="Export"/>
        <VBox alignment="TOP_LEFT" maxWidth="560.0" spacing="12.0" styleClass="ag-card">
            <children>
                <Label styleClass="ag-field-label" text="TARGET"/>
                <ComboBox fx:id="targetCombo" maxWidth="Infinity" prefHeight="38.0"
                          promptText="Target" styleClass="ag-field"/>
                <Label styleClass="ag-field-label" text="FORMAT"/>
                <ComboBox fx:id="formatCombo" maxWidth="Infinity" prefHeight="38.0"
                          styleClass="ag-field"/>
                <Button maxWidth="Infinity" mnemonicParsing="false" onAction="#onExport"
                        prefHeight="44.0" styleClass="ag-primary" text="Export"/>
                <Label fx:id="statusLabel" maxWidth="Infinity" styleClass="ag-muted"
                       text="" wrapText="true"/>
            </children>
        </VBox>
        <VBox alignment="TOP_LEFT" maxWidth="560.0" spacing="6.0">
            <children>
                <Label styleClass="ag-field-label" text="THE PACKAGE CONTAINS"/>
                <Label maxWidth="560.0" styleClass="ag-muted" wrapText="true" text="Entry points ranked by priority score, every finding with its KEV and VirusTotal detail, the operator and the scan date. Markdown or JSON."/>
            </children>
        </VBox>
    </children>
</VBox>
```

Remove the unused `Insets` import. Drop `private MainApp main;` from
`ExportController` for the same reason as Targets, keeping the
`setMain(MainApp)` parameter and its body.

- [ ] **Step 6: Verify**

Run: `mvn -q test -Dtest=FxmlLoadTest+TargetsControllerTest+ExportControllerTest`
Expected: PASS. `TargetsControllerTest` must stay green, which is why
the `executor` field keeps its package visibility. Then `mvn -q verify`.

- [ ] **Step 7: Manual click path**

Targets: add a target with a scope, confirm the card row layout and that
the table lists it. Click the row: the inspector fills with domain,
profile, created and the CIDR list, and the red Delete target button
enables. Delete: CONFIRMATION, row gone, inspector back to placeholders
with the button disabled. Empty the table, the pane still lays out.
Export: pick a target and format, Export opens the chooser, the
application-modal progress dialog runs, the INFORMATION alert reports
completion.

- [ ] **Step 8: DIARY entry and hand-off commit message**

```markdown
## 2026-09-27 - UI overhaul cycle 4: targets and export panes
- **CHANGED:** `targets.fxml` rebuilt: add form as an `.ag-card` of labelled rows (label, domain, scope, profile, Add primary), `.ag-search` row, four column `.ag-table` (Label, Domain, Profile, Scope), 300px `.ag-inspector` on the right with domain, profile, created, the full CIDR `ListView`, and the row level `ag-danger` Delete target. The floating AnchorPane Delete button is gone. `TargetsController` - inspector fields, `showTarget` on selection, `STAMP` formatter, `setMain` becomes the uniform seam (unused `main` field dropped, `executor` visibility kept for the existing test). `export.fxml` rebuilt as one card (target, format, full width 44 Export primary, status line) plus a static "the package contains" block; `ExportController` drops its unused `main` field.
- **VERIFIED:** `mvn verify` green. New `FxmlLoadTest.targetsAndExportUseTokenClasses` (inspector present, delete is `ag-danger`, scope list exists, export is a card). The 2 `TargetsControllerTest` and 2 `ExportControllerTest` tests unchanged and green. Manual: add, select, delete, confirm, empty state, export round trip.
- **OPEN:** the inspector shows a target's scope, not the last scan of that target; a scan history strip is a future item.
```

Commit message for the human: `feat(ui): targets inspector, export card, no more floating delete buttons`

---

### Task 6: Results and Entry points panes

The two data panes, both with an inspector. This is where the pure
helpers land, so it carries the Review Focus items 1 to 3.

**Files:**
- Create: `src/main/java/com/argus/ui/HostDetail.java`
- Create: `src/test/java/com/argus/ui/HostDetailTest.java`
- Create: `src/test/java/com/argus/ui/controller/ResultsControllerTest.java`
- Modify: `src/main/java/com/argus/core/export/ExportService.java` (add two methods near `findingOuts`)
- Modify: `src/test/java/com/argus/core/export/ExportServiceTest.java` (add two tests)
- Modify: `src/main/resources/com/argus/ui/view/results.fxml`
- Modify: `src/main/resources/com/argus/ui/view/entrypoints.fxml`
- Modify: `src/main/java/com/argus/ui/controller/ResultsController.java`
- Modify: `src/main/java/com/argus/ui/controller/EntryPointsController.java`
- Test: `src/test/java/com/argus/ui/FxmlLoadTest.java` (add `resultsAndEntryPointsUseTokenClasses`)

**Interfaces:**
- Consumes: `PortDAO.listByScan(long)`, `FindingDAO.listByScan(long)`,
  `TargetRow.target()`, both already in use.
- Produces:
  - `com.argus.ui.HostDetail` record
    `HostDetail(String host, List<PortResult> openPorts, List<Finding> findings)`
    with `static Map<String, HostDetail> byHost(List<PortResult> ports, List<Finding> findings)`,
    `static HostDetail empty(String host)`, `int findingCount()`.
  - `ExportService.hostPortOf(Finding)` returning `String` or `null`.
  - `ExportService.findingOutsFor(List<Finding> findings, String hostPort)`
    returning `List<FindingOut>`.
  - `ResultsController.portLine(PortResult)` and
    `ResultsController.findingLine(Finding)`, both `static` and package
    visible.
  - `results.fxml` gains `inspectorTitle`, `ipValue`, `asnValue`,
    `orgValue`, `countryValue`, `portList`, `findingList`, and keeps
    `scanCombo`, `dateFilter`, `searchField`, `hostsTable`,
    `deleteButton`.
  - `entrypoints.fxml` gains `inspectorTitle`, `serviceValue`,
    `severityValue`, `scoreValue`, `findingList`, and keeps `scanCombo`,
    `entryTable`.

- [ ] **Step 1: Write the failing ExportService tests**

Add to `src/test/java/com/argus/core/export/ExportServiceTest.java`:

```java
    @Test
    void findingOutsForReturnsOnlyThatHostAndPort() {
        var findings = List.of(
                new Finding(0, null, "kev", "KEV_MATCH", "HIGH",
                        "{\"host\":\"a.example.com\",\"port\":80,\"cve\":\"CVE-2021-1\"}"),
                new Finding(0, null, "kev", "KEV_MATCH", "MEDIUM",
                        "{\"host\":\"a.example.com\",\"port\":443,\"cve\":\"CVE-2021-2\"}"));

        var out = ExportService.findingOutsFor(findings, "a.example.com:80");

        assertEquals(1, out.size());
        assertEquals("CVE-2021-1", out.getFirst().detail().split(" ")[1]);
    }

    @Test
    void findingOutsForHandlesUnreadableDetail() {
        var findings = List.of(
                new Finding(0, null, "kev", "KEV_MATCH", "HIGH", null),
                new Finding(0, null, "kev", "KEV_MATCH", "LOW", "{"));

        assertEquals(List.of(), ExportService.findingOutsFor(findings, "a.example.com:80"),
                "a corrupt detail_json costs the finding its host, never the view");
        assertEquals(List.of(), ExportService.findingOutsFor(List.of(), "a.example.com:80"));
    }
```

- [ ] **Step 2: Run them and watch them fail**

Run: `mvn -q test -Dtest=ExportServiceTest`
Expected: compile FAIL, `cannot find symbol: method findingOutsFor`.

- [ ] **Step 3: Implement the two core methods**

In `ExportService`, next to `findingOuts`:

```java
    /**
     * "host:port" from a finding's detail_json, or null when it carries
     * no host (a corrupt or absent detail must not throw).
     */
    public static String hostPortOf(Finding f) {
        JsonNode d = detail(f);
        if (d == null || !d.has("host") || !d.has("port")) {
            return null;
        }
        return d.path("host").asText() + ":" + d.path("port").asInt();
    }

    /** Finding lines for one host:port, in input order (view detail panel). */
    public static List<FindingOut> findingOutsFor(List<Finding> findings, String hostPort) {
        return findings.stream()
                .filter(f -> hostPort.equals(hostPortOf(f)))
                .map(f -> new FindingOut(f.type(), f.severity(), detailText(f)))
                .toList();
    }
```

Then simplify `entryPoints`, which builds the same map by hand, to
reuse `hostPortOf`:

```java
        for (Finding f : findings) {
            String key = hostPortOf(f);
            if (key != null) {
                byPort.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
            }
        }
```

- [ ] **Step 4: Run the core tests green**

Run: `mvn -q test -Dtest=ExportServiceTest`
Expected: PASS, including the 5 pre-existing tests (the `entryPoints`
refactor must not change ranking).

- [ ] **Step 5: Write the failing HostDetail test**

Create `src/test/java/com/argus/ui/HostDetailTest.java`:

```java
package com.argus.ui;

import com.argus.core.export.ExportService;
import com.argus.core.model.Finding;
import com.argus.core.model.PortResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Results inspector reads already loaded lists, grouped per host.
 * Pure, so it is tested without a toolkit or a database.
 */
class HostDetailTest {

    @Test
    void portsAndFindingsLandOnTheRightHost() {
        var ports = List.of(
                new PortResult("a.example.com", 80, "tcp", "http", "nginx/1.24", "", "", true),
                new PortResult("a.example.com", 22, "tcp", "ssh", "", "", "", false),
                new PortResult("b.example.com", 443, "tcp", "https", "", "", "", true));
        var findings = List.of(new Finding(0, null, "kev", "KEV_MATCH", "HIGH",
                "{\"host\":\"a.example.com\",\"port\":80,\"cve\":\"CVE-2021-1\"}"));

        Map<String, HostDetail> byHost = HostDetail.byHost(ports, findings);

        assertEquals(Set.of("a.example.com", "b.example.com"), byHost.keySet());
        assertEquals(1, byHost.get("a.example.com").openPorts().size(),
                "the closed ssh port is not open");
        assertEquals(1, byHost.get("a.example.com").findingCount());
        assertEquals(0, byHost.get("b.example.com").findingCount());
    }

    @Test
    void emptyDetailHasNothingToShow() {
        HostDetail empty = HostDetail.empty("a.example.com");

        assertTrue(empty.openPorts().isEmpty());
        assertTrue(empty.findings().isEmpty());
        assertEquals(0, empty.findingCount());
        assertEquals(Map.of(), HostDetail.byHost(List.of(), List.of()));
    }

    @Test
    void aFindingWithNoHostIsDropped() {
        var findings = List.of(new Finding(0, null, "kev", "KEV_MATCH", "HIGH", null));

        assertTrue(HostDetail.byHost(List.of(), findings).isEmpty(),
                "a corrupt detail_json must not invent a host row");
    }

    @Test
    void hostDetailReusesTheSharedHostPortContract() {
        var finding = new Finding(0, null, "kev", "KEV_MATCH", "HIGH",
                "{\"host\":\"a.example.com\",\"port\":80,\"cve\":\"CVE-2021-1\"}");

        assertEquals("a.example.com:80", ExportService.hostPortOf(finding));
        assertEquals("a.example.com:80", HostDetail.byHost(List.of(), List.of(finding))
                .keySet().iterator().next() + ":80");
    }
}
```

Add `import java.util.Set;` to the file.

- [ ] **Step 6: Run it and watch it fail**

Run: `mvn -q test -Dtest=HostDetailTest`
Expected: compile FAIL, `cannot find symbol: class HostDetail`.

- [ ] **Step 7: Implement HostDetail**

Create `src/main/java/com/argus/ui/HostDetail.java`:

```java
package com.argus.ui;

import com.argus.core.export.ExportService;
import com.argus.core.model.Finding;
import com.argus.core.model.PortResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One host's slice of an already loaded scan: its open ports and its
 * findings. Pure grouping for the Results inspector, so the controller
 * never parses detail_json and this is testable without a toolkit
 * (THREAD.md: the load stays off the FX thread, the selection does not).
 *
 * <p>Ports carry the host as {@code PortResult.host()}, the subdomain
 * string. Findings carry it inside detail_json, read through
 * {@link ExportService#hostPortOf} so the contract lives in one place.
 */
public record HostDetail(String host, List<PortResult> openPorts,
                         List<Finding> findings) {

    /** A host with nothing to show, used when the scan has no rows for it. */
    public static HostDetail empty(String host) {
        return new HostDetail(host, List.of(), List.of());
    }

    /** One entry per host that has at least one open port or one finding. */
    public static Map<String, HostDetail> byHost(List<PortResult> ports, List<Finding> findings) {
        Map<String, List<PortResult>> open = new LinkedHashMap<>();
        for (PortResult p : ports) {
            if (p.open()) {
                open.computeIfAbsent(p.host(), k -> new ArrayList<>()).add(p);
            }
        }
        Map<String, List<Finding>> found = new LinkedHashMap<>();
        for (Finding f : findings) {
            String hostPort = ExportService.hostPortOf(f);
            if (hostPort == null) {
                continue;
            }
            int colon = hostPort.lastIndexOf(':');
            found.computeIfAbsent(hostPort.substring(0, colon), k -> new ArrayList<>()).add(f);
        }
        Map<String, HostDetail> out = new LinkedHashMap<>();
        open.forEach((host, own) -> out.put(host, new HostDetail(host, List.copyOf(own), List.of())));
        found.forEach((host, own) -> out.merge(host, new HostDetail(host, List.of(), List.copyOf(own)),
                (a, b) -> new HostDetail(host, a.openPorts(), b.findings())));
        return out;
    }

    public int findingCount() {
        return findings.size();
    }
}
```

- [ ] **Step 8: Run HostDetail green**

Run: `mvn -q test -Dtest=HostDetailTest`
Expected: PASS, 4 tests.

- [ ] **Step 9: Write the failing line-format test**

Create `src/test/java/com/argus/ui/controller/ResultsControllerTest.java`:

```java
package com.argus.ui.controller;

import com.argus.core.model.Finding;
import com.argus.core.model.PortResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The inspector's two line formats, pinned (headless: no toolkit). */
class ResultsControllerTest {

    @Test
    void portLineCarriesPortServiceAndVersion() {
        var line = ResultsController.portLine(new PortResult(
                "a.example.com", 80, "tcp", "http", "nginx/1.24", "", "", true));

        assertEquals("80/tcp  http  nginx/1.24", line);
    }

    @Test
    void portLineSurvivesAMissingService() {
        var line = ResultsController.portLine(new PortResult(
                "a.example.com", 8443, "tcp", null, "", "", "", true));

        assertEquals("8443/tcp  -  -", line);
    }

    @Test
    void findingLineCarriesSeverityThenType() {
        var line = ResultsController.findingLine(
                new Finding(0, null, "kev", "KEV_MATCH", "HIGH", "{}"));

        assertEquals("HIGH  KEV_MATCH", line);
    }
}
```

- [ ] **Step 10: Run it and watch it fail**

Run: `mvn -q test -Dtest=ResultsControllerTest`
Expected: compile FAIL, `cannot find symbol: method portLine`.

- [ ] **Step 11: Rebuild results.fxml**

Replace `src/main/resources/com/argus/ui/view/results.fxml`. The table
keeps four columns; ASN and Org move to the inspector, where the search
still reaches them. `Delete scan` is scan level, so it stays in the
header with the other scan level controls:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.ComboBox?>
<?import javafx.scene.control.DatePicker?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.ListView?>
<?import javafx.scene.control.Separator?>
<?import javafx.scene.control.TableColumn?>
<?import javafx.scene.control.TableView?>
<?import javafx.scene.control.TextField?>
<?import javafx.scene.control.cell.PropertyValueFactory?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.Region?>
<?import javafx.scene.layout.VBox?>
<VBox spacing="16.0" styleClass="ag-pane"
      xmlns="http://javafx.com/javafx/21.0.8"
      xmlns:fx="http://javafx.com/fxml/1"
      fx:controller="com.argus.ui.controller.ResultsController">
    <children>
        <HBox alignment="BOTTOM_LEFT" spacing="12.0">
            <children>
                <VBox alignment="TOP_LEFT" spacing="2.0" HBox.hgrow="ALWAYS">
                    <children>
                        <Label styleClass="ag-view-title" text="Scan results"/>
                        <Label fx:id="scanMetaLabel" maxWidth="Infinity" styleClass="ag-sub"
                               text="No scan selected"/>
                    </children>
                </VBox>
                <ComboBox fx:id="scanCombo" prefHeight="34.0" prefWidth="380.0"
                          promptText="Scan" styleClass="ag-field"/>
                <DatePicker fx:id="dateFilter" prefHeight="34.0" promptText="Any date"
                            styleClass="ag-field"/>
                <Button fx:id="deleteButton" mnemonicParsing="false" onAction="#onDelete"
                        prefHeight="34.0" styleClass="ag-danger" text="Delete scan"/>
            </children>
        </HBox>

        <TextField fx:id="searchField" maxWidth="Infinity" promptText="Search hosts, IP, country, ASN or org"
                   styleClass="ag-search"/>

        <HBox spacing="16.0" VBox.vgrow="ALWAYS">
            <children>
                <TableView fx:id="hostsTable" maxWidth="Infinity" styleClass="ag-table"
                           HBox.hgrow="ALWAYS" VBox.vgrow="ALWAYS">
                    <columns>
                        <TableColumn prefWidth="260.0" text="Host">
                            <cellValueFactory>
                                <PropertyValueFactory property="subdomain"/>
                            </cellValueFactory>
                        </TableColumn>
                        <TableColumn prefWidth="150.0" text="IP">
                            <cellValueFactory>
                                <PropertyValueFactory property="ip"/>
                            </cellValueFactory>
                        </TableColumn>
                        <TableColumn prefWidth="140.0" text="Country">
                            <cellValueFactory>
                                <PropertyValueFactory property="country"/>
                            </cellValueFactory>
                        </TableColumn>
                        <TableColumn prefWidth="110.0" text="Status">
                            <cellValueFactory>
                                <PropertyValueFactory property="alive"/>
                            </cellValueFactory>
                        </TableColumn>
                    </columns>
                </TableView>

                <VBox alignment="TOP_LEFT" maxWidth="300.0" minWidth="300.0"
                      prefWidth="300.0" spacing="10.0" styleClass="ag-inspector"
                      HBox.hgrow="NEVER">
                    <children>
                        <Label fx:id="inspectorTitle" maxWidth="Infinity"
                               styleClass="ag-value" text="Select a host"/>
                        <Separator styleClass="ag-rule"/>
                        <Label styleClass="ag-field-label" text="IP"/>
                        <Label fx:id="ipValue" maxWidth="Infinity" styleClass="ag-value" text="-"/>
                        <Label styleClass="ag-field-label" text="ASN"/>
                        <Label fx:id="asnValue" maxWidth="Infinity" styleClass="ag-value" text="-"/>
                        <Label styleClass="ag-field-label" text="ORG"/>
                        <Label fx:id="orgValue" maxWidth="Infinity" styleClass="ag-value" text="-"/>
                        <Label styleClass="ag-field-label" text="COUNTRY"/>
                        <Label fx:id="countryValue" maxWidth="Infinity" styleClass="ag-value" text="-"/>
                        <Label styleClass="ag-field-label" text="OPEN PORTS"/>
                        <ListView fx:id="portList" maxWidth="Infinity" prefHeight="120.0"
                                  styleClass="ag-list"/>
                        <Label styleClass="ag-field-label" text="FINDINGS"/>
                        <ListView fx:id="findingList" maxWidth="Infinity" prefHeight="120.0"
                                  styleClass="ag-list" VBox.vgrow="ALWAYS"/>
                    </children>
                </VBox>
            </children>
        </HBox>
    </children>
</VBox>
```

Remove the unused `Insets` import. `Entry points` and `Back` buttons are
gone: the sidebar is the navigation now.

- [ ] **Step 12: Wire the Results inspector**

In `ResultsController`, add the fields and the detail state:

```java
    @FXML
    private Label scanMetaLabel;
    @FXML
    private Label inspectorTitle;
    @FXML
    private Label ipValue;
    @FXML
    private Label asnValue;
    @FXML
    private Label orgValue;
    @FXML
    private Label countryValue;
    @FXML
    private ListView<String> portList;
    @FXML
    private ListView<String> findingList;

    private final ObservableList<String> portLines = FXCollections.observableArrayList();
    private final ObservableList<String> findingLines = FXCollections.observableArrayList();
    private Map<String, HostDetail> details = Map.of();
```

In `initialize()`, add the selection listener after the search listener:

```java
        portList.setItems(portLines);
        findingList.setItems(findingLines);
        hostsTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, row) -> showDetail(row));
```

Replace `loadHosts` so one off-FX load brings the table and the detail
together, and clears the inspector when the scan changes (Review Focus 3):

```java
    private void loadHosts(long scanId) {
        executor.execute(() -> {
            try {
                List<Host> hosts = hostDao.listByScan(scanId);
                List<PortResult> ports = new PortDAO(Database.inUserHome()).listByScan(scanId);
                List<Finding> findings = new FindingDAO(Database.inUserHome()).listByScan(scanId);
                List<HostRow> fresh = hosts.stream().map(HostRow::new).toList();
                Map<String, HostDetail> grouped = HostDetail.byHost(ports, findings);
                Platform.runLater(() -> {
                    rows.setAll(fresh);
                    details = grouped;
                    showDetail(null);
                    scanMetaLabel.setText(metaFor(hosts.size(), ports, findings));
                });
            } catch (SQLException e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Database failure.").showAndWait());
            }
        });
    }
```

```java
    private static String metaFor(int hostCount, List<PortResult> ports, List<Finding> findings) {
        long open = ports.stream().filter(PortResult::open).count();
        return hostCount + " hosts · " + open + " open ports · " + findings.size() + " findings";
    }
```

```java
    private void showDetail(HostRow row) {
        if (row == null) {
            inspectorTitle.setText("Select a host");
            ipValue.setText("-");
            asnValue.setText("-");
            orgValue.setText("-");
            countryValue.setText("-");
            portLines.clear();
            findingLines.clear();
            return;
        }
        HostDetail detail = details.getOrDefault(row.getSubdomain(), HostDetail.empty(row.getSubdomain()));
        inspectorTitle.setText(row.getSubdomain());
        ipValue.setText(orDash(row.getIp()));
        asnValue.setText(orDash(row.getAsn()));
        orgValue.setText(orDash(row.getOrg()));
        countryValue.setText(orDash(row.getCountry()));
        portLines.setAll(detail.openPorts().stream().map(ResultsController::portLine).toList());
        findingLines.setAll(detail.findings().stream().map(ResultsController::findingLine).toList());
    }

    /** Pure, so the line formats are unit tested. */
    static String portLine(PortResult p) {
        return p.port() + "/" + p.protocol() + "  " + orDash(p.service()) + "  " + orDash(p.version());
    }

    static String findingLine(Finding f) {
        return orDash(f.severity()) + "  " + orDash(f.type());
    }

    private static String orDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
```

Delete `onEntryPoints` and `onBack`, the `IOException` import, and the
`main` field, keeping the `setMain(MainApp)` parameter. Add imports
`com.argus.core.model.Finding`, `com.argus.core.model.Host`,
`com.argus.core.model.PortResult`, `com.argus.db.FindingDAO`,
`com.argus.db.PortDAO`, `com.argus.ui.HostDetail`,
`javafx.scene.control.Label`, `javafx.scene.control.ListView`,
`java.util.Map`.

- [ ] **Step 13: Rebuild entrypoints.fxml**

Replace `src/main/resources/com/argus/ui/view/entrypoints.fxml`. All
seven columns stay at reduced widths; the KEV column truncates and the
controller gives it a tooltip:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.control.ComboBox?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.ListView?>
<?import javafx.scene.control.Separator?>
<?import javafx.scene.control.TableColumn?>
<?import javafx.scene.control.TableView?>
<?import javafx.scene.control.cell.PropertyValueFactory?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.Region?>
<?import javafx.scene.layout.VBox?>
<VBox spacing="16.0" styleClass="ag-pane"
      xmlns="http://javafx.com/javafx/21.0.8"
      xmlns:fx="http://javafx.com/fxml/1"
      fx:controller="com.argus.ui.controller.EntryPointsController">
    <children>
        <HBox alignment="CENTER_LEFT" spacing="12.0">
            <children>
                <Label styleClass="ag-view-title" text="Entry points"/>
                <ComboBox fx:id="scanCombo" maxWidth="Infinity" prefHeight="34.0"
                          promptText="Scan" styleClass="ag-field" HBox.hgrow="ALWAYS"/>
            </children>
        </HBox>

        <HBox spacing="16.0" VBox.vgrow="ALWAYS">
            <children>
                <TableView fx:id="entryTable" maxWidth="Infinity" styleClass="ag-table"
                           HBox.hgrow="ALWAYS" VBox.vgrow="ALWAYS">
                    <columns>
                        <TableColumn prefWidth="50.0" text="Rank">
                            <cellValueFactory>
                                <PropertyValueFactory property="rank"/>
                            </cellValueFactory>
                        </TableColumn>
                        <TableColumn prefWidth="170.0" text="Host:Port">
                            <cellValueFactory>
                                <PropertyValueFactory property="hostPort"/>
                            </cellValueFactory>
                        </TableColumn>
                        <TableColumn prefWidth="80.0" text="Service">
                            <cellValueFactory>
                                <PropertyValueFactory property="service"/>
                            </cellValueFactory>
                        </TableColumn>
                        <TableColumn fx:id="severityColumn" prefWidth="90.0" text="Severity">
                            <cellValueFactory>
                                <PropertyValueFactory property="severity"/>
                            </cellValueFactory>
                        </TableColumn>
                        <TableColumn fx:id="kevColumn" prefWidth="120.0" text="KEV">
                            <cellValueFactory>
                                <PropertyValueFactory property="kev"/>
                            </cellValueFactory>
                        </TableColumn>
                        <TableColumn prefWidth="70.0" text="VT">
                            <cellValueFactory>
                                <PropertyValueFactory property="vt"/>
                            </cellValueFactory>
                        </TableColumn>
                        <TableColumn prefWidth="60.0" text="Score">
                            <cellValueFactory>
                                <PropertyValueFactory property="score"/>
                            </cellValueFactory>
                        </TableColumn>
                    </columns>
                </TableView>

                <VBox alignment="TOP_LEFT" maxWidth="300.0" minWidth="300.0"
                      prefWidth="300.0" spacing="10.0" styleClass="ag-inspector"
                      HBox.hgrow="NEVER">
                    <children>
                        <Label fx:id="inspectorTitle" maxWidth="Infinity"
                               styleClass="ag-value" text="Select an entry point"/>
                        <Separator styleClass="ag-rule"/>
                        <Label styleClass="ag-field-label" text="SERVICE"/>
                        <Label fx:id="serviceValue" maxWidth="Infinity" styleClass="ag-value" text="-"/>
                        <Label styleClass="ag-field-label" text="SEVERITY"/>
                        <Label fx:id="severityValue" maxWidth="Infinity" styleClass="ag-value" text="-"/>
                        <Label styleClass="ag-field-label" text="SCORE"/>
                        <Label fx:id="scoreValue" maxWidth="Infinity" styleClass="ag-value" text="-"/>
                        <Label styleClass="ag-field-label" text="FINDINGS"/>
                        <ListView fx:id="findingList" maxWidth="Infinity" prefHeight="240.0"
                                  styleClass="ag-list" VBox.vgrow="ALWAYS"/>
                    </children>
                </VBox>
            </children>
        </HBox>
    </children>
</VBox>
```

The KEV column is 120 wide and the sheet gives it `.ag-kev`
(monospace, 11), so the controller adds the class in Step 14.

- [ ] **Step 14: Wire the Entry points inspector**

In `EntryPointsController`, keep the raw lists the load already fetches
and add the inspector:

```java
    @FXML
    private TableColumn<EntryPointsRow, String> severityColumn;
    @FXML
    private TableColumn<EntryPointsRow, String> kevColumn;
    @FXML
    private Label inspectorTitle;
    @FXML
    private Label serviceValue;
    @FXML
    private Label severityValue;
    @FXML
    private Label scoreValue;
    @FXML
    private ListView<String> findingList;

    private final ObservableList<String> findingLines = FXCollections.observableArrayList();
    private List<Finding> findings = List.of();
```

In `initialize()`:

```java
        findingList.setItems(findingLines);
        kevColumn.getStyleClass().add("ag-kev");
        kevColumn.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(EntryPointsRow item, boolean empty) {
                super.updateItem(item, empty);
                String kev = empty || item == null ? null : item.getKev();
                setText(kev);
                setTooltip(kev == null || "-".equals(kev) ? null : new Tooltip(kev));
            }
        });
        severityColumn.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(EntryPointsRow item, boolean empty) {
                super.updateItem(item, empty);
                String severity = empty || item == null ? null : item.getSeverity();
                setText(severity);
                getStyleClass().removeAll("ag-status-ok", "ag-status-warn",
                        "ag-status-danger", "ag-status-info");
                if (severity != null && !"-".equals(severity)) {
                    getStyleClass().add(severityClass(severity));
                }
            }
        });
        entryTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, row) -> showDetail(row));
```

```java
    /** Severity to status colour, worst first. */
    static String severityClass(String severity) {
        return switch (severity) {
            case "CRITICAL", "HIGH" -> "ag-status-danger";
            case "MEDIUM" -> "ag-status-warn";
            default -> "ag-status-info";
        };
    }
```

Replace `loadEntries` so the findings survive for the inspector, and
clear it when the scan changes:

```java
    private void loadEntries(long scanId) {
        worker.execute(() -> {
            try {
                List<PortResult> ports = portDao.listByScan(scanId);
                List<Finding> loaded = findingDao.listByScan(scanId);
                List<EntryPointsRow> fresh = ExportService.entryPoints(ports, loaded)
                        .stream().map(EntryPointsRow::new).toList();
                Platform.runLater(() -> {
                    findings = loaded;
                    rows.setAll(fresh);
                    showDetail(null);
                });
            } catch (SQLException e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Database failure.").showAndWait());
            }
        });
    }
```

```java
    private void showDetail(EntryPointsRow row) {
        if (row == null) {
            inspectorTitle.setText("Select an entry point");
            serviceValue.setText("-");
            severityValue.setText("-");
            scoreValue.setText("-");
            findingLines.clear();
            return;
        }
        EntryPoint entry = row.entry();
        inspectorTitle.setText(entry.hostPort());
        serviceValue.setText(entry.service());
        severityValue.setText(entry.severity());
        scoreValue.setText(String.valueOf(entry.score()));
        findingLines.setAll(ExportService
                .findingOutsFor(findings, entry.hostPort()).stream()
                .map(EntryPointsController::line)
                .toList());
    }

    static String line(FindingOut out) {
        return out.severity() + "  " + out.type() + "  " + out.detail();
    }
```

Delete `onBack`, the `IOException` import and the `main` field, keeping
the `setMain(MainApp)` parameter. Add imports
`com.argus.core.export.ExportService.EntryPoint`,
`com.argus.core.export.ExportService.FindingOut`,
`com.argus.core.model.Finding`, `com.argus.core.model.PortResult`,
`javafx.scene.control.Label`, `javafx.scene.control.ListView`,
`javafx.scene.control.TableCell`, `javafx.scene.control.TableColumn`,
`javafx.scene.control.Tooltip`.

- [ ] **Step 15: Add the two pane layout tests**

Add to `FxmlLoadTest`:

```java
    @Test
    void resultsAndEntryPointsUseTokenClasses() throws IOException {
        FXMLLoader results = loader("results");
        Parent hosts = results.load();
        assertEquals(1, hosts.lookupAll(".ag-inspector").size());
        assertEquals(4, hosts.lookupAll(".ag-table .table-column").size(),
                "ASN and Org moved into the inspector");
        assertTrue(hosts.lookup("#deleteButton").getStyleClass().contains("ag-danger"));

        FXMLLoader entry = loader("entrypoints");
        Parent points = entry.load();
        assertEquals(7, points.lookupAll(".ag-table .table-column").size(),
                "entry points keeps all seven columns");
        assertEquals(1, points.lookupAll(".ag-inspector").size());
    }
```

- [ ] **Step 16: Verify the whole suite**

Run: `mvn -q verify`
Expected: green, with 4 new `HostDetailTest`, 3 new `ResultsControllerTest`
and 2 new `ExportServiceTest` tests on top of the Task 5 total.

- [ ] **Step 17: Manual click path**

Results: pick a scan, four columns of hosts, the header shows the host
and port counts. Click a row: the inspector shows IP, ASN, Org, Country
and the open ports with service and version, and the findings with
severity. Click a dead host: "-" where nothing was enriched, empty lists.
Switch scan: the inspector resets, no stale ports. Search "Test Org":
rows filter, and the org is still searchable although it left the table.
Delete scan: CONFIRMATION, gone from the combo.

Entry points: pick a scan, seven ranked rows, KEV in monospace, hover a
KEV cell for the full list, severity coloured worst first. Click a row:
the inspector lists that port's findings with CVE and confidence, and
VirusTotal engine counts. Click a row with no findings: empty list, no
crash.

- [ ] **Step 18: DIARY entry and hand-off commit message**

```markdown
## 2026-09-27 - UI overhaul cycle 5: results and entry points panes (old cycle C)
- **CHANGED:** core - `ExportService.hostPortOf(f)` and `findingOutsFor(findings, hostPort)`, and `entryPoints` now builds its map through `hostPortOf` instead of re-parsing detail_json (one contract, one place). `ui/HostDetail` (new, pure): open ports and findings grouped per host subdomain, empty host gives empty lists. `results.fxml` - four column table (Host, IP, Country, Status), scan level controls in the header with the `ag-danger` Delete scan, `.ag-search` row, 300px `.ag-inspector` (IP, ASN, Org, Country, open ports, findings) and a `scanMetaLabel` count line; the Entry points and Back buttons are gone (sidebar navigates). ASN/Org moved into the inspector, still searchable. `entrypoints.fxml` - all seven columns kept at reduced widths, `.ag-kev` monospace with a per cell Tooltip, `.ag-inspector` listing the selected port's findings. `ResultsController` - one off-FX load brings hosts, ports and findings, `showDetail` renders from the grouped map, `portLine`/`findingLine` pure, `metaFor` count line, `setMain` uniform seam. `EntryPointsController` - keeps the raw findings for the inspector, `severityClass` colouring, KEV tooltip, `setMain` uniform seam.
- **VERIFIED:** `mvn verify` green. New: `HostDetailTest` (4 - grouping, empty detail, corrupt detail_json dropped, shared host:port contract), `ResultsControllerTest` (3 - port line, missing service, finding line), `ExportServiceTest` +2 (filtering, unreadable detail), `FxmlLoadTest` +2 (inspectors present, column counts). Review Focus 1 to 3 covered: corrupt detail_json cannot throw, an empty scan shows placeholders, switching scan clears the inspector. Manual: select, dead host, switch scan, search by org, delete scan, KEV tooltip, severity colours, port with no findings.
- **OPEN:** the Results inspector has no row level action (nothing to act on); if one is ever wanted it goes in the inspector footer per the placement rules.
```

Commit message for the human: `feat(ui): results and entry point inspectors, shared finding host:port contract`

---

### Task 7: API keys dialog, legacy deletion, layout floor, docs

**Files:**
- Modify: `src/main/resources/com/argus/ui/view/apikeys.fxml`
- Modify: `src/main/java/com/argus/ui/MainApp.java` (delete four scene switches)
- Modify: `src/main/java/com/argus/ui/controller/ShellController.java` (host all six panes)
- Modify: `JAVAFX.md`, `DIARY.md`
- Test: `src/test/java/com/argus/ui/LayoutFloorTest.java` (new)
- Test: `src/test/java/com/argus/ui/CssContractTest.java` (add the migration test)

**Interfaces:**
- Consumes: everything from Tasks 1 to 6.
- Produces: `FxmlLoadTest.onFx(Supplier)` for the layout test. Deleted:
  `MainApp.showTargets`, `showResults`, `showEntryPoints`, `showExport`.
  Kept: `showLogin`, `showShell`, `showLock`, `showAddApiKey`.

- [ ] **Step 1: Add the migration test to CssContractTest**

Add to `src/test/java/com/argus/ui/CssContractTest.java`:

```java
    /** After the overhaul no view may use a pre-overhaul class name. */
    @Test
    void noLegacyStyleClassesRemain() throws IOException {
        var legacy = List.of("striped", "title-label", "status-label",
                "text-muted", "title-2", "flat", "accent", "danger");
        Set<String> found = new LinkedHashSet<>();
        for (String name : FXML) {
            for (String token : matches(resource("/com/argus/ui/view/" + name + ".fxml"))) {
                if (!token.startsWith("ag-")) {
                    found.add(token);
                }
            }
        }
        found.remove("root-pane");

        Set<String> offenders = new LinkedHashSet<>();
        for (String token : found) {
            for (String old : legacy) {
                if (token.equals(old)) {
                    offenders.add(token);
                }
            }
        }

        assertEquals(Set.of(), offenders,
                "pre-overhaul class names still in an FXML: " + offenders);
    }
```

- [ ] **Step 2: Run it and watch it fail**

Run: `mvn -q test -Dtest=CssContractTest#noLegacyStyleClassesRemain`
Expected: FAIL, listing `striped`, `title-label`, `title-2`,
`status-label`, `text-muted` from the FXMLs still to be rebuilt
(`apikeys.fxml` is the last one).

- [ ] **Step 3: Rebuild apikeys.fxml**

Replace `src/main/resources/com/argus/ui/view/apikeys.fxml`. The dialog
sizes to its content (480x400) because the root declares those
preferences, so `MainApp.showAddApiKey` needs no geometry change. The
footer obeys rule 6: destructive on the left group, close plus primary on
the right, 24px apart:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.ComboBox?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.PasswordField?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.Region?>
<?import javafx.scene.layout.StackPane?>
<?import javafx.scene.layout.VBox?>
<StackPane prefHeight="400.0" prefWidth="480.0" styleClass="root-pane"
           xmlns="http://javafx.com/javafx/21.0.8"
           xmlns:fx="http://javafx.com/fxml/1"
           fx:controller="com.argus.ui.controller.AddApiKeyController">
    <children>
        <VBox alignment="TOP_LEFT" maxWidth="430.0" spacing="12.0" styleClass="ag-card">
            <children>
                <Label styleClass="ag-view-title" text="API keys"/>
                <Label fx:id="statusLabel" maxWidth="Infinity" styleClass="ag-mono"
                       text="" wrapText="true"/>
                <Label styleClass="ag-field-label" text="PROVIDER"/>
                <ComboBox fx:id="providerCombo" maxWidth="Infinity" prefHeight="38.0"
                          styleClass="ag-field"/>
                <Label styleClass="ag-field-label" text="API KEY"/>
                <PasswordField fx:id="keyField" maxWidth="Infinity" prefHeight="38.0"
                               promptText="Never displayed after save" styleClass="ag-key"/>
                <Label fx:id="messageLabel" maxWidth="Infinity" styleClass="ag-muted"
                       text="" wrapText="true"/>
                <Region VBox.vgrow="ALWAYS"/>
                <HBox spacing="24.0">
                    <children>
                        <Button fx:id="removeButton" maxWidth="Infinity" mnemonicParsing="false"
                                onAction="#onRemove" prefHeight="38.0" styleClass="ag-danger"
                                text="Remove" HBox.hgrow="ALWAYS"/>
                        <Button fx:id="saveButton" maxWidth="Infinity" mnemonicParsing="false"
                                onAction="#onSave" prefHeight="38.0" styleClass="ag-primary"
                                text="Save" HBox.hgrow="ALWAYS"/>
                        <Button mnemonicParsing="false" onAction="#onClose"
                                prefHeight="38.0" styleClass="ag-link" text="Close"/>
                    </children>
                </HBox>
            </children>
        </VBox>
    </children>
</StackPane>
```

Remove the unused `Insets` import. `AddApiKeyController` keeps all its
ids (`statusLabel`, `providerCombo`, `keyField`, `messageLabel`,
`saveButton`, `removeButton`) and its `onClose`, so no Java change.

- [ ] **Step 4: Delete the legacy scene switches**

In `MainApp`, delete `showTargets`, `showResults`, `showEntryPoints` and
`showExport` in full, plus their now unused imports
(`com.argus.ui.controller.EntryPointsController`,
`com.argus.ui.controller.ResultsController`,
`com.argus.ui.controller.TargetsController`,
`com.argus.ui.controller.ExportController`).

In `ShellController`, replace `onNavResults`, `onNavEntryPoints`,
`onNavTargets` and `onNavExport` with the pane swap used by Dashboard,
and make the wiring uniform for all six:

```java
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
```

```java
    @FXML
    private void onNavResults() {
        showResults();
    }

    @FXML
    private void onNavEntryPoints() {
        showEntryPoints();
    }
```

Keep `onNavDashboard`, `onNavTargets`, `onNavExport`, `onNavApiKeys`,
`onLock` and `onLogout` as they are. Add the imports
`com.argus.ui.controller.ResultsController` and
`com.argus.ui.controller.EntryPointsController`.

- [ ] **Step 5: Add the layout floor test**

Create `src/test/java/com/argus/ui/LayoutFloorTest.java`. Review Focus
item 4: at the 1100x640 minimum the content area is 880x612, and every
pane must still have visible content rather than a clipped zero-height
node. The scene graph must be built on the FX thread, so this reuses an
`onFx` helper.

```java
package com.argus.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * At the 1100x640 minimum the shell content area is 880x612. Every pane
 * must lay out at that size with real content, not a clipped node
 * (Review Focus 4 of the spec plan).
 */
class LayoutFloorTest {

    /** What must survive the squeeze, per pane. */
    private record Pane(String fxml, String mustHaveHeight) {
    }

    private static final Pane[] PANES = {
            new Pane("dashboard", ".ag-tile"),
            new Pane("targets", ".ag-table"),
            new Pane("results", ".ag-table"),
            new Pane("entrypoints", ".ag-table"),
            new Pane("export", ".ag-card"),
    };

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // toolkit already running in this JVM
        }
    }

    @Test
    void everyPaneLaysOutAtTheMinimumWindowSize() throws Exception {
        for (Pane pane : PANES) {
            Parent root = load(pane.fxml());
            onFx(() -> {
                Scene scene = new Scene(root, 880, 612);
                root.applyCss();
                root.layout();
                return null;
            });

            javafx.scene.Node node = onFx(() -> root.lookup(pane.mustHaveHeight()));
            assertTrue(node != null, pane.fxml() + " has no " + pane.mustHaveHeight());
            double height = onFx(node::getBoundsInLocal).getHeight();
            assertTrue(height > 0, pane.fxml() + " collapsed " + pane.mustHaveHeight()
                    + " to zero height at 880x612");
        }
    }

    @Test
    void aTableKeepsRowsVisibleAtTheMinimum() throws Exception {
        Parent root = load("results");
        onFx(() -> {
            new Scene(root, 880, 612);
            root.applyCss();
            root.layout();
            return null;
        });

        TableView<?> table = (TableView<?>) onFx(() -> root.lookup("#hostsTable"));
        assertTrue(table != null, "results lost its table");
        double height = onFx(table::getBoundsInLocal).getHeight();
        assertTrue(height > 100, "the results table is only " + height
                + "px tall at the minimum window size");
    }

    private static Parent load(String name) throws Exception {
        URL fxml = LayoutFloorTest.class.getResource("/com/argus/ui/view/" + name + ".fxml");
        if (fxml == null) {
            fail("missing resource: " + name + ".fxml");
        }
        return onFx(() -> new FXMLLoader(fxml).load());
    }

    /** Runs on the FX thread and waits: a scene graph is not thread safe. */
    private static <T> T onFx(Supplier<T> work) throws Exception {
        var result = new AtomicReference<T>();
        var error = new AtomicReference<Throwable>();
        var done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(work.get());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(20, TimeUnit.SECONDS)) {
            fail("the FX thread did not answer within 20s");
        }
        if (error.get() != null) {
            throw new IllegalStateException(error.get());
        }
        return result.get();
    }
}
```

- [ ] **Step 6: Run the layout test**

Run: `mvn -q test -Dtest=LayoutFloorTest`
Expected: PASS, 2 tests. If a pane fails, the fix is in that pane's
FXML (a missing `VBox.vgrow="ALWAYS"` or a fixed `prefHeight` that
cannot shrink), never in the test.

- [ ] **Step 7: Run the full gate**

Run: `mvn -q verify`
Expected: green, including `CssContractTest.noLegacyStyleClassesRemain`
and every pre-existing test. The suite must not lose a single test from
the `3fc8394` baseline.

- [ ] **Step 8: Amend JAVAFX.md**

Add to the `## MainApp` section:

```markdown
- Every main window opens at 1280x720 with a 1100x640 minimum, set in
  one place: `MainApp.applyGeometry(scene, stage)`. Dialogs size to
  their content.
```

Add to `## FXML rules`:

```markdown
- No inline `style=` in FXML and no `setStyle` in controllers. Every
  Argus class is namespaced `ag-*` and lives in `application.css`;
  `CssContractTest` proves the FXMLs, the controllers and the sheet agree.
- Panes are `VBox`/`HBox`/`GridPane` with a `ag-pane` root, never
  `AnchorPane` coordinates. Uppercase label text is authored uppercase:
  CSS has no `text-transform`.
```

Add a new section after `## Styling`:

```markdown
## Design system (2026-09-27 overhaul)
- Palette tokens in `application.css`: background `#0d1117`, chrome
  `#10151c`, panel `#11161d`, selected `#1b2430`, border `#21262d` and
  `#30363d`, text `#e6edf3` / `#8b949e` / `#6e7681`, accent `#58a6ff`,
  primary `#238636`, destructive `#b62324`, ok `#3fb950`, warn `#d29922`,
  danger text `#ff7b72`.
- Buttons: one primary per view (filled green), secondary, destructive
  (filled red), text link. 34 tall in the shell, 44 in auth, 6 radius.
- Placement: title and scan metadata left in the header, filters and
  actions right with the primary last; search is always its own full
  width row; row level destructive actions live in the inspector footer,
  scan level ones in the header; destructive never adjacent to the
  primary.
- Inspector: Results, Entry points and Targets pair a table with a
  300px right hand inspector. Its data comes from the same off-FX load as
  the table (`ui/HostDetail` for the per host grouping), so selecting a
  row never touches the database.
```

- [ ] **Step 9: Final DIARY entry**

```markdown
## 2026-09-27 - UI overhaul cycle 6: api keys dialog, legacy deletion, layout floor
- **CHANGED:** `apikeys.fxml` rebuilt as a content sized card (480x400, no geometry change needed in `showAddApiKey`): provider and key as labelled rows, `.ag-key` monospace key field, footer with the `ag-danger` Remove on the left group and Close plus `ag-primary` Save on the right, 24px apart. Legacy deletion: `MainApp.showTargets/showResults/showEntryPoints/showExport` gone with their imports; `ShellController` now hosts all six panes and the sidebar is the only navigation. `JAVAFX.md` amended (geometry, no inline styles, the design system, the inspector pattern).
- **VERIFIED:** `mvn verify` green. New `LayoutFloorTest` (2) lays every pane out at the 880x612 content floor and requires a non collapsed node plus a usable results table; `CssContractTest.noLegacyStyleClassesRemain` proves no pre-overhaul class name survives. No test from the `3fc8394` baseline was lost. Manual: API keys save, verify and remove in the new footer; every nav item swaps panes with the selection following; nothing clips at the minimum.
- **OPEN:** `.superpowers/` added to `.gitignore`. `docs/superpowers/specs/2026-09-27-ui-shell-design.md` stays as history, superseded by this cycle's spec.
```

- [ ] **Step 10: Hand-off commit messages**

One commit for the human, or one per task:
`feat(ui): api keys dialog, sidebar is the only navigation, layout floor test`

---

## Plan self-review

**Spec coverage.** Window geometry: Task 2. Design system, palette,
button variants, placement rules, the mark: Task 1. Auth split frame:
Task 3. Shell frame: Task 2. Dashboard: Task 4. Targets and Export:
Task 5. Results and Entry points inspectors: Task 6. API Keys: Task 7.
Threading and data flow unchanged: no task touches a worker or a
`Platform.runLater` hop except to add one grouped load (Task 6 Step 12,
still one hop, still off FX). Error handling: dialogs unchanged, plus
the inspector placeholders (Task 5 Step 4, Task 6 Steps 12 and 14).
`HostDetail` and `findingOutsFor`: Task 6. JAVAFX.md amendments: Task 7
Step 8. DIARY: every task.

**Placeholder scan.** No TBD, no "similar to Task N", no "add error
handling". The two spots that needed a judgement call are stated as
decisions: a pane that reads no session state keeps the `setMain`
parameter and drops the field (Global Constraints), and a `LayoutFloorTest`
failure is fixed in the FXML, never in the test (Task 7 Step 6).

**Type consistency.** `ag-*` inventory is declared once in Task 1 and
every later task uses names from it. `HostDetail.byHost/empty/findingCount`,
`ExportService.hostPortOf/findingOutsFor`, `ResultsController.portLine/findingLine`,
`EntryPointsController.severityClass/line` are each defined in the step that
introduces them and used only after it. The `setMain(MainApp)` seam is used
by `ShellController` in Tasks 5 and 7 for controllers that no longer store
the field, which is the stated rule.

**Review Focus.** Items 1, 2 and 3 are pinned by tests in Task 6
(`findingOutsForHandlesUnreadableDetail`, `emptyDetailHasNothingToShow`,
and the `showDetail(null)` reset reviewed in Step 12). Item 4 is pinned
by `LayoutFloorTest` in Task 7 plus a click path in every task. Item 5 is
pinned by `CssContractTest` in Task 1, both directions.
