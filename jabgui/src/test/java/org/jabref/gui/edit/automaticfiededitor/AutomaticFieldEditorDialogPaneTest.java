package org.jabref.gui.edit.automaticfiededitor;

import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TabPane;

import org.jabref.logic.l10n.Localization;

import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/// Exercises the FXML/2 dialog-pane split (mirrors CleanupDialogPane): the "Keep modifications"
/// button and the tabPane placeholder that AutomaticFieldEditorDialog populates with tabs.
class AutomaticFieldEditorDialogPaneTest extends ApplicationTest {

    @Test
    void paneIsInitialized() {
        AutomaticFieldEditorDialogPane pane = new AutomaticFieldEditorDialogPane();

        assertEquals(2, pane.getButtonTypes().size());
        assertEquals(Localization.lang("Keep modifications"), pane.getButtonTypes().getFirst().getText());
        assertEquals(ButtonBar.ButtonData.OK_DONE, pane.getButtonTypes().getFirst().getButtonData());
        assertEquals(ButtonType.CANCEL, pane.getButtonTypes().getLast());
        assertInstanceOf(TabPane.class, pane.getContent());
        assertInstanceOf(TabPane.class, pane.tabPane);
    }
}
