package org.jabref.gui.cleanup;

import java.util.List;

import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TabPane;

import org.jabref.logic.l10n.Localization;

import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/// Exercises the FXML/2 dialog-pane pattern: `ButtonType` declarations (named constructor
/// arguments, `fx:constant`) and localized button text.
class CleanupDialogPaneTest extends ApplicationTest {

    @Test
    void paneIsInitialized() {
        CleanupDialogPane pane = new CleanupDialogPane();

        assertEquals(List.of(pane.cleanUpButton, ButtonType.CANCEL), pane.getButtonTypes());
        assertEquals(Localization.lang("Clean up"), pane.cleanUpButton.getText());
        assertEquals(ButtonBar.ButtonData.APPLY, pane.cleanUpButton.getButtonData());
        assertInstanceOf(TabPane.class, pane.getContent());
    }
}
