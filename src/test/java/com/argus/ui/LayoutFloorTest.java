package com.argus.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * At the 1100x640 minimum the shell content area is 880x612. Every pane
 * must fill that box without overflowing it, and the parts an operator
 * reads (a table, the inspector) must stay usable there
 * (Review Focus 4 of the UI overhaul plan).
 *
 * <p>Measured at 880x612: the pane roots ask for 624, 619, 344, 667 and
 * 527. Only the dashboard may exceed the box, because it is the one pane
 * inside a ScrollPane. The other four are squeezed into it, which is
 * legal only while their minimum height still fits.
 */
class LayoutFloorTest {

    /** The four panes that must fit the box. */
    private static final List<String> FITTING = List.of("targets", "export", "results", "entrypoints");

    /** The panes whose table has to stay usable. */
    private static final List<String> TABLES = List.of("targets", "results", "entrypoints");

    /** The shell's own furniture, mirrored from shell.fxml. */
    private static final double SIDEBAR = 220;
    private static final double STATUS_BAR = 28;
    private static final double CONTENT_WIDTH = MainApp.MIN_WIDTH - SIDEBAR;
    private static final double BOX = MainApp.MIN_HEIGHT - STATUS_BAR;

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // toolkit already running in this JVM
        }
    }

    @Test
    void nonScrollingPanesCanBeSqueezedIntoTheBox() throws Exception {
        for (String name : FITTING) {
            Parent root = layoutAtTheFloor(name);

            // A root is always resized to its scene, so its own height proves
            // nothing. What matters is whether the content CAN compress into
            // the box: a pane whose min height exceeds the floor has no room
            // to give, so its bottom is cut off.
            double minHeight = onFx(() -> root.minHeight(-1));
            assertTrue(minHeight <= BOX, name + " needs " + (long) minHeight
                    + "px minimum in a " + (long) BOX + "px box, it cannot be squeezed "
                    + "and its bottom will be clipped");
            double height = onFx(root::getBoundsInLocal).getHeight();
            assertTrue(Math.abs(height - BOX) < 2, name + " laid out " + height
                    + "px tall at the floor, it must fill " + (long) BOX + "px");
        }
    }

    @Test
    void aTableStaysUsableAtTheFloor() throws Exception {
        for (String name : TABLES) {
            Parent root = layoutAtTheFloor(name);

            TableView<?> table = (TableView<?>) onFx(() -> root.lookup(".ag-table"));
            assertTrue(table != null, name + " has no table");
            Bounds bounds = onFx(table::getBoundsInLocal);
            assertTrue(bounds.getHeight() > 100, name + " table is only "
                    + (long) bounds.getHeight() + "px tall at the floor, a table needs rows");
            assertTrue(bounds.getHeight() <= BOX, name + " table is "
                    + (long) bounds.getHeight() + "px, it overflows the " + (long) BOX + "px box");
        }
    }

    @Test
    void theDashboardScrollsRatherThanClips() throws Exception {
        URL fxml = resource("dashboard.fxml");
        ScrollPane root = (ScrollPane) onFx(() -> {
            try {
                return (ScrollPane) new FXMLLoader(fxml).load();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        onFx(() -> {
            new Scene(root, 880, BOX);
            root.applyCss();
            root.layout();
            return null;
        });

        Node content = onFx(root::getContent);
        double viewport = onFx(root.getViewportBounds()::getHeight);
        double wanted = onFx(() -> content.prefHeight(-1));
        assertTrue(viewport > 0, "the dashboard viewport collapsed at the floor");
        assertTrue(wanted > viewport,
                "the dashboard content (" + (long) wanted + "px) fits the viewport ("
                        + (long) viewport + "px); the ScrollPane would then be dead weight");
    }

    @Test
    void theInspectorTracksTheWindowWidth() throws Exception {
        // 30% of the pane, at two widths where the clamps are not active
        assertEquals(Math.round(1008 * 0.30), inspectorWidthAt("results", 1008), 2,
                "at 1008 wide the inspector must be 30% of it");
        assertEquals(Math.round(1100 * 0.30), inspectorWidthAt("results", 1100), 2,
                "at 1100 wide the inspector must be 30% of it, not a fixed 300");
    }

    @Test
    void theInspectorKeepsItsClampsAtBothEnds() throws Exception {
        int wide = inspectorWidthAt("results", 1600);
        int narrow = inspectorWidthAt("results", 500);

        assertTrue(wide <= 340, "the inspector must not grow past 340px, got " + wide);
        assertTrue(narrow >= 260, "the inspector must not shrink below 260px, got " + narrow);
    }

    /** The inspector's laid-out width with the pane content given this width. */
    private static int inspectorWidthAt(String name, double contentWidth) throws Exception {
        URL fxml = resource(name + ".fxml");
        return onFx(() -> {
            Parent root;
            try {
                root = new FXMLLoader(fxml).load();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            new Scene(root, contentWidth, BOX);
            root.applyCss();
            root.layout();
            Node inspector = root.lookup(".ag-inspector");
            if (inspector == null) {
                fail(name + " has no inspector");
            }
            return (int) inspector.getBoundsInLocal().getWidth();
        });
    }

    /** Loads a pane, unwraps a ScrollPane, and runs one layout pass at 880x612. */
    private static Parent layoutAtTheFloor(String name) throws Exception {
        URL fxml = resource(name + ".fxml");
        return onFx(() -> {
            Parent root;
            try {
                root = new FXMLLoader(fxml).load();
            } catch (IOException e) {
                throw new IllegalStateException("cannot load " + name + ".fxml", e);
            }
            new Scene(root, CONTENT_WIDTH, BOX);
            root.applyCss();
            root.layout();
            // A ScrollPane keeps its content out of getChildren(), so lookup has
            // to start at the content node rather than the root.
            return root instanceof ScrollPane scroll ? (Parent) scroll.getContent() : root;
        });
    }

    private static URL resource(String name) {
        URL fxml = LayoutFloorTest.class.getResource("/com/argus/ui/view/" + name);
        if (fxml == null) {
            fail("missing resource: " + name);
        }
        return fxml;
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
