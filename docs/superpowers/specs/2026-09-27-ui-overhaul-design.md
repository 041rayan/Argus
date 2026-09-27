# UI Overhaul — Spec (2026-09-27)

## Intent

Replace the current fixed-coordinate JavaFX views with one coherent
application: every main window opens at 1280x720, a single token sheet
drives every colour, size and radius, and actions sit in the same place
in every view. Nothing that works today stops working. No new features.

This spec supersedes `2026-09-27-ui-shell-design.md`, which set
1280x800 and 1100x700. Its unbuilt parts (cycles C and D) are
absorbed here as cycles 5 and 6.

## Non-goals

- No DB, pipeline, crypto or threading changes.
- No new dependency. The mark is drawn from JavaFX shapes, not an icon font.
- No new features. No Scan cancel button, no per host entry point filter,
  no dark/light switch, no window geometry persistence.
- One core method is added (see Code impact). Nothing else in `core`.

## Decisions

| Question | Decision |
|---|---|
| Window size | 1280x720 landscape, not the 720x1280 first requested (portrait breaks the 6 and 7 column tables) |
| Dialogs | Main stage only is fixed geometry. The API Keys window sizes to its content |
| Scope | Token sheet plus every FXML rebuilt. Absorbs the old cycles C and D |
| Auth screen | Sign in and create account stay on one 1280x720 screen, toggled by a text link |
| Auth layout | Split hero: brand panel left at 47%, form card right |
| Main layout | 220px sidebar shell, 28px status bar, 1280x720, minimum 1100x640, resizable |
| Data views | Detail inspector on the right of Results, Entry points and Targets |
| Destructive buttons | Solid red `#b62324`, never adjacent to the primary action |
| Icons | None. The eye mark is five shapes in `mark.fxml`: a stroked `Ellipse`, two stroked iris `Circle`s, two filled pupils |
| Theme | Keep AtlantaFX Primer Dark, restate the palette as tokens |

Rejected: CSS only (the fixed anchor positions in `results.fxml` collide
and tables have no room for an inspector); FXML plus controller rewrite
(regresses tested scan and thread logic for no visual gain); a light
theme or an OS following switch (no demand, doubles the token sheet).

## Window geometry

Every main window (sign in and create account, lock, shell) opens at
1280x720, is resizable, and has a minimum of 1100x640. `MainApp.applyGeometry(Scene)` is the single place that sets
it, so no view can drift and `clearStageMin()` goes away. The API Keys
window is the one exception: about 480x400, owner set, window modal
(JAVAFX.md windows table).

1280x720 leaves the shell 1060x692 of content. At the 1100x640 minimum
the content is 880x612, which is why every scrolling view keeps a
`ScrollPane` with `fitToWidth`.

## Design system

`application.css` becomes the token sheet. No `setStyle` in controllers
and no inline `style=` in FXML. Controllers add style classes only.

### Palette

| Role | Value |
|---|---|
| Background | `#0d1117` |
| Sidebar, status bar | `#10151c` |
| Panel, card | `#11161d` |
| Selected row, nav item | `#1b2430` |
| Border quiet / strong | `#21262d` / `#30363d` |
| Text / muted / faint | `#e6edf3` / `#8b949e` / `#6e7681` |
| Accent | `#58a6ff` |
| Primary | `#238636`, border `#2ea043` |
| Destructive | `#b62324`, border `#d04545` |
| Ok / warn / danger text | `#3fb950` (unused) / `#d29922` / `#ff7b72` |

### Buttons

| Variant | Style | Use |
|---|---|---|
| Primary | Filled `#238636`, white, semibold | The one committing action in a view |
| Secondary | `#161b22` on `#30363d` | Filters, toggles, neutral actions |
| Destructive | Filled `#b62324`, white | Delete, remove. Solid, as approved |
| Text link | Transparent, `#58a6ff` | Tertiary: create account, back to sign in, close |

Heights: 44 in the auth card, 34 in the shell, 28 in the status bar.
Radius 6 on controls, 8 on cards. Padding is 15 horizontal, or 17 for a
primary.

### Placement rules

1. View title and scan metadata on the left of the view header.
   Filters and actions on the right, primary last.
