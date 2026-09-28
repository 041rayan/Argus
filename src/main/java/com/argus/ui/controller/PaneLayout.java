package com.argus.ui.controller;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.layout.Region;

/**
 * The constraints that make a pane responsive: one shared rule, applied the
 * same way in every pane, instead of a number per view.
 *
 * <p>An FXML {@code prefWidth="47%"} does not load, and a percent
 * {@code -fx-pref-width} on a child of an HBox is circular because the parent's
 * width comes from its children. A property bound in the controller is what
 * actually works, which is why these live here rather than in the sheet.
 */
final class PaneLayout {

    /** The style class the sheet gives a pane root, used to find it. */
    private static final String PANE = "ag-pane";

    private PaneLayout() {
    }

    /**
     * Binds an inspector to a share of its pane's width. The clamps keep it
     * readable at the minimum window size and stop it taking the table's space.
     *
     * @param inspector the inspector pane
     * @param share     fraction of the pane width, e.g. 0.30
     * @param min       smallest usable width
     * @param max       largest useful width
     */
    static void bindInspectorWidth(Region inspector, double share, double min, double max) {
        bindShare(inspector, paneOf(inspector).widthProperty(), share, min, max, true);
    }

    /**
     * Binds a block's height to a share of a source that already tracks the
     * window.
     *
     * <p>The caller supplies the source because it differs by pane: a table
     * pane follows its own width, but a pane inside a ScrollPane must follow the
     * viewport, because a scrolling pane's own height comes from its content and
     * binding to that would be circular.
     *
     * @param block  the block to size
     * @param source a height or width that tracks the window
     * @param share  fraction of the source
     * @param min    smallest usable size
     * @param max    largest useful size
     */
    static void bindHeight(Region block, ObservableValue<Number> source,
                           double share, double min, double max) {
        bindShare(block, source, share, min, max, false);
    }

    private static void bindShare(Region region, ObservableValue<Number> source,
                                  double share, double min, double max, boolean width) {
        var target = width ? region.prefWidthProperty() : region.prefHeightProperty();
        target.bind(Bindings.createDoubleBinding(
                () -> clamp(source.getValue().doubleValue() * share, min, max), source));
        if (width) {
            region.setMinWidth(min);
            region.setMaxWidth(max);
        } else {
            region.setMinHeight(min);
            region.setMaxHeight(max);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** The nearest ancestor carrying the pane style class. */
    private static Region paneOf(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n instanceof Region region && region.getStyleClass().contains(PANE)) {
                return region;
            }
        }
        throw new IllegalStateException(
                "no " + PANE + " ancestor for: " + node.getClass().getSimpleName());
    }
}
