package org.jabref.gui.fieldeditors;

import java.util.Optional;

import javax.swing.undo.UndoManager;

import javafx.scene.Parent;
import javafx.scene.control.Tooltip;

import org.jabref.gui.DialogService;
import org.jabref.gui.autocompleter.SuggestionProvider;
import org.jabref.gui.preferences.GuiPreferences;
import org.jabref.injection.Injector;
import org.jabref.logic.icore.ConferenceRepository;
import org.jabref.logic.integrity.FieldCheckers;
import org.jabref.logic.l10n.Localization;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;

import jakarta.inject.Inject;

public class ICORERankingEditor extends ICORERankingEditorBase implements FieldEditorFX {
    private final ICORERankingEditorViewModel viewModel;

    @Inject private DialogService dialogService;
    @Inject private UndoManager undoManager;
    @Inject private GuiPreferences preferences;
    @Inject private ConferenceRepository conferenceRepository;

    private Optional<BibEntry> entry = Optional.empty();

    public ICORERankingEditor(Field field,
                              SuggestionProvider<?> suggestionProvider,
                              FieldCheckers fieldCheckers) {

        Injector.registerExistingAndInject(this);

        this.viewModel = new ICORERankingEditorViewModel(
                field,
                suggestionProvider,
                fieldCheckers,
                dialogService,
                undoManager,
                preferences,
                conferenceRepository
        );

        initializeComponent();

        textField.setId(field.getName());
        textField.textProperty().bindBidirectional(viewModel.textProperty());

        lookupICORERankButton.setTooltip(
                new Tooltip(Localization.lang("Look up conference rank"))
        );
        visitICOREConferencePageButton.setTooltip(
                new Tooltip(Localization.lang("Visit ICORE conference page"))
        );
        visitICOREConferencePageButton.disableProperty().bind(textField.textProperty().isEmpty());
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

    void lookupRank() {
        entry.ifPresent(viewModel::lookupIdentifier);
    }

    void openExternalLink() {
        viewModel.openExternalLink();
    }
}

