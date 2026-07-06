package org.jabref.gui.edit.automaticfiededitor.clearcontent;

import javafx.collections.FXCollections;

import org.jabref.gui.DialogService;
import org.jabref.gui.StateManager;
import org.jabref.gui.undo.NamedCompoundEdit;
import org.jabref.logic.l10n.Localization;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.testfx.framework.junit5.ApplicationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/// Exercises the FXML/2-compiled tab view after `AbstractAutomaticFieldEditorTabView` was
/// removed (it could not be the fx:subclass superclass AND a shared abstract base at the same
/// time - see PLAN-FXML2.md). Each tab now implements `AutomaticFieldEditorTab` directly.
class ClearContentTabViewTest extends ApplicationTest {

    @Test
    void sceneGraphAndTabPlumbingAreInitialized() {
        StateManager stateManager = mock(StateManager.class, Answers.RETURNS_DEEP_STUBS);
        BibEntry entry = new BibEntry(BibEntry.DEFAULT_TYPE).withField(StandardField.YEAR, "2015");
        when(stateManager.getSelectedEntries()).thenReturn(FXCollections.observableArrayList(entry));

        ClearContentTabView tabView = new ClearContentTabView(
                new BibDatabase(),
                mock(NamedCompoundEdit.class),
                mock(DialogService.class),
                stateManager);

        assertSame(tabView, tabView.getContent());
        assertEquals(Localization.lang("Clear content"), tabView.getTabName());
        assertTrue(tabView.getChildren().size() > 0);
    }
}
