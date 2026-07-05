package org.jabref.gui.entryeditor;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.SequencedSet;
import java.util.Set;
import java.util.regex.Pattern;

import javax.swing.undo.UndoManager;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.jabref.gui.StateManager;
import org.jabref.gui.fieldeditors.FieldEditorFX;
import org.jabref.gui.icon.IconTheme;
import org.jabref.gui.menus.ChangeEntryTypeMenu;
import org.jabref.gui.preferences.GuiPreferences;
import org.jabref.gui.preview.PreviewPanel;
import org.jabref.gui.undo.RedoAction;
import org.jabref.gui.undo.UndoAction;
import org.jabref.logic.journals.JournalAbbreviationRepository;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.util.strings.StringUtil;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.database.BibDatabaseMode;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.BibEntryTypesManager;
import org.jabref.model.entry.event.FieldChangedEvent;
import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.FieldFactory;
import org.jabref.model.entry.field.FieldTextMapper;
import org.jabref.model.entry.field.InternalField;
import org.jabref.model.entry.field.OrFields;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.field.UserSpecificCommentField;

import com.google.common.eventbus.Subscribe;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// The single scroll-list tab ("Main") showing *all* fields of an entry (issue #12711):
/// the citation key, all required fields (even when unset), and every set field.
/// Replaces the classic category tabs (required / optional / other / …).
///
/// Below the main fields sits a chip bar for adding unset optional fields ("Show more"
/// reveals the secondary-optional ones). The identifiers, files & links, bibliometrics,
/// comments, and meta groups are always-present collapsible sections — collapsed when
/// empty — each with its own add-chips for its unset member fields. A free-form
/// field-name box at the bottom adds arbitrary fields.
@NullMarked
public class AllFieldsTab extends FieldsEditorTab {

    /// Preferred number of visible text rows for multiline editors in the scroll list
    /// (instead of the JavaFX TextArea default of 10).
    private static final int MULTILINE_ROWS = 4;

    /// Pixels of preferred height granted per weight unit for editors with weight > 1
    /// (e.g. the linked-files list), since percent-height rows do not exist in the scroll list.
    private static final double HEIGHT_PER_WEIGHT = 60;

