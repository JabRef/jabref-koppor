package org.jabref.gui.fieldeditors;

import java.util.Collections;

import javax.swing.undo.UndoManager;

import javafx.scene.Parent;

import org.jabref.gui.DialogService;
import org.jabref.gui.actions.ActionFactory;
import org.jabref.gui.actions.StandardActions;
import org.jabref.gui.autocompleter.SuggestionProvider;
import org.jabref.gui.keyboard.KeyBindingRepository;
import org.jabref.gui.l10n.LocalizedView;
import org.jabref.gui.preferences.GuiPreferences;
import org.jabref.gui.undo.RedoAction;
import org.jabref.gui.undo.UndoAction;
import org.jabref.injection.Injector;
import org.jabref.logic.integrity.FieldCheckers;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;

import jakarta.inject.Inject;

public class CitationKeyEditor extends CitationKeyEditorBase implements FieldEditorFX, LocalizedView {

    private final CitationKeyEditorViewModel viewModel;

    @Inject private GuiPreferences preferences;
    @Inject private KeyBindingRepository keyBindingRepository;
    @Inject private DialogService dialogService;
    @Inject private UndoManager undoManager;

    public CitationKeyEditor(Field field,
                             SuggestionProvider<?> suggestionProvider,
                             FieldCheckers fieldCheckers,
                             BibDatabaseContext databaseContext,
                             UndoAction undoAction,
                             RedoAction redoAction) {

        Injector.registerExistingAndInject(this);

        this.viewModel = new CitationKeyEditorViewModel(
                field,
                suggestionProvider,
                fieldCheckers,
                preferences,
                databaseContext,
                undoManager,
                dialogService);

        initializeComponent();

        textField.setId(field.getName());
        establishBinding(textField, viewModel.textProperty(), keyBindingRepository, undoAction, redoAction);
        textField.initContextMenu(Collections::emptyList, keyBindingRepository);
        new EditorValidator(preferences).configureValidation(viewModel.getFieldValidator().getValidationStatus(), textField);
    }

    public CitationKeyEditorViewModel getViewModel() {
        return viewModel;
    }

    @Override
    public void bindToEntry(BibEntry entry) {
        viewModel.bindToEntry(entry);

        // Configure button to generate citation key
        new ActionFactory().configureIconButton(
                StandardActions.GENERATE_CITE_KEY,
                viewModel.getGenerateCiteKeyCommand(),
                generateCitationKeyButton);
    }

    @Override
    public Parent getNode() {
        return this;
    }
}
