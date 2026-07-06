package org.jabref.gui.fieldeditors;

import java.util.List;
import java.util.function.Supplier;

import javax.swing.undo.UndoManager;

import javafx.event.ActionEvent;
import javafx.scene.Parent;
import javafx.scene.control.MenuItem;

import org.jabref.gui.DialogService;
import org.jabref.gui.autocompleter.SuggestionProvider;
import org.jabref.gui.fieldeditors.contextmenu.EditorMenus;
import org.jabref.gui.keyboard.KeyBindingRepository;
import org.jabref.gui.l10n.LocalizedView;
import org.jabref.gui.preferences.GuiPreferences;
import org.jabref.gui.undo.RedoAction;
import org.jabref.gui.undo.UndoAction;
import org.jabref.injection.Injector;
import org.jabref.logic.formatter.bibtexfields.CleanupUrlFormatter;
import org.jabref.logic.formatter.bibtexfields.TrimWhitespaceFormatter;
import org.jabref.logic.integrity.FieldCheckers;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;

import jakarta.inject.Inject;

public class UrlEditor extends UrlEditorBase implements FieldEditorFX, LocalizedView {

    private final UrlEditorViewModel viewModel;

    @Inject private DialogService dialogService;
    @Inject private GuiPreferences preferences;
    @Inject private KeyBindingRepository keyBindingRepository;
    @Inject private UndoManager undoManager;

    public UrlEditor(Field field,
                     SuggestionProvider<?> suggestionProvider,
                     FieldCheckers fieldCheckers,
                     UndoAction undoAction,
                     RedoAction redoAction) {
        Injector.registerExistingAndInject(this);

        this.viewModel = new UrlEditorViewModel(field, suggestionProvider, dialogService, preferences, fieldCheckers, undoManager);

        initializeComponent();

        textField.setId(field.getName());
        establishBinding(textField, viewModel.textProperty(), keyBindingRepository, undoAction, redoAction);

        Supplier<List<MenuItem>> contextMenuSupplier = EditorMenus.getCleanupUrlMenu(textField);
        textField.initContextMenu(contextMenuSupplier, preferences.getKeyBindingRepository());

        // init paste handler for UrlEditor to format pasted url link in textArea
        textField.setAdditionalPasteActionHandler(() -> textField.setText(new CleanupUrlFormatter().format(new TrimWhitespaceFormatter().format(textField.getText()))));

        new EditorValidator(preferences).configureValidation(viewModel.getFieldValidator().getValidationStatus(), textField);
    }

    public UrlEditorViewModel getViewModel() {
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

    void openExternalLink(ActionEvent event) {
        viewModel.openExternalLink();
    }
}