2. One primary per view. It is the only filled green button on screen.
3. Destructive actions never sit adjacent to the primary. 16px minimum
   gap, or a different group entirely.
4. Row level destructive actions live in the inspector footer. Scan
   level ones live in the view header.
5. Search is always its own full width row under the header, never
   inside the toolbar.
6. Dialog footers: destructive on the left group, close plus primary on
   the right group, 24px apart.
7. A button is never wider than its label plus 30px. No two buttons of
   the same variant side by side.

### Type, forms, tables

Type scale: 20 view title, 13.5 body, 12.5 muted, 11 uppercase field
labels and table headers with 1.2 letter spacing. Status bar is 11.5
monospace.

Form rows: uppercase 11 label above a 38 tall input, 6 radius, 12 gap
between rows. Errors replace the hint line under the field, never a
dialog. Auth inputs are 44 tall.

Tables: 10.5 uppercase muted headers, 12.5 body, hairline rows
`#161b22`, selected row `#1b2430`, constrained resize policy, striped
class dropped in favour of the selected row only.

## The mark

`mark.fxml` is a 46x29 `StackPane` holding five shapes in accent colour:
a wide stroked `Ellipse` for the outline, two stroked `Circle`s for the
top and bottom of the iris, and two filled `Circle`s for the pupils.
`login.fxml`, `lock.fxml` and `shell.fxml` include it. No controller, no
dependency.

## Views

### Sign in and create account (one screen, `login.fxml`)

Brand panel left at 47%: `#10151c`, hairline right border, mark and
`ARGUS` at 30 with 9 letter spacing, one line of copy
("Reconnaissance console. Targets you are authorized to assess."),
version and vault line in monospace at the bottom. No gradient, no
background pattern, no feature bullet list.

Form card right, 430 wide, 34 padding: heading, one line of sub copy,
username and password rows, full width 44 primary, status line, text
link that toggles to create account. `LoginController.setCreateMode`
keeps working and only rewrites the heading, sub copy, primary label,
link label and status text. First run still opens in create mode.

### Lock (`lock.fxml`)

Same split frame, same mark, password field and a 44 Unlock primary.
Copy: "Session idle. Re-enter your password to unlock the vault."

### Shell (`shell.fxml`)

220px sidebar: mark plus wordmark, six nav items (Dashboard, Results,
Entry points, Targets, Export, API keys), a hairline, the provider
status block in 11.5 monospace, then Lock and Log out pinned to the
bottom. Selected nav item gets `#1b2430` plus a 2px accent inset bar;
no fill change on hover. Content area unchanged (one pane at a time in
the `StackPane`). Status bar 28 tall: scan state on the left, operator
and vault state on the right.

### Dashboard

Header, then a scan command card (target combo growing, Scan primary on
the right, progress bar and status line under), five stat tiles in one
row (Scans, Alive hosts, Open ports, KEV findings, VT flagged), then
the two charts side by side. Whole view inside a `ScrollPane` with
`fitToWidth`.

### Targets

Add form as a labelled card (label, domain, scope, profile, Add).
Full width search row. Table: Label, Domain, Profile, Scope.
Inspector: label, domain, profile, created date, the full scope CIDR
list on separate lines, and Delete target as the solid red footer
action. The floating Delete button is gone. Adding and deleting keep
their existing validation and dialogs.

### Results

Table: Host, IP, Country, Status. Inspector: IP, ASN, Org, open ports
with service and version, findings with type and severity.

ASN and Org move out of the table into the inspector. They stay in the
search filter, so nothing becomes unsearchable.

Scan level actions stay in the header: scan combo, date filter, and
Delete scan as the solid red button. The Entry points button is removed
because Entry points is sidebar navigation now. The inspector is read
only here.

### Entry points

Table keeps all seven columns (Rank, Host:Port, Service, Severity, KEV,
VT, Score) at reduced widths, KEV truncating with a tooltip carrying the
full list. Inspector: every finding on the selected host and port, with
CVE, confidence, ransomware flag and VT engine counts.

### Export

One card: target combo, format combo, Export as a full width 44
primary, status line, and a static list of what the package contains
(subdomains, hosts, open ports, findings, entry points; markdown or
JSON). No new queries.

