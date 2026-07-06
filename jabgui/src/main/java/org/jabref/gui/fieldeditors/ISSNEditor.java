package org.jabref.gui.fieldeditors;

import java.util.Optional;

import javax.swing.undo.UndoManager;

import javafx.scene.Parent;

import org.jabref.gui.DialogService;
import org.jabref.gui.StateManager;
import org.jabref.gui.autocompleter.SuggestionProvider;
import org.jabref.gui.fieldeditors.contextmenu.DefaultMenu;
import org.jabref.gui.keyboard.KeyBindingRepository;
import org.jabref.gui.l10n.LocalizedView;
import org.jabref.gui.preferences.GuiPreferences;
import org.jabref.gui.undo.RedoAction;
import org.jabref.gui.undo.UndoAction;
import org.jabref.injection.Injector;
import org.jabref.logic.integrity.FieldCheckers;
import org.jabref.logic.util.TaskExecutor;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;

import jakarta.inject.Inject;

// TODO: Document why this is not an IdentifierEditor like ISBN and EPrint
//       If there is no reason, then integrate it in IdentifierEditor
public class ISSNEditor extends ISSNEditorBase implements FieldEditorFX, LocalizedView {
    private final ISSNEditorViewModel viewModel;

    @Inject private DialogService dialogService;
    @Inject private GuiPreferences preferences;
    @Inject private KeyBindingRepository keyBindingRepository;
    @Inject private UndoManager undoManager;
    @Inject private TaskExecutor taskExecutor;
    @Inject private StateManager stateManager;

    private Optional<BibEntry> entry = Optional.empty();

    public ISSNEditor(Field field,
                      SuggestionProvider<?> suggestionProvider,
                      FieldCheckers fieldCheckers,
                      UndoAction undoAction,
                      RedoAction redoAction) {

        Injector.registerExistingAndInject(this);

        this.viewModel = new ISSNEditorViewModel(
                field,
                suggestionProvider,
                fieldCheckers,
                taskExecutor,
                dialogService,
                undoManager,
                stateManager,
                preferences);

        initializeComponent();

        textField.setId(field.getName());
        establishBinding(textField, viewModel.textProperty(), keyBindingRepository, undoAction, redoAction);
        textField.initContextMenu(new DefaultMenu(textField), keyBindingRepository);
        new EditorValidator(preferences).configureValidation(viewModel.getFieldValidator().getValidationStatus(), textField);
    }

    public ISSNEditorViewModel getViewModel() {
        return viewModel;
    }

    @Override
    public void bindToEntry(BibEntry entry) {
        this.entry = Optional.of(entry);
        viewModel.bindToEntry(entry);
    }

    @Override
    public Parent getNode() {
        return this;
    }

    @Override
    public void requestFocus() {
        textField.requestFocus();
    }

    void fetchInformationByIdentifier() {
        entry.ifPresent(viewModel::fetchBibliographyInformation);
    }

    void showJournalInfo() {
        if (JournalInfoOptInDialogHelper.isJournalInfoEnabled(dialogService, preferences.getEntryEditorPreferences())) {
            viewModel.showJournalInfo(journalInfoButton);
        }
    }
}
