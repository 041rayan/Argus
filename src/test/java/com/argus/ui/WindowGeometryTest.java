package com.argus.ui;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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

    /**
     * The point of the constants is that geometry lives in one place. A new
     * literal scene size anywhere in MainApp would defeat that silently.
     * Line based, because a constructor call nests parentheses.
     */
    @Test
    void noSceneIsBuiltWithALiteralSize() throws Exception {
        var source = java.nio.file.Files.readAllLines(java.nio.file.Path.of(
                "src/main/java/com/argus/ui/MainApp.java"));

        int mainScenes = 0;
        for (String line : source) {
            if (!line.contains("new Scene(")) {
                continue;
            }
            if (line.contains("WINDOW_WIDTH")) {
                mainScenes++;
                assertTrue(line.contains("WINDOW_HEIGHT"),
                        "a main window pairs WINDOW_WIDTH with a literal height: " + line.strip());
            } else {
                // the one exception: the API keys dialog sizes to its content
                assertTrue(line.contains("new Scene(root)"),
                        "a scene is built with a literal size: " + line.strip());
            }
        }

        assertEquals(3, mainScenes, "login, shell and lock are the only main windows left");
        assertTrue(source.stream().noneMatch(l -> l.contains("setMinWidth(0)")),
                "the old clearStageMin escape hatch is gone; do not bring it back");
    }

    private static int intConstant(String name) throws Exception {
        Field field = MainApp.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(null);
    }
}