### API Keys window

Content sized, about 480x400, owner set, window modal. Form rows for
provider and key. Footer: Remove (destructive, left), Close (text link)
and Save (primary, right), 24px apart. Key verification messaging is
unchanged.

## Code impact

| File | Change |
|---|---|
| `application.css` | Rebuilt as the token sheet |
| `mark.fxml` (new) | The eye mark |
| all nine FXMLs | Rebuilt on the token classes and the layout grid |
| `MainApp` | `applyGeometry(Scene)`, all scenes 1280x720, `clearStageMin` deleted |
| `LoginController` | `modeLabel` becomes two labels, heading and sub line. Only copy strings change, plus the `setCreateMode` text swaps. No logic change |
| `ShellController` | Selected nav state toggles exactly one `nav-selected` class, no other class juggling. All six panes load through `setPane` |
| `ResultsController` | Loads ports and findings for the selected scan, groups them per host, feeds the inspector |
| `EntryPointsController` | Keeps the raw lists it already loads, feeds the inspector, Back button removed |
| `TargetsController` | Feeds the inspector, Delete moved to it |
| `ExportController`, `DashboardController`, `AddApiKeyController` | Wiring and copy only |
| `ui/HostDetail` (new) | Pure grouping of ports and findings per host subdomain. Unit tested, no toolkit |
| `ExportService` | New `hostPortOf(finding)`, `findingOut(finding)` and `findingOutsFor(findings, hostPort)`, all pure, reuses `detailText` |

`HostDetail` and the `ExportService` helpers exist so no controller
parses `detail_json` and all of them can be unit tested without a
display. No new SQL: the inspectors read `PortDAO.listByScan` and
`FindingDAO.listByScan`, which the controllers already call, and group
in memory. Both inspectors group in the same off-FX hop that fills the
table, so a row click is a map lookup, never a re-parse on the FX
thread.

## Data flow and threading

Unchanged. Workers and tasks load, `Platform.runLater` renders in
batches about every 100ms, never per port. Inspector population is
part of the same off FX load as the table, so selecting a row is a pure
FX operation over already loaded lists. A scan keeps running while you
navigate; the mid scan leave guard stays.

## Error handling

Unchanged dialogs per the JAVAFX.md table. One addition: an inspector
with no selection shows muted placeholder text, never blank. A view load
failure still writes to the status bar and stays on the current pane.

## Cycles

Each cycle ends `mvn verify` green with a manual click path.

1. **Foundation.** Full token sheet, `mark.fxml`, `MainApp.applyGeometry`
   at 1280x720, sidebar and status bar restyle, `clearStageMin` gone.
2. **Auth.** `login.fxml` and `lock.fxml` to the split layout.
3. **Dashboard.** Grid, stat tiles, command card, charts.
4. **Targets and Export.** Card layout, inspector, `HostDetail` with
   its test.
5. **Results and Entry points.** Inspectors, column moves,
   `findingOutsFor` with its test. Old cycle C.
6. **API Keys and cleanup.** Dialog restyle, then delete the legacy
   scene switches: `MainApp.showTargets`, `showResults`, `showEntryPoints`
   and `showExport`, plus `ResultsController.onEntryPoints` and `onBack`,
   and `EntryPointsController.onBack`. `showLogin`, `showShell`,
   `showLock` and `showAddApiKey` stay, they are not panes. Old cycle D.

## Testing

- `FxmlLoadTest` loads all ten FXMLs including `mark.fxml`.
- `HostDetail` grouping: ports and findings land on the right host,
  dead hosts get an empty list, null safe.
- `findingOutsFor`: filters to one host and port, empty input gives an
  empty list.
- `mvn verify` per cycle. Visual polish is human verified, because a
  CSS class typo fails silently.

## Spec amendments needing approval

- `JAVAFX.md`: window geometry (1280x720, minimum 1100x640, dialogs size
  to content), the design system and placement rules, the inspector
  pattern, and the ban on inline `style=` in FXML.
- `2026-09-27-ui-shell-design.md` is superseded, not edited.
- `DECISIONS.md` needs no entry: no dependency, and window geometry
  belongs in `JAVAFX.md`.
