package org.jabref.gui.fieldeditors;

import java.util.List;

import javax.swing.undo.UndoManager;

import javafx.collections.FXCollections;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Tooltip;

import org.jabref.gui.DialogService;
import org.jabref.gui.StateManager;
import org.jabref.gui.autocompleter.SuggestionProvider;
import org.jabref.gui.fieldeditors.contextmenu.DefaultMenu;
import org.jabref.gui.preferences.GuiPreferences;
import org.jabref.gui.util.ViewModelListCellFactory;
import org.jabref.injection.Injector;
import org.jabref.logic.citation.SearchCitationsRelationsService;
import org.jabref.logic.importer.fetcher.citation.CitationCountFetcherType;
import org.jabref.logic.integrity.FieldCheckers;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.util.TaskExecutor;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.FieldTextMapper;

import jakarta.inject.Inject;

public class CitationCountEditor extends CitationCountEditorBase implements FieldEditorFX {
    private final CitationCountEditorViewModel viewModel;

    // FXML/2 does not carry Java generics: the generated base declares `fetcherComboBox` as the
    // raw `ComboBox` type. Cast once so the generic-typed calls below type-check.
    private final ComboBox<CitationCountFetcherType> fetcherComboBox;

    @Inject private DialogService dialogService;
    @Inject private GuiPreferences preferences;
    @Inject private UndoManager undoManager;
    @Inject private TaskExecutor taskExecutor;
    @Inject private StateManager stateManager;
    @Inject private SearchCitationsRelationsService searchCitationsRelationsService;

    public CitationCountEditor(Field field,
                               SuggestionProvider<?> suggestionProvider,
                               FieldCheckers fieldCheckers) {
        Injector.registerExistingAndInject(this);
        this.viewModel = new CitationCountEditorViewModel(
                field,
                suggestionProvider,
                fieldCheckers,
                taskExecutor,
                dialogService,
                undoManager,
                stateManager,
                preferences,
                searchCitationsRelationsService);

        initializeComponent();

        @SuppressWarnings("unchecked")
        ComboBox<CitationCountFetcherType> typedFetcherComboBox = (ComboBox<CitationCountFetcherType>) (ComboBox<?>) super.fetcherComboBox;
        this.fetcherComboBox = typedFetcherComboBox;

        textField.setId(field.getName());
        textField.textProperty().bindBidirectional(viewModel.textProperty());

        fetchCitationCountButton.setTooltip(
                new Tooltip(Localization.lang("Look up %0", FieldTextMapper.getDisplayName(field))));
        textField.initContextMenu(new DefaultMenu(textField), preferences.getKeyBindingRepository());

        fetcherComboBox.setItems(FXCollections.observableList(List.of(CitationCountFetcherType.values())));
        fetcherComboBox.setTooltip(new Tooltip(Localization.lang("Select citation fetcher")));
        fetcherComboBox.setPrefWidth(160);
        new ViewModelListCellFactory<CitationCountFetcherType>()
                .withText(CitationCountFetcherType::getName)
                .install(fetcherComboBox);
        fetcherComboBox.valueProperty().bindBidirectional(
                preferences.getEntryEditorPreferences().citationCountFetcherTypeProperty()
        );
        new EditorValidator(preferences).configureValidation(viewModel.getFieldValidator().getValidationStatus(), textField);
    }

    void fetchCitationCount() {
        viewModel.getCitationCount();
    }

    public CitationCountEditorViewModel getViewModel() {
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
}
