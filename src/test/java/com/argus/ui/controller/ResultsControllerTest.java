package com.argus.ui.controller;

import com.argus.core.model.Finding;
import com.argus.core.model.PortResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Results inspector's two line formats, pinned (headless: no toolkit). */
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
