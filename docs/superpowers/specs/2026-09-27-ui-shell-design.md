# UI Shell Redesign — Spec (2026-09-27)

## Intent
Replace the childish fixed-size scene-switch UI with a professional
sidebar-shell application without breaking any working feature.
Approved path: Approach 1 (sidebar shell, migrate view-by-view).
Non-destructive: every cycle ends green and shippable.

## Non-goals
- No core/db/pipeline changes. No new dependencies (charts are
  JavaFX built-in). No controller business-logic rewrites.

## Architecture
- `shell.fxml` (`BorderPane`) on the primary Stage: left sidebar nav
  (Dashboard, Targets, Results, Entry Points, Export; key status +
  lock/logout pinned bottom), center `StackPane` content area (one
  loaded view at a time), bottom status bar (scan state, provider
  states, operator).
- `ShellController`: owns nav selection + content swapping only.
  Views load via FXMLLoader into the content area.
- `MainApp` keeps the Stage and session; gains `showInShell(name)`.
  Old `showX()` methods stay until their view migrates, then are
  deleted with the dead FXML navigation path.
- Secondary Stages (owner = shell): scan monitor, detached view host,
  torn-off widget host. Same modality discipline as today's API-keys
  dialog (JAVAFX.md windows table).

## Components
1. **Shell** (new: `shell.fxml`, `ShellController`): sidebar buttons
   `flat` + `accent` when selected; status bar labels.
2. **Dashboard pane** (reworked `dashboard.fxml`): scan command card
   (target picker, profile, Scan/Cancel, progress + counters), stat
   cards (scans, alive hosts, open ports, KEV findings, VT-flagged IPs
   via existing DAOs), two charts (findings-by-severity,
   top-ports histogram) over loaded scan data.
3. **Migrated panes**: results, entrypoints, targets, export — same
   controllers minus Back buttons; KEV cell gets full-list tooltip;
   entry-points (later results) gets a master-detail strip showing
   the selected row's full findings.
4. **VT column**: already in EntryPoint/export (earlier cycle); shell
   keeps it. CORE.md view-table line still needs the approved
   "..., KEV, VT, Score" amendment (pending human yes).
5. **Secondary windows**: monitor (auto-open on scan start,
   auto-close on finish; one per running scan), detached Results /
   Entry Points for any scan, torn-off dashboard cards.

## Data flow
Unchanged pattern: workers/Task load (DAOs, scan events),
`Platform.runLater` renders. Charts rebuild on scan-selection change,
never per event. Detached windows run the same loads their
controllers already do; each window owns its workers and closes them
on close. No shared mutable UI state between windows.

## Error handling
- View load failure: status-bar message + stay on current view
  (never a blank content area).
- Secondary window with dead scan id: close with WARNING dialog.
- Lookup/dialog discipline follows the existing JAVAFX.md table.

## Layout rules (JAVAFX.md amendments, need human yes)
- No fixed scene sizes; shell 1280x800 start, 1100x700 minimum.
- `BorderPane`/`SplitPane`/`VBox` over fixed `AnchorPane`
  coordinates in migrated views; tables keep constrained resize.
- No `prefWidth/prefHeight` as layout crutches on containers.
- Window geometry persisted in prefs, restored on open.
- Styling stays AtlantaFX classes + `application.css`; no `setStyle`.

## Migration cycles (≤200 lines each, `mvn verify` + visual sign-off)
1. Dashboard from zero (command card, stat cards, charts; old nav buttons deleted).
2. Targets + Export as panes (sidebar persists).
3. Results + Entry Points as panes (+ KEV tooltip, detail strip).
4. Delete every legacy `showX` scene-switch and Back button (no eject paths remain).

## Testing
- `FxmlLoadTest` extended per new FXML; full `mvn verify` per cycle.
- Styling is human-verified (screenshot glance); CSS typos fail
  silently and are caught only by eyes.
