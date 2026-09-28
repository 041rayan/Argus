package com.argus.ui.controller;

import com.argus.core.export.ExportService;
import com.argus.ui.row.EntryPointsRow;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A cell factory only runs once a table has a skin and rows, which needs a
 * real layout pass, so a plain FXML load test cannot reach it. These tests
 * build a real table and lay it out, which is how the KEV cell was caught
 * casting a String to a row.
 *
 * <p>Headless, but it needs the toolkit: the JavaFX CSS and skin pass
 * creates the cells (ADR-009, local gate only).
 */
class EntryPointsCellTest {

    private static final EntryPointsRow KEV_ROW = new EntryPointsRow(
            new ExportService.EntryPoint(1, "api.example.com:80", "http", "HIGH",
                    "CVE-2021-1 CONFIRMED", "-", 75));
    private static final EntryPointsRow BARE_ROW = new EntryPointsRow(
            new ExportService.EntryPoint(2, "dev.example.com:22", "ssh", "-", "-", "-", 10));

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // toolkit already running in this JVM
        }
    }

    @Test
    void theKevCellShowsTheIdentifierAndKeepsTheFullTextAsATooltip() throws Exception {
        TableCell<?, ?> cell = renderCell("kev", EntryPointsController.kevCells(), KEV_ROW);

        assertEquals("CVE-2021-1", cell.getText(),
                "the column is 160px, so the cell carries the identifier only");
        assertNotNull(cell.getTooltip(), "a real KEV match needs its full list on hover");
        assertEquals("CVE-2021-1 CONFIRMED", cell.getTooltip().getText(),
                "confidence and the rest of the list stay one hover away");
    }

    @Test
    void aKevCellCountsTheIdentifiersItHides() throws Exception {
        var row = new EntryPointsRow(new ExportService.EntryPoint(1, "a.example.com:80", "http",
                "HIGH", "CVE-2021-41773 CONFIRMED; CVE-2021-45046 CANDIDATE", "-", 75));

        TableCell<?, ?> cell = renderCell("kev", EntryPointsController.kevCells(), row);

        assertEquals("CVE-2021-41773 +1", cell.getText(),
                "two identifiers do not fit the column, so the count is shown");
        assertEquals("CVE-2021-41773 CONFIRMED; CVE-2021-45046 CANDIDATE",
                cell.getTooltip().getText());
    }

    @Test
    void theCellTextCollapsesTheConfidenceWords() {
        assertEquals("CVE-2021-41773",
                EntryPointsController.kevCellText("CVE-2021-41773 CONFIRMED"));
        assertEquals("CVE-2021-41773",
                EntryPointsController.kevCellText("CVE-2021-41773 CANDIDATE"));
        assertEquals("CVE-2021-41773 +1",
                EntryPointsController.kevCellText(
                        "CVE-2021-41773 CONFIRMED; CVE-2021-45046 CANDIDATE"));
        assertEquals("CVE-2021-41773", EntryPointsController.kevCellText("CVE-2021-41773"),
                "a bare identifier is already the display form");
    }

    @Test
    void aRowWithNoMatchIsUnchanged() {
        assertEquals("-", EntryPointsController.kevCellText("-"));
        assertEquals("", EntryPointsController.kevCellText(""));
        assertEquals("", EntryPointsController.kevCellText(null));
    }

    @Test
    void aRowWithNoKevMatchHasNoTooltip() throws Exception {
        TableCell<?, ?> cell = renderCell("kev", EntryPointsController.kevCells(), BARE_ROW);

        assertEquals("-", cell.getText());
        assertNull(cell.getTooltip(), "a dash is not worth a tooltip");
    }

    @Test
    void theSeverityCellColoursTheWorstFirst() throws Exception {
        TableCell<?, ?> high = renderCell("severity", EntryPointsController.severityCells(), KEV_ROW);

        assertEquals("HIGH", high.getText());
        assertTrue(high.getStyleClass().contains("ag-status-danger"),
                "HIGH is a danger colour, got " + high.getStyleClass());
    }

    @Test
    void aRowWithNoFindingsCarriesNoSeverityColour() throws Exception {
        TableCell<?, ?> bare = renderCell("severity", EntryPointsController.severityCells(), BARE_ROW);

        assertEquals("-", bare.getText());
        assertTrue(bare.getStyleClass().stream().noneMatch(c -> c.startsWith("ag-status-")),
                "a placeholder must not be coloured, got " + bare.getStyleClass());
    }

    @Test
    void severityMapsWorstFirst() {
        assertEquals("ag-status-danger", EntryPointsController.severityClass("CRITICAL"));
        assertEquals("ag-status-danger", EntryPointsController.severityClass("HIGH"));
        assertEquals("ag-status-warn", EntryPointsController.severityClass("MEDIUM"));
        assertEquals("ag-status-info", EntryPointsController.severityClass("LOW"));
    }

    /** Builds a one column table, lays it out, and returns the rendered cell. */
    @SuppressWarnings("unchecked")
    private static TableCell<?, ?> renderCell(String property,
                                               javafx.util.Callback<TableColumn<EntryPointsRow, String>,
                                                       TableCell<EntryPointsRow, String>> factory,
                                               EntryPointsRow row) throws Exception {
        AtomicReference<TableCell<?, ?>> rendered = new AtomicReference<>();
        onFx(() -> {
            TableColumn<EntryPointsRow, String> column = new TableColumn<>(property);
            column.setCellValueFactory(new PropertyValueFactory<>(property));
            column.setCellFactory(factory);
            TableView<EntryPointsRow> table = new TableView<>(
                    FXCollections.observableArrayList(List.of(row)));
            table.getColumns().add(column);
            table.setPrefSize(400, 200);
            new Scene(table, 400, 200);
            table.applyCss();
            table.layout();
            table.lookupAll(".table-cell").stream()
                    .filter(TableCell.class::isInstance)
                    .map(TableCell.class::cast)
                    .findFirst()
                    .ifPresent(c -> rendered.set((TableCell<?, ?>) c));
            return null;
        });
        TableCell<?, ?> cell = rendered.get();
        assertNotNull(cell, "no cell rendered for property " + property);
        return cell;
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
            throw new IllegalStateException("the FX thread did not answer within 20s");
        }
        if (error.get() != null) {
            throw new IllegalStateException(error.get());
        }
        return result.get();
    }
}
