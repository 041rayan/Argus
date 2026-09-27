package com.argus.ui.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The auth screen is one 1280x720 frame in two modes. The copy is pure
 * so it can be pinned without a toolkit (headless gate, ADR-009).
 */
class LoginControllerTest {

    @Test
    void signInModeCopy() {
        var copy = LoginController.copyFor(false, false);

        assertEquals("Sign in", copy.heading());
        assertEquals("Unlock your vault key.", copy.sub());
        assertEquals("Sign in", copy.primary());
        assertEquals("Create account", copy.link());
    }

    @Test
    void firstRunOpensOnAccountCreation() {
        var copy = LoginController.copyFor(true, true);

        assertEquals("Create your account", copy.heading());
        assertTrue(copy.sub().startsWith("First run"),
                "first run must say so, got: " + copy.sub());
        assertEquals("Create account", copy.primary());
        assertEquals("Back to sign in", copy.link());
    }

    @Test
    void aReturningOperatorCreatesWithoutTheFirstRunWording() {
        var copy = LoginController.copyFor(true, false);

        assertEquals("Create an account", copy.heading());
        assertTrue(copy.sub().startsWith("New operator"),
                "got: " + copy.sub());
    }

    /**
     * The FXML ships the sign in wording so the frame never flashes the
     * wrong mode before the first run check answers (which runs off the FX
     * thread). An edit that breaks the match shows the user a flicker.
     */
    @Test
    void thePrepaintFxmlMatchesTheSignInCopy() throws Exception {
        String fxml = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/resources/com/argus/ui/view/login.fxml"));
        var copy = LoginController.copyFor(false, false);

        assertTrue(fxml.contains("text=\"" + copy.heading() + "\""),
                "login.fxml heading must prepaint " + copy.heading());
        assertTrue(fxml.contains("text=\"" + copy.sub() + "\""),
                "login.fxml sub line must prepaint " + copy.sub());
        assertTrue(fxml.contains("text=\"" + copy.primary() + "\""),
                "login.fxml primary must prepaint " + copy.primary());
        assertTrue(fxml.contains("text=\"" + copy.link() + "\""),
                "login.fxml toggle must prepaint " + copy.link());
    }
}
