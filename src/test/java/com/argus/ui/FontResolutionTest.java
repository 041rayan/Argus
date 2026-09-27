package com.argus.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A font family that does not resolve is a silent failure: the control
 * renders in the default face and nothing complains. Two lessons from
 * measuring it, both pinned here.
 *
 * <p>Headless, but it needs the toolkit: the CSS pass resolves the family.
 */
class FontResolutionTest {

    /** The default face JavaFX falls back to when a name does not resolve. */
    private static final String DEFAULT_FACE = "System";

    private static final Pattern FAMILY = Pattern.compile("-fx-font-family:\\s*([^;]+);");

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // toolkit already running in this JVM
        }
    }

    @Test
    void theMonospaceRoleActuallyResolves() throws Exception {
        String family = resolvedFamilyOf("#statusBarLabel");

        assertEquals("Monospaced", family,
                "the status bar is a monospace role; an unresolved name would "
                        + "silently render it in the default proportional face");
    }

    @Test
    void theSheetDeclaresOneFamilyPerRule() throws Exception {
        Matcher matcher = FAMILY.matcher(sheet());
        int rules = 0;
        while (matcher.find()) {
            String value = matcher.group(1);
            rules++;
            assertTrue(!value.contains(","),
                    "JavaFX takes the first -fx-font-family and never falls "
                            + "through, so a comma list is a no-op: " + value.strip());
        }
        assertTrue(rules >= 2, "expected a sans and a monospace rule, found " + rules);
    }

    @Test
    void theSansRoleResolvesOrDegradesToTheDefault() throws Exception {
        String named = declaredFamilyFor("root-pane");
        String resolved = resolvedFamilyOf("#navResults");

        assertTrue(resolved.equals(unquote(named)) || resolved.equals(DEFAULT_FACE),
                "the nav item must render in " + unquote(named) + " or fall back to the "
                        + "default face, but it resolved to " + resolved);
    }

    private static String resolvedFamilyOf(String fxId) throws Exception {
        URL fxml = FontResolutionTest.class.getResource("/com/argus/ui/view/shell.fxml");
        return onFx(() -> {
            Parent root;
            try {
                root = new FXMLLoader(fxml).load();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            Scene scene = new Scene(root, 1280, 720);
            scene.getStylesheets().add(FontResolutionTest.class.getResource(
                    "/com/argus/ui/view/application.css").toExternalForm());
            root.applyCss();
            root.layout();
            javafx.scene.Node node = root.lookup(fxId);
            if (node == null) {
                fail("no node for " + fxId);
            }
            return toFont(node).getFamily();
        });
    }

    /** The font a styled node actually resolved to (Label and Button are Labeled). */
    private static javafx.scene.text.Font toFont(javafx.scene.Node node) {
        if (node instanceof javafx.scene.control.Labeled labeled) {
            return labeled.getFont();
        }
        throw new IllegalStateException("not a styled node: " + node.getClass());
    }

    private static String unquote(String family) {
        return family.replace("\"", "").trim();
    }

    private static String sheet() throws IOException {
        URL css = FontResolutionTest.class.getResource("/com/argus/ui/view/application.css");
        assertTrue(css != null, "application.css is missing");
        return new String(css.openStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    /** The family the sheet declares for a rule, e.g. Lato for .root-pane. */
    private static String declaredFamilyFor(String selector) throws IOException {
        var matcher = Pattern.compile(
                "\\." + selector + "\\s*\\{[^}]*?-fx-font-family:\\s*([^;]+);").matcher(sheet());
        assertTrue(matcher.find(), selector + " declares no font family");
        return matcher.group(1);
    }

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
