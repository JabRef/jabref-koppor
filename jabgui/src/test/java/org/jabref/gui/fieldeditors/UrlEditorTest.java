package org.jabref.gui.fieldeditors;

import javax.swing.undo.UndoManager;

import javafx.scene.control.Button;

import org.jabref.gui.DialogService;
import org.jabref.gui.StateManager;
import org.jabref.gui.autocompleter.SuggestionProvider;
import org.jabref.gui.entryeditor.EntryEditorPreferences;
import org.jabref.gui.keyboard.KeyBindingRepository;
import org.jabref.gui.preferences.GuiPreferences;
import org.jabref.gui.undo.RedoAction;
import org.jabref.gui.undo.UndoAction;
import org.jabref.injection.Injector;
import org.jabref.logic.integrity.FieldCheckers;
import org.jabref.model.entry.field.StandardField;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/// Exercises the FXML/2-compiled field editor: `${viewModel.x}` expression bindings and
/// `onAction="method"` (no `#`, package-visible method) resolve against the code-behind class
/// instead of through `@FXML` reflection.
class UrlEditorTest extends ApplicationTest {

    private GuiPreferences preferences;

    @BeforeEach
    void setUp() {
        Injector.setModelOrService(StateManager.class, mock(StateManager.class));

        preferences = mock(GuiPreferences.class);
        when(preferences.getKeyBindingRepository()).thenReturn(new KeyBindingRepository());
        when(preferences.getEntryEditorPreferences()).thenReturn(mock(EntryEditorPreferences.class));

        Injector.setModelOrService(DialogService.class, mock(DialogService.class));
        Injector.setModelOrService(GuiPreferences.class, preferences);
        Injector.setModelOrService(KeyBindingRepository.class, preferences.getKeyBindingRepository());
        Injector.setModelOrService(UndoManager.class, mock(UndoManager.class));
    }

    private UrlEditor newEditor() {
        return new UrlEditor(
                StandardField.URL,
                mock(SuggestionProvider.class),
                mock(FieldCheckers.class),
                mock(UndoAction.class),
                mock(RedoAction.class));
    }

    @Test
    void sceneGraphIsInitialized() {
        UrlEditor editor = newEditor();

        assertEquals(2, editor.getChildren().size());
        assertInstanceOf(EditorTextField.class, editor.getChildren().get(0));
        assertInstanceOf(Button.class, editor.getChildren().get(1));
    }

    @Test
    void openLinkButtonDisabledStateFollowsViewModel() {
        UrlEditor editor = newEditor();
        Button openLinkButton = (Button) editor.getChildren().get(1);

        assertTrue(openLinkButton.isDisable());

        interact(() -> editor.getViewModel().textProperty().set("https://www.jabref.org"));
        assertFalse(openLinkButton.isDisable());
    }

    @Test
    void openLinkButtonIsWiredToOnActionHandler() {
        // Does not fire() the button: firing would delegate to viewModel.openExternalLink(), which
        // launches a real OS browser via NativeDesktop.openBrowser() - out of scope for this test,
        // and unsafe to trigger from a headless test run.
        UrlEditor editor = newEditor();
        Button openLinkButton = (Button) editor.getChildren().get(1);

        assertNotNull(openLinkButton.getOnAction());
    }
}
