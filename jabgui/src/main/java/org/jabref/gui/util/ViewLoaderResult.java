package org.jabref.gui.util;

import javafx.scene.Parent;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Result of [ViewLoader#load()], giving access to the loaded root node and the
/// initialized controller. Drop-in replacement for afterburner.fx's
/// `ViewLoaderResult`.
@NullMarked
public class ViewLoaderResult {

    private final Parent view;
    private final @Nullable Object controller;

    public ViewLoaderResult(Parent view, @Nullable Object controller) {
        this.view = view;
        this.controller = controller;
    }

    public Parent getView() {
        return view;
    }

    /// @return the controller, or null if the FXML file does not declare an `fx:controller`
    public @Nullable Object getController() {
        return controller;
    }

    /// Sets the loaded view as the content of the given dialog pane.
    public void setAsContent(DialogPane dialogPane) {
        dialogPane.setContent(view);
    }

    /// Sets the loaded view as the dialog pane of the given dialog.
    /// The FXML root element has to be a [DialogPane].
    public <T> void setAsDialogPane(Dialog<T> dialog) {
        if (view instanceof DialogPane dialogPane) {
            dialog.setDialogPane(dialogPane);
        } else {
            throw new IllegalStateException("View " + view.getClass().getName() + " has to derive from DialogPane");
        }
    }
}
