package org.jabref.gui.fieldeditors;

import javax.swing.undo.UndoManager;

import javafx.scene.Parent;

import org.jabref.gui.autocompleter.SuggestionProvider;
import org.jabref.gui.fieldeditors.contextmenu.EditorMenus;
import org.jabref.gui.keyboard.KeyBindingRepository;
import org.jabref.gui.l10n.LocalizedView;
import org.jabref.gui.preferences.GuiPreferences;
import org.jabref.gui.undo.RedoAction;
import org.jabref.gui.undo.UndoAction;
import org.jabref.injection.Injector;
import org.jabref.logic.integrity.FieldCheckers;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;

import jakarta.inject.Inject;

public class OwnerEditor extends OwnerEditorBase implements FieldEditorFX, LocalizedView {

    private final OwnerEditorViewModel viewModel;

    @Inject private GuiPreferences preferences;
    @Inject private KeyBindingRepository keyBindingRepository;
    @Inject private UndoManager undoManager;

    public OwnerEditor(Field field,
                       SuggestionProvider<?> suggestionProvider,
                       FieldCheckers fieldCheckers,
                       UndoAction undoAction,
                       RedoAction redoAction) {
        Injector.registerExistingAndInject(this);

        this.viewModel = new OwnerEditorViewModel(field, suggestionProvider, preferences, fieldCheckers, undoManager);

        initializeComponent();

        textField.setId(field.getName());
        establishBinding(textField, viewModel.textProperty(), keyBindingRepository, undoAction, redoAction);
        textField.initContextMenu(EditorMenus.getNameMenu(textField), keyBindingRepository);
        new EditorValidator(preferences).configureValidation(viewModel.getFieldValidator().getValidationStatus(), textField);
    }

    public OwnerEditorViewModel getViewModel() {
        return viewModel;
    }

    @Override
    public void bindToEntry(BibEntry entry) {
        viewModel.bindToEntry(entry);
    }

    @Override
    public Parent getNode() {
        return this;
    }

    void setOwner() {
        viewModel.setOwner();
    }
}
