package com.argus.ui.controller;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The add/update form is the only place a target is created or changed, so
 * its parsing and validation are pinned here rather than left to a click
 * path (headless: no FX toolkit touched).
 */
class TargetsFormTest {

    @Test
    void aCompleteFormIsAccepted() {
        var input = TargetsController.readForm("  Acme  ", " example.com ",
                "203.0.113.0/24, 198.51.100.0/24", "full");

        assertTrue(input.isPresent());
        assertEquals("Acme", input.get().label());
        assertEquals("example.com", input.get().domain());
        assertEquals("full", input.get().profile());
        assertEquals(List.of("203.0.113.0/24", "198.51.100.0/24"), input.get().scope());
    }

    @Test
    void aBlankLabelOrDomainIsRejected() {
        assertTrue(TargetsController.readForm("", "example.com", "", "quick").isEmpty());
        assertTrue(TargetsController.readForm("   ", "example.com", "", "quick").isEmpty(),
                "whitespace is not a label");
        assertTrue(TargetsController.readForm("Acme", "", "", "quick").isEmpty());
        assertTrue(TargetsController.readForm("Acme", "  ", "", "quick").isEmpty());
    }

    @Test
    void scopeSplitsOnCommasAndDropsTheBlanks() {
        assertEquals(List.of(), TargetsController.readForm("Acme", "example.com", "", "quick")
                .orElseThrow().scope(), "no scope is a valid target, an empty list");
        assertEquals(List.of("10.0.0.0/8", "10.0.0.0/8"),
                TargetsController.readForm("Acme", "example.com", " 10.0.0.0/8 ,, 10.0.0.0/8 ",
                        "quick").orElseThrow().scope(),
                "empty entries are dropped, surrounding space is trimmed");
    }

    @Test
    void anUnsetProfileFallsBackToQuick() {
        assertEquals("quick", TargetsController.readForm("Acme", "example.com", "", null)
                .orElseThrow().profile());
    }

    @Test
    void aTargetIsBuiltFromTheFormForBothInsertAndUpdate() {
        var input = TargetsController.readForm("Acme", "example.com", "10.0.0.0/8", "full")
                .orElseThrow();

        Optional<com.argus.core.model.Target> created =
                TargetsController.toTarget(input, null, java.time.Instant.parse("2026-09-28T00:00:00Z"));
        assertTrue(created.isPresent());
        assertEquals(null, created.get().id(), "a new target has no id yet");
        assertEquals("2026-09-28T00:00:00Z", created.get().createdAt().toString());

        var updated = TargetsController.toTarget(input, 42L, java.time.Instant.parse("2026-09-28T00:00:00Z"))
                .orElseThrow();
        assertEquals(42L, updated.id(), "an update keeps the row's id and its created_at");
    }
}