    /// Characters not allowed in the user-specific comment field name.
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]");

    private final BibEntryTypesManager entryTypesManager;
    private final GuiPreferences guiPreferences;
    private final UndoManager undoManager;

    /// The current user's personal comment field (derived from the default-owner preference).
    private final UserSpecificCommentField userSpecificCommentField;

    /// Fields the user added via chip / free-form box that are still empty: they are not part
    /// of [BibEntry#getFields()] yet, but must stay visible while this entry is edited.
    private final Set<Field> userAddedFields = new LinkedHashSet<>();
    private @Nullable BibEntry entryOfUserAddedFields;

    /// Manual expand/collapse decisions per section, kept across rebuilds for the current
    /// entry (cleared on entry switch). Without an override, a section is expanded iff it
    /// contains at least one shown field.
    private final Map<FieldListSections.SectionType, Boolean> sectionExpandOverrides =
            new EnumMap<>(FieldListSections.SectionType.class);

    /// Sticky per tab instance: whether the secondary-optional chips are expanded.
    private boolean showSecondaryOptionalChips;

    /// The entry whose event bus this tab is currently subscribed to (for live refresh
    /// when fields are set/unset from outside, e.g. Source tab, fetchers, undo).
    private Optional<BibEntry> subscribedEntry = Optional.empty();

    /// Scroll content: semantic preview + main grid + chip bar + section panes +
    /// free-form add row.
    private final VBox listContainer = new VBox();

    /// The editable semantic preview at the top of the tab (concept #2, issue #12711).
    private final SemanticPreviewFlow previewFlow = new SemanticPreviewFlow();

    /// Fields currently represented in the semantic preview (as value or placeholder).
    /// They are subtracted from all rows and chip lists below — the preview takes
    /// precedence, a field never appears twice (no-duplication rule).
    private SequencedSet<Field> coveredByPreview = new LinkedHashSet<>();

    /// In-place editing: the preview-covered field currently edited in the overlay row
    /// directly beneath the flow; empty when no in-place editor is open.
    private Optional<Field> inPlaceEditingField = Optional.empty();

    /// The field's value when the in-place editor was opened; Esc restores it.
    private Optional<String> inPlaceEditingPreviousValue = Optional.empty();

    /// Overlay row hosting the in-place editor (field label + bound editor node).
    private final HBox inPlaceEditorRow = new HBox();

    /// Header line above the preview: "@type · citationkey"; the key is click-to-edit,
    /// the type opens the change-entry-type menu.
    private final HBox headerRow = new HBox();

    /// Guards the focus-loss listener while the editor is being closed programmatically.
    private boolean closingInPlaceEditor;

    public AllFieldsTab(UndoManager undoManager,
                        UndoAction undoAction,
                        RedoAction redoAction,
                        GuiPreferences preferences,
                        BibEntryTypesManager entryTypesManager,
                        JournalAbbreviationRepository journalAbbreviationRepository,
                        StateManager stateManager,
                        PreviewPanel previewPanel) {
        super(
                false,
                undoManager,
                undoAction,
                redoAction,
                preferences,
                journalAbbreviationRepository,
                stateManager,
                previewPanel
        );

        this.entryTypesManager = entryTypesManager;
        this.guiPreferences = preferences;
        this.undoManager = undoManager;
        String defaultOwner = NON_ALPHANUMERIC.matcher(
                preferences.getOwnerPreferences().getDefaultOwner().toLowerCase(Locale.ROOT)).replaceAll("-");
        this.userSpecificCommentField = new UserSpecificCommentField(defaultOwner);
        this.listContainer.getStyleClass().add("all-fields-container");

        setText(EntryEditorTabModel.BuiltIn.ALL_FIELDS.displayName());
        setTooltip(new Tooltip(Localization.lang("Show all fields")));
        setGraphic(IconTheme.JabRefIcons.REQUIRED.getGraphicNode());

        headerRow.getStyleClass().add("semantic-preview-header");
        headerRow.setAlignment(Pos.CENTER_LEFT);

        inPlaceEditorRow.getStyleClass().add("semantic-preview-editor-row");
        inPlaceEditorRow.setAlignment(Pos.CENTER_LEFT);
        inPlaceEditorRow.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                closeInPlaceEditor(true);
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                closeInPlaceEditor(false);
                event.consume();
            }
        });
        // Focus leaving the overlay row closes the editor (value is already written
        // through); runLater so focus transfers inside the row do not close it.
        inPlaceEditorRow.focusWithinProperty().addListener((_, _, focused) -> {
            if (!focused && inPlaceEditingField.isPresent() && !closingInPlaceEditor) {
                Platform.runLater(() -> {
                    if (inPlaceEditingField.isPresent() && !closingInPlaceEditor && !inPlaceEditorRow.isFocusWithin()) {
                        closeInPlaceEditor(true);
                    }
                });
            }
        });
    }

    /// Order: citation key, required fields (entry-type order), set optional fields
    /// (important first, then detail; each in entry-type order), then all remaining set
    /// fields sorted by name, then still-empty user-added fields.
    // [impl->req~entry-editor.main-tab.single-list~1]
    @Override
    protected SequencedSet<Field> determineFieldsToShow(BibEntry entry) {
        if (entry != entryOfUserAddedFields) {
            userAddedFields.clear();
            sectionExpandOverrides.clear();
            entryOfUserAddedFields = entry;
        }

        BibDatabaseMode mode = getDatabaseMode();
        Set<Field> setFields = entry.getFields();
        SequencedSet<Field> fields = new LinkedHashSet<>();
        fields.add(InternalField.KEY_FIELD);
        entryTypesManager.enrich(entry.getType(), mode).ifPresent(entryType -> {
            for (OrFields orFields : entryType.getRequiredFields()) {
                fields.addAll(orFields.getFields());
            }
            entryType.getImportantOptionalFields().stream()
                     .filter(setFields::contains)
                     .forEach(fields::add);
            entryType.getDetailOptionalNotDeprecatedFields(mode).stream()
                     .filter(setFields::contains)
                     .forEach(fields::add);
        });
        setFields.stream()
                 .sorted(Comparator.comparing(Field::getName))
                 .forEach(fields::add);
        fields.addAll(userAddedFields);
        return fields;
    }

    @Override
    protected boolean stretchContentToTabHeight() {
        return false;
    }

    @Override
    protected Node getEditorContent() {
        return listContainer;
    }

    @Override
    protected void bindToEntry(BibEntry entry) {
        discardInPlaceEditor();
        if (subscribedEntry.filter(current -> current == entry).isEmpty()) {
            subscribedEntry.ifPresent(previous -> previous.unregisterListener(this));
            entry.registerListener(this);
            subscribedEntry = Optional.of(entry);
        }
        super.bindToEntry(entry);
    }

    /// Refreshes the list when a field is set or unset from outside this tab
    /// (Source tab, fetchers, undo, …). Rebuilds only when the set of shown fields
    /// actually changes, so typing inside a visible editor never rebuilds or steals focus.
    @Subscribe
    public void listen(FieldChangedEvent event) {
        if (subscribedEntry.filter(current -> current == event.getBibEntry()).isEmpty()) {
            return;
        }
        Platform.runLater(() -> refreshShownFieldsIfNeeded(event));
    }

    // [impl->req~entry-editor.main-tab.live-refresh~1]
    private void refreshShownFieldsIfNeeded(FieldChangedEvent event) {
        BibEntry entry = event.getBibEntry();
        if (getCurrentEntry() != entry) {
            return;
        }
        // A visible field that was just cleared stays visible while this entry is edited
        // (otherwise deleting the last character would remove the editor mid-edit).
        if (editors.containsKey(event.getField()) && StringUtil.isBlank(event.getNewValue())) {
            userAddedFields.add(event.getField());
        }
        if (inPlaceEditingField.isPresent()) {
            // Defer structural rebuilds while the overlay editor is open (no focus
            // theft); the flow still re-renders so the edited segment's text follows
            // the typing live. The deferred check runs in closeInPlaceEditor.
            previewFlow.render(computeSegments(entry), inPlaceEditingField, this::onSegmentClicked);
            return;
        }
        refreshAfterChange(entry);
    }

    /// Rebuilds when the shown or preview-covered field set changed; otherwise only
    /// re-renders the flow text and the header line.
    private void refreshAfterChange(BibEntry entry) {
        SequencedSet<Field> target = determineFieldsToShow(entry);
        CitationSegments segments = computeSegments(entry);
        if (!target.equals(editors.keySet()) || !coveredWithCitationKey(segments).equals(coveredByPreview)) {
            rebuildPanel(activeDatabaseContext(), entry);
        } else {
            previewFlow.render(segments, Optional.empty(), this::onSegmentClicked);
            renderHeaderRow(entry);
        }
    }

    private CitationSegments computeSegments(BibEntry entry) {
        return CitationSegments.of(entry, entryTypesManager.enrich(entry.getType(), getDatabaseMode()));
    }

    /// The preview also represents the citation key (in the header line above the flow),
    /// so it counts as covered for the no-duplication rule.
    private SequencedSet<Field> coveredWithCitationKey(CitationSegments segments) {
        SequencedSet<Field> covered = new LinkedHashSet<>(segments.coveredFields());
        covered.add(InternalField.KEY_FIELD);
        return covered;
    }

    // region header row

    /// "@type · citationkey": the type opens the change-entry-type menu, the key opens
    /// its in-place editor (CitationKeyEditor including the generate button).
    private void renderHeaderRow(BibEntry entry) {
        Label typeLabel = new Label("@" + entry.getType().getName());
        typeLabel.getStyleClass().add("semantic-preview-entry-type");
        typeLabel.setCursor(Cursor.HAND);
        typeLabel.setTooltip(new Tooltip(Localization.lang("Change entry type")));
        typeLabel.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                ContextMenu typeMenu = new ChangeEntryTypeMenu(
                        List.of(entry), activeDatabaseContext(), undoManager, entryTypesManager).asContextMenu();
                typeMenu.show(typeLabel, Side.BOTTOM, 0, 0);
            }
        });

        Label keyLabel = entry.getCitationKey()
                              .filter(StringUtil::isNotBlank)
                              .map(key -> {
                                  Label label = new Label(key);
                                  label.getStyleClass().add("semantic-preview-citation-key");
                                  return label;
                              })
                              .orElseGet(() -> {
                                  Label label = new Label("{{" + FieldTextMapper.getDisplayName(InternalField.KEY_FIELD) + "}}");
                                  label.getStyleClass().add("semantic-preview-citation-key-missing");
                                  return label;
                              });
        if (inPlaceEditingField.filter(InternalField.KEY_FIELD::equals).isPresent()) {
            keyLabel.getStyleClass().add("semantic-preview-editing");
        }
        keyLabel.setCursor(Cursor.HAND);
        keyLabel.setTooltip(new Tooltip(FieldTextMapper.getDisplayName(InternalField.KEY_FIELD)));
        keyLabel.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                onSegmentClicked(InternalField.KEY_FIELD);
            }
        });

        Label separator = new Label("·");
        separator.getStyleClass().add("semantic-preview-header-separator");
        headerRow.getChildren().setAll(typeLabel, separator, keyLabel);
    }

    // endregion

    // region in-place editing

    /// Clicking a preview segment opens the field's bound editor in the overlay row
    /// directly beneath the flow; the segment gets a highlight. (A literal swap inside
    /// the TextFlow is not feasible: field editors are compound HBox controls that
    /// would wreck the line layout.)
    // [impl->req~entry-editor.main-tab.in-place-edit~1]
    private void onSegmentClicked(Field field) {
        if (inPlaceEditingField.filter(field::equals).isPresent()) {
            if (editors.containsKey(field)) {
                editors.get(field).focus();
            }
            return;
        }
        if (!coveredByPreview.contains(field)) {
            requestFocus(field);
            return;
        }
        openInPlaceEditor(field);
    }

    private void openInPlaceEditor(Field field) {
        if (!editors.containsKey(field)) {
            return;
        }
        Optional.ofNullable(getCurrentEntry()).ifPresent(entry -> {
            closeInPlaceEditor(true);
            FieldEditorFX editor = editors.get(field);
            inPlaceEditingField = Optional.of(field);
            inPlaceEditingPreviousValue = entry.getField(field);

            Label label = new Label(FieldTextMapper.getDisplayName(field));
            label.getStyleClass().add("semantic-preview-editor-label");
            Node editorNode = editor.getNode();
            HBox.setHgrow(editorNode, Priority.ALWAYS);
            inPlaceEditorRow.getChildren().setAll(label, editorNode);
            int flowIndex = listContainer.getChildren().indexOf(previewFlow);
            listContainer.getChildren().add(flowIndex + 1, inPlaceEditorRow);

            previewFlow.render(computeSegments(entry), inPlaceEditingField, this::onSegmentClicked);
            renderHeaderRow(entry);
            Platform.runLater(editor::focus);
        });
    }

    /// Jump-to-field and the focus key bindings route preview-covered fields to the
    /// in-place editor; everything else focuses its regular editor row.
    @Override
    public void requestFocus(Field fieldName) {
        if (coveredByPreview.contains(fieldName) && editors.containsKey(fieldName)) {
            openInPlaceEditor(fieldName);
            return;
        }
        super.requestFocus(fieldName);
    }

    /// Closes the overlay editor. `keepValue = false` (Esc) restores the field to the
    /// value it had when the editor was opened. Afterwards the deferred layout check
    /// runs: rebuild if the shown/covered set changed while editing, else just drop
    /// the highlight.
    private void closeInPlaceEditor(boolean keepValue) {
        if (inPlaceEditingField.isEmpty()) {
            return;
        }
        closingInPlaceEditor = true;
        Field field = inPlaceEditingField.orElseThrow();
        Optional<BibEntry> entry = Optional.ofNullable(getCurrentEntry());
        try {
            if (!keepValue) {
                entry.ifPresent(current -> inPlaceEditingPreviousValue.ifPresentOrElse(
                        value -> current.setField(field, value),
                        () -> current.clearField(field)));
            }
        } finally {
            discardInPlaceEditor();
            closingInPlaceEditor = false;
        }
        entry.ifPresent(this::refreshAfterChange);
    }

    /// Drops the overlay editor without value restore or refresh (entry switch, rebind).
    private void discardInPlaceEditor() {
        inPlaceEditingField = Optional.empty();
        inPlaceEditingPreviousValue = Optional.empty();
        listContainer.getChildren().remove(inPlaceEditorRow);
        inPlaceEditorRow.getChildren().clear();
    }

    // endregion

    /// Main fields as a grid with natural row heights, then the optional-field chip bar,
    /// then the always-present collapsible sections (identifiers / files & links /
    /// bibliometrics / comments / meta, collapsed when empty) each with its own add-chips,
    /// then the free-form add row. The whole column scrolls instead of stretching to the
    /// tab height.
    @Override
    protected void layoutEditors(BibDatabaseContext bibDatabaseContext, BibEntry entry, boolean compressed, List<Label> labels) {
        // labels were created in editors-map iteration order (see FieldsEditorTab#setupPanel)
        Map<Field, Label> labelForField = new LinkedHashMap<>();
        int labelIndex = 0;
        for (Field field : editors.keySet()) {
            labelForField.put(field, labels.get(labelIndex));
            labelIndex++;
        }

        CitationSegments segments = computeSegments(entry);
        coveredByPreview = coveredWithCitationKey(segments);
        previewFlow.render(segments, inPlaceEditingField, this::onSegmentClicked);
        renderHeaderRow(entry);

        Map<FieldListSections.SectionType, SequencedSet<Field>> buckets =
                new EnumMap<>(FieldListSections.SectionType.class);
        for (FieldListSections.SectionType type : FieldListSections.SectionType.values()) {
            buckets.put(type, new LinkedHashSet<>());
        }
        // [impl->req~entry-editor.main-tab.no-duplication~1]
        Set<Field> suppressedAlternatives = unsetAlternativesOfSatisfiedGroups(entry);
        editors.keySet().stream()
               .filter(field -> !coveredByPreview.contains(field))
               .filter(field -> !suppressedAlternatives.contains(field))
               .forEach(field -> buckets.get(FieldListSections.sectionOf(field)).add(field));

        // Main section rows go into the (already cleared) inherited gridPane
        if (!gridPane.getStyleClass().contains("all-fields-list")) {
            gridPane.getStyleClass().add("all-fields-list");
        }
        addFieldRows(gridPane, buckets.get(FieldListSections.SectionType.MAIN), labelForField);

        listContainer.getChildren().setAll(headerRow, previewFlow, gridPane, createMainChipBar(bibDatabaseContext, entry));
        for (FieldListSections.SectionType type : FieldListSections.SectionType.values()) {
            if (type == FieldListSections.SectionType.MAIN) {
                continue;
            }
            listContainer.getChildren().add(
                    createSectionPane(type, buckets.get(type), labelForField, bibDatabaseContext, entry));
        }
        listContainer.getChildren().add(createFreeFormAddRow(bibDatabaseContext, entry));

        editors.values().forEach(AllFieldsTab::applyNaturalHeight);
    }

    /// Label/editor rows with natural heights, label column as narrow as its content.
    private void addFieldRows(GridPane grid, SequencedCollection<Field> fields, Map<Field, Label> labelForField) {
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints editorColumn = new ColumnConstraints();
        editorColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().setAll(labelColumn, editorColumn);

        int row = 0;
        for (Field field : fields) {
            Label label = labelForField.get(field);
            // FieldNameLabel sets prefHeight to infinity to fill the stretch layout's
            // percent-height rows; in the natural-height list that would blow up every
            // row's preferred height, so reset it to the computed size.
            label.setPrefHeight(Region.USE_COMPUTED_SIZE);
            GridPane.setValignment(label, VPos.TOP);
            grid.add(label, 0, row);
            grid.add(editors.get(field).getNode(), 1, row);
            row++;
        }
    }

    // region sections

    /// An always-present collapsible section: its shown fields as rows plus add-chips for
    /// its unset member fields. Collapsed by default when it contains no field; a manual
    /// expand/collapse survives rebuilds until another entry is opened.
    // [impl->req~entry-editor.main-tab.sections~1]
    private TitledPane createSectionPane(FieldListSections.SectionType type,
                                         SequencedSet<Field> shownFields,
                                         Map<Field, Label> labelForField,
                                         BibDatabaseContext bibDatabaseContext,
                                         BibEntry entry) {
        VBox content = new VBox();
        content.getStyleClass().add("all-fields-section-content");

        if (!shownFields.isEmpty()) {
            GridPane sectionGrid = new GridPane();
            sectionGrid.setHgap(10);
            sectionGrid.setVgap(8);
            addFieldRows(sectionGrid, shownFields, labelForField);
            content.getChildren().add(sectionGrid);
        }

        SequencedSet<Field> chipFields = FieldListSections.subtract(sectionMemberFields(type), visibleFields());
        if (!chipFields.isEmpty()) {
            FlowPane chips = new FlowPane();
            chips.getStyleClass().add("all-fields-add-chips");
            chipFields.forEach(field -> chips.getChildren().add(createAddChip(bibDatabaseContext, entry, field)));
            content.getChildren().add(chips);
        }

        TitledPane pane = new TitledPane(type.header().orElseThrow(), content);
        pane.getStyleClass().add("all-fields-section-pane");
        pane.setCollapsible(true);
        pane.setAnimated(false);
        pane.setExpanded(sectionExpandOverrides.getOrDefault(type, !shownFields.isEmpty()));
        pane.expandedProperty().addListener((_, _, expanded) -> sectionExpandOverrides.put(type, expanded));
        return pane;
    }

    /// All member fields of a section offered as add-chips; the comments section offers the
    /// general comment plus the current user's personal comment field (if enabled).
    // [impl->req~entry-editor.main-tab.section-chips~1]
    private SequencedSet<Field> sectionMemberFields(FieldListSections.SectionType type) {
        if (type == FieldListSections.SectionType.COMMENTS) {
            SequencedSet<Field> commentFields = new LinkedHashSet<>();
            commentFields.add(StandardField.COMMENT);
            if (guiPreferences.getEntryEditorPreferences().shouldShowUserCommentsFields()) {
                commentFields.add(userSpecificCommentField);
            }
            return commentFields;
        }
        return FieldListSections.fieldsOf(type);
    }

    // endregion

    // region add-field controls

    /// Chips for the entry type's unset optional fields that belong to the main section
    /// (identifier/file/comment fields get their chips inside their own section);
    /// "Show more" reveals the secondary-optional ones.
    private Node createMainChipBar(BibDatabaseContext bibDatabaseContext, BibEntry entry) {
        BibDatabaseMode mode = getDatabaseMode();

        FlowPane chips = new FlowPane();
        chips.getStyleClass().add("all-fields-add-chips");

        entryTypesManager.enrich(entry.getType(), mode).ifPresent(entryType -> {
            Set<Field> shown = visibleFields();
            FieldListSections.subtract(entryType.getImportantOptionalFields(), shown).stream()
                             .filter(field -> FieldListSections.sectionOf(field) == FieldListSections.SectionType.MAIN)
                             .forEach(field -> chips.getChildren().add(createAddChip(bibDatabaseContext, entry, field)));

            List<Field> secondary = FieldListSections.subtract(
                                                             entryType.getDetailOptionalNotDeprecatedFields(mode), shown).stream()
                                                     .filter(field -> FieldListSections.sectionOf(field) == FieldListSections.SectionType.MAIN)
                                                     .toList();
            if (!secondary.isEmpty()) {
                if (showSecondaryOptionalChips) {
                    secondary.forEach(field -> chips.getChildren().add(createAddChip(bibDatabaseContext, entry, field)));
                }
                Hyperlink toggle = new Hyperlink(showSecondaryOptionalChips
                                                 ? Localization.lang("Show less")
                                                 : Localization.lang("Show more"));
                toggle.setOnAction(_ -> {
                    showSecondaryOptionalChips = !showSecondaryOptionalChips;
                    rebuildPanel(bibDatabaseContext, entry);
                });
                chips.getChildren().add(toggle);
            }
        });

        return chips;
    }

    // [impl->req~entry-editor.main-tab.free-form-add~1]
    private Node createFreeFormAddRow(BibDatabaseContext bibDatabaseContext, BibEntry entry) {
        ComboBox<String> fieldNameBox = new ComboBox<>();
        fieldNameBox.setEditable(true);
        fieldNameBox.getItems().addAll(FieldFactory.getAllFieldsWithOutInternal().stream()
                                                   .map(Field::getName)
                                                   .sorted()
                                                   .toList());
        fieldNameBox.setPromptText(Localization.lang("Field name"));
        Button addButton = new Button(Localization.lang("Add"));
        Runnable addAction = () -> addFreeFormField(bibDatabaseContext, entry, fieldNameBox.getEditor().getText());
        addButton.setOnAction(_ -> addAction.run());
        fieldNameBox.getEditor().setOnAction(_ -> addAction.run());
        HBox freeFormRow = new HBox(fieldNameBox, addButton);
        freeFormRow.getStyleClass().add("all-fields-add-free-form");
        freeFormRow.setAlignment(Pos.CENTER_LEFT);
        return freeFormRow;
    }

    private Button createAddChip(BibDatabaseContext bibDatabaseContext, BibEntry entry, Field field) {
        Button chip = new Button("+ " + FieldTextMapper.getDisplayName(field));
        chip.getStyleClass().add("all-fields-add-chip");
        chip.setOnAction(_ -> showFieldEditor(bibDatabaseContext, entry, field));
        return chip;
    }

    private void addFreeFormField(BibDatabaseContext bibDatabaseContext, BibEntry entry, @Nullable String fieldName) {
        if (StringUtil.isBlank(fieldName)) {
            return;
        }
        showFieldEditor(bibDatabaseContext, entry, FieldFactory.parseField(entry.getType(), fieldName.trim()));
    }

    /// Makes an editor for `field` visible in the list (adding it as still-empty user-added
    /// field if necessary) and focuses it.
    // [impl->req~entry-editor.main-tab.add-chips~1]
    private void showFieldEditor(BibDatabaseContext bibDatabaseContext, BibEntry entry, Field field) {
        closeInPlaceEditor(true);
        userAddedFields.add(field);
        rebuildPanel(bibDatabaseContext, entry);
        Platform.runLater(() -> requestFocus(field));
    }

    private void rebuildPanel(BibDatabaseContext bibDatabaseContext, BibEntry entry) {
        setupPanel(bibDatabaseContext, entry, false);
    }

    // endregion

    /// Unset members of required [OrFields] groups whose requirement is already satisfied
    /// by another (set) member — e.g. the empty Author row of an editor-only `@book`.
    /// They are alternatives, not missing data, so they get no empty editor row below the
    /// preview. Explicitly user-added fields stay visible (free-form add is the escape
    /// hatch to fill the other alternative anyway).
    private Set<Field> unsetAlternativesOfSatisfiedGroups(BibEntry entry) {
        Set<Field> result = new LinkedHashSet<>();
        entryTypesManager.enrich(entry.getType(), getDatabaseMode()).ifPresent(entryType -> {
            for (OrFields group : entryType.getRequiredFields()) {
                if (group.hasExactlyOne()) {
                    continue;
                }
                boolean satisfied = group.getFields().stream()
                                         .anyMatch(field -> entry.getField(field).filter(value -> !value.isBlank()).isPresent());
                if (satisfied) {
                    group.getFields().stream()
                         .filter(field -> entry.getField(field).filter(value -> !value.isBlank()).isEmpty())
                         .filter(field -> !userAddedFields.contains(field))
                         .forEach(result::add);
                }
            }
        });
        return result;
    }

    /// Fields already visible somewhere on this tab: as an editor row or as a
    /// segment (value/placeholder) of the semantic preview.
    private Set<Field> visibleFields() {
        Set<Field> visible = new LinkedHashSet<>(editors.keySet());
        visible.addAll(coveredByPreview);
        return visible;
    }

    private BibDatabaseMode getDatabaseMode() {
        return stateManager.getActiveDatabase()
                           .map(BibDatabaseContext::getMode)
                           .orElse(BibDatabaseMode.BIBLATEX);
    }

    private BibDatabaseContext activeDatabaseContext() {
        return stateManager.getActiveDatabase().orElse(new BibDatabaseContext());
    }

    private static void applyNaturalHeight(FieldEditorFX editor) {
        normalizeInputHeights(editor.getNode());
        if ((editor.getWeight() > 1) && (editor.getNode() instanceof Region region)) {
            region.setPrefHeight(editor.getWeight() * HEIGHT_PER_WEIGHT);
        }
    }

    /// The classic stretch layout lets text inputs fill their percent-height rows by setting
    /// an infinite pref height ([org.jabref.gui.fieldeditors.EditorTextField]); in the
    /// natural-height list that blows up the rows' preferred heights, so reset text fields to
    /// their computed size and cap text areas at a few visible rows.
    private static void normalizeInputHeights(Node node) {
        if (node instanceof TextArea textArea) {
            textArea.setPrefRowCount(MULTILINE_ROWS);
            textArea.setPrefHeight(Region.USE_COMPUTED_SIZE);
        } else if (node instanceof TextField textField) {
            textField.setPrefHeight(Region.USE_COMPUTED_SIZE);
        } else if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(AllFieldsTab::normalizeInputHeights);
        }
    }
}
