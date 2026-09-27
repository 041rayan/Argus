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
 * runtime, so this proves every class the views use is actually defined.
 * The reverse direction (no dead rule) is asserted by
 * {@code noLegacyStyleClassesRemain}'s sibling in the final cycle, once
 * every FXML has been migrated.
 */
class CssContractTest {

    /** The ten FXMLs the app ships. Listed, not scanned: a jar hides a directory. */
    private static final List<String> FXML = List.of(
            "mark", "login", "lock", "shell", "dashboard",
            "targets", "export", "results", "entrypoints", "apikeys");

    // a class ends alphanumeric; "ag-status-" in a startsWith prefix is not one
    private static final Pattern AG =
            Pattern.compile("\\b(ag-[a-z0-9]+(?:-[a-z0-9]+)*)(?![a-z0-9-])");

    /**
     * A class counts as used when it can reach a node: a
     * {@code styleClass="..."} attribute in FXML, or an {@code ag-} literal
     * in a ui controller (handed to {@code add()}, or returned into it by a
     * switch). The one thing that is NOT a use is a cleanup list: a class
     * that only ever appears inside {@code removeAll(...)} has no call site,
     * and counting it would let a dead rule certify itself as live. The
     * production code therefore clears status colours with
     * {@code removeIf(c -> c.startsWith("ag-status-"))} and keeps no literal
     * list. A class that is both added and removed is reported dead, which
     * is a false alarm on purpose: it sends the author back to the source.
     */
    private static final Pattern USED_IN_FXML =
            Pattern.compile("styleClass=\"([^\"]*)\"");
    private static final Pattern USED_IN_JAVA =
            Pattern.compile("getStyleClass\\(\\)\\.add\\(\"([^\"]*)\"\\)");
    private static final Pattern CLEANUP_IN_JAVA =
            Pattern.compile("(?:removeAll|remove)\\s*\\(([^)]*)\\)");

    @Test
    void everyUsedClassIsDefined() throws IOException {
        Set<String> defined = cssClasses();
        Set<String> used = usedClasses();

        Set<String> missing = new LinkedHashSet<>(used);
        missing.removeAll(defined);

        assertEquals(Set.of(), missing, "ag-* class used but not in application.css");
    }

    @Test
    void noInlineStylesSurvive() throws IOException {
        for (String name : FXML) {
            String fxml = resource("/com/argus/ui/view/" + name + ".fxml");
            assertTrue(!fxml.contains("style=\""),
                    name + ".fxml uses an inline style=; the token sheet owns styling (JAVAFX.md)");
        }
        Path ui = Path.of("src/main/java/com/argus/ui");
        try (var walk = Files.walk(ui)) {
            var offenders = walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return Files.readString(p).contains(".setStyle(");
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .map(Path::toString)
                    .toList();
            assertEquals(List.of(), offenders, "setStyle in a controller; use a style class");
        }
    }

    @Test
    void theSheetIsNotEmpty() throws IOException {
        assertTrue(cssClasses().size() >= 34,
                "the token sheet lost classes, expected at least 34, got " + cssClasses());
    }

    /**
     * Every .ag-* class the views use must be defined, and nothing the
     * sheet defines may be dead. Both halves are safe now that every FXML
     * is migrated.
     */
    @Test
    void everyDefinedClassIsUsed() throws IOException {
        Set<String> used = usedClasses();
        Set<String> dead = new LinkedHashSet<>(cssClasses());
        dead.removeAll(used);

        assertEquals(Set.of(), dead, "ag-* class in application.css but never used");
    }

    /** After the overhaul no view may use a pre-overhaul class name. */
    @Test
    void noLegacyStyleClassesRemain() throws IOException {
        Set<String> legacy = Set.of("striped", "title-label", "status-label",
                "text-muted", "title-2", "flat", "accent", "danger");
        Set<String> found = new LinkedHashSet<>();
        for (String name : FXML) {
            found.addAll(matches(resource("/com/argus/ui/view/" + name + ".fxml")));
        }
        found.remove("root-pane");

        Set<String> offenders = new LinkedHashSet<>(found);
        offenders.retainAll(legacy);

        assertEquals(Set.of(), offenders,
                "pre-overhaul class names still in an FXML: " + offenders);
    }

    /** Every .ag-* selector in the sheet. */
    private static Set<String> cssClasses() throws IOException {
        URL css = MainApp.class.getResource("/com/argus/ui/view/application.css");
        assertTrue(css != null, "application.css is missing from the resources");
        return matches(new String(css.openStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    /**
     * Every ag-* class that can reach a node, minus the ones a controller
     * only ever removes.
     */
    private static Set<String> usedClasses() throws IOException {
        Set<String> used = new LinkedHashSet<>();
        for (String name : FXML) {
            collect(resource("/com/argus/ui/view/" + name + ".fxml"), USED_IN_FXML, used);
        }
        Path ui = Path.of("src/main/java/com/argus/ui");
        assertTrue(Files.isDirectory(ui), "ui sources not found at " + ui.toAbsolutePath());
        Set<String> addedLiterally = new LinkedHashSet<>();
        Set<String> cleanup = new LinkedHashSet<>();
        try (var walk = Files.walk(ui)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            String source = Files.readString(p);
                            collect(source, USED_IN_JAVA, addedLiterally);
                            // a name returned into add(), e.g. a severity switch
                            used.addAll(agTokens(source));
                            collect(source, CLEANUP_IN_JAVA, cleanup);
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    });
        }
        used.addAll(addedLiterally);
        cleanup.removeAll(addedLiterally); // a class added somewhere is not dead
        used.removeAll(cleanup);
        return used;
    }

    private static void collect(String text, Pattern pattern, Set<String> into) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            for (String token : matcher.group(1).split("[\\s\"]+")) {
                if (token.startsWith("ag-") && !token.endsWith("-")) {
                    into.add(token);
                }
            }
        }
    }

    private static Set<String> agTokens(String text) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = AG.matcher(text);
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
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
