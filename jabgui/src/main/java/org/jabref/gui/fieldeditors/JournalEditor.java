package org.jabref.gui.fieldeditors;

import javax.swing.undo.UndoManager;

import javafx.scene.Parent;

import org.jabref.gui.DialogService;
import org.jabref.gui.autocompleter.AutoCompletionTextInputBinding;
import org.jabref.gui.autocompleter.SuggestionProvider;
import org.jabref.gui.fieldeditors.contextmenu.DefaultMenu;
import org.jabref.gui.keyboard.KeyBindingRepository;
import org.jabref.gui.l10n.LocalizedView;
import org.jabref.gui.preferences.GuiPreferences;
import org.jabref.gui.undo.RedoAction;
import org.jabref.gui.undo.UndoAction;
import org.jabref.injection.Injector;
import org.jabref.logic.integrity.FieldCheckers;
import org.jabref.logic.journals.JournalAbbreviationRepository;
import org.jabref.logic.util.TaskExecutor;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;

import jakarta.inject.Inject;

public class JournalEditor extends JournalEditorBase implements FieldEditorFX, LocalizedView {

    private final JournalEditorViewModel viewModel;

    @Inject private DialogService dialogService;
    @Inject private GuiPreferences preferences;
    @Inject private KeyBindingRepository keyBindingRepository;
    @Inject private TaskExecutor taskExecutor;
    @Inject private JournalAbbreviationRepository abbreviationRepository;
    @Inject private UndoManager undoManager;

    public JournalEditor(Field field,
                         SuggestionProvider<?> suggestionProvider,
                         FieldCheckers fieldCheckers,
                         UndoAction undoAction,
                         RedoAction redoAction) {

        Injector.registerExistingAndInject(this);

        this.viewModel = new JournalEditorViewModel(
                field,
                suggestionProvider,
                abbreviationRepository,
                fieldCheckers,
                taskExecutor,
                dialogService,
                undoManager);

        textField.setId(field.getName());
        establishBinding(textField, viewModel.textProperty(), keyBindingRepository, undoAction, redoAction);
        textField.initContextMenu(new DefaultMenu(textField), keyBindingRepository);
        AutoCompletionTextInputBinding.autoComplete(textField, viewModel::complete);
        new EditorValidator(preferences).configureValidation(viewModel.getFieldValidator().getValidationStatus(), textField);
    }

    public JournalEditorViewModel getViewModel() {
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

    void toggleAbbreviation() {
        viewModel.toggleAbbreviation();
    }

    void showJournalInfo() {
        if (JournalInfoOptInDialogHelper.isJournalInfoEnabled(dialogService, preferences.getEntryEditorPreferences())) {
            viewModel.showJournalInfo(journalInfoButton);
        }
    }
}
