package org.jabref.gui.fieldeditors;

import java.util.Comparator;

import javax.swing.undo.UndoManager;

import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;

import org.jabref.gui.DialogService;
import org.jabref.gui.JabRefDialogService;
import org.jabref.gui.StateManager;
import org.jabref.gui.actions.ActionFactory;
import org.jabref.gui.actions.SimpleCommand;
import org.jabref.gui.actions.StandardActions;
import org.jabref.gui.autocompleter.SuggestionProvider;
import org.jabref.gui.clipboard.ClipBoardManager;
import org.jabref.gui.icon.IconTheme;
import org.jabref.gui.util.ViewModelListCellFactory;
import org.jabref.injection.Injector;
import org.jabref.logic.integrity.FieldCheckers;
import org.jabref.logic.l10n.Localization;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.EntryLinkList;
import org.jabref.model.entry.ParsedEntryLink;
import org.jabref.model.entry.field.Field;

import com.dlsc.gemsfx.TagsField;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LinkedEntriesEditor extends LinkedEntriesEditorBase implements FieldEditorFX {
    private static final Logger LOGGER = LoggerFactory.getLogger(LinkedEntriesEditor.class);
    private static final PseudoClass FOCUSED = PseudoClass.getPseudoClass("focused");

    private final LinkedEntriesEditorViewModel viewModel;

    // FXML/2 does not carry Java generics: the generated base declares `entryLinkField` as the
    // raw `TagsField` type. Cast once so the generic-typed lambdas below type-check.
    private final TagsField<ParsedEntryLink> tagsField;

    @Inject private DialogService dialogService;
    @Inject private ClipBoardManager clipBoardManager;
    @Inject private UndoManager undoManager;
    @Inject private StateManager stateManager;

    public LinkedEntriesEditor(Field field, BibDatabaseContext databaseContext, SuggestionProvider<?> suggestionProvider, FieldCheckers fieldCheckers) {
        Injector.registerExistingAndInject(this);

        this.viewModel = new LinkedEntriesEditorViewModel(field, suggestionProvider, databaseContext, fieldCheckers, undoManager, stateManager);

        initializeComponent();

        @SuppressWarnings("unchecked")
        TagsField<ParsedEntryLink> typedEntryLinkField = (TagsField<ParsedEntryLink>) (TagsField<?>) entryLinkField;
        this.tagsField = typedEntryLinkField;

        tagsField.setCellFactory(new ViewModelListCellFactory<ParsedEntryLink>().withText(ParsedEntryLink::getKey));
        tagsField.setSuggestionProvider(request -> viewModel.getSuggestions(request.getUserText()));

        tagsField.setTagViewFactory(this::createTag);
        tagsField.setConverter(viewModel.getStringConverter());
        tagsField.setNewItemProducer(searchText -> viewModel.getStringConverter().fromString(searchText));
        tagsField.setMatcher((entryLink, searchText) -> entryLink.getKey().toLowerCase().startsWith(searchText.toLowerCase()));
        tagsField.setComparator(Comparator.comparing(ParsedEntryLink::getKey));

        tagsField.setShowSearchIcon(false);
        tagsField.setOnMouseClicked(event -> tagsField.getEditor().requestFocus());
        tagsField.getEditor().getStyleClass().clear();
        tagsField.getEditor().getStyleClass().add("tags-field-editor");
        tagsField.getEditor().focusedProperty().addListener((observable, oldValue, newValue) -> tagsField.pseudoClassStateChanged(FOCUSED, newValue));

        String separator = EntryLinkList.SEPARATOR;
        tagsField.getEditor().setOnKeyReleased(event -> {
            if (event.getText().equals(separator)) {
                tagsField.commit();
                event.consume();
            }
        });

        Bindings.bindContentBidirectional(tagsField.getTags(), viewModel.linkedEntriesProperty());
    }

    private Node createTag(ParsedEntryLink entryLink) {
        Label tagLabel = new Label();
        tagLabel.setText(tagsField.getConverter().toString(entryLink));
        tagLabel.setGraphic(IconTheme.JabRefIcons.REMOVE_TAGS.getGraphicNode());
        tagLabel.getGraphic().setOnMouseClicked(event -> tagsField.removeTags(entryLink));
        tagLabel.setContentDisplay(ContentDisplay.RIGHT);
        tagLabel.setOnMouseClicked(event -> {
            if ((event.getClickCount() == 2 || event.isControlDown()) && event.getButton() == MouseButton.PRIMARY) {
                viewModel.jumpToEntry(entryLink);
            }
        });

        ContextMenu contextMenu = new ContextMenu();
        ActionFactory factory = new ActionFactory();
        contextMenu.getItems().addAll(
                factory.createMenuItem(StandardActions.COPY, new TagContextAction(StandardActions.COPY, entryLink)),
                factory.createMenuItem(StandardActions.CUT, new TagContextAction(StandardActions.CUT, entryLink)),
                factory.createMenuItem(StandardActions.DELETE, new TagContextAction(StandardActions.DELETE, entryLink))
        );
        tagLabel.setContextMenu(contextMenu);
        return tagLabel;
    }

    public LinkedEntriesEditorViewModel getViewModel() {
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

    private class TagContextAction extends SimpleCommand {
        private final StandardActions command;
        private final ParsedEntryLink entryLink;

        public TagContextAction(StandardActions command, ParsedEntryLink entryLink) {
            this.command = command;
            this.entryLink = entryLink;
        }

        @Override
        public void execute() {
            switch (command) {
                case COPY -> {
                    clipBoardManager.setContent(entryLink.getKey());
                    dialogService.notify(Localization.lang("Copied '%0' to clipboard.",
                            JabRefDialogService.shortenDialogMessage(entryLink.getKey())));
                }
                case CUT -> {
                    clipBoardManager.setContent(entryLink.getKey());
                    dialogService.notify(Localization.lang("Copied '%0' to clipboard.",
                            JabRefDialogService.shortenDialogMessage(entryLink.getKey())));
                    tagsField.removeTags(entryLink);
                }
                case DELETE ->
                        tagsField.removeTags(entryLink);
                default ->
                        LOGGER.info("Action {} not defined", command.getText());
            }
        }
    }
}
