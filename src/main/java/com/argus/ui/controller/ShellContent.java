package com.argus.ui.controller;

/** A view hosted in the shell content area. Called when the pane is swapped out. */
public interface ShellContent {

    /** Release threads, subscriptions, listeners. Default no-op for stateless panes. */
    default void onHidden() {
    }
}
