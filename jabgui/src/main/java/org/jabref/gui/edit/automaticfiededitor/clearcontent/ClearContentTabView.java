package org.jabref.gui.edit.automaticfiededitor.clearcontent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.Pane;

import org.jabref.gui.DialogService;
import org.jabref.gui.StateManager;
import org.jabref.gui.edit.automaticfiededitor.AutomaticFieldEditorTab;
import org.jabref.gui.edit.automaticfiededitor.FieldHelper;
import org.jabref.gui.l10n.LocalizedView;
import org.jabref.gui.undo.NamedCompoundEdit;
import org.jabref.logic.l10n.Localization;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;

import org.jspecify.annotations.NonNull;

import static org.jabref.gui.util.FieldsUtil.FIELD_STRING_CONVERTER;

public class ClearContentTabView extends ClearContentTabViewBase implements AutomaticFieldEditorTab, LocalizedView {

    private final List<BibEntry> selectedEntries;
    @NonNull private final StateManager stateManager;
    private final BibDatabase database;
    private final NamedCompoundEdit compoundEdit;
    private final DialogService dialogService;
    private ClearContentViewModel viewModel;

    // FXML/2 does not carry Java generics: the generated base declares `fieldComboBox` as the
    // raw `ComboBox` type. Cast once so the generic-typed calls below type-check.
    private final ComboBox<Field> fieldComboBox;

    public ClearContentTabView(BibDatabase database, NamedCompoundEdit compoundEdit, DialogService dialogService, StateManager stateManager) {
        this.database = database;
        this.compoundEdit = compoundEdit;
        this.dialogService = dialogService;
        this.selectedEntries = new ArrayList<>(stateManager.getSelectedEntries());
        this.stateManager = stateManager;

        initializeComponent();

        @SuppressWarnings("unchecked")
        ComboBox<Field> typedFieldComboBox = (ComboBox<Field>) (ComboBox<?>) super.fieldComboBox;
        this.fieldComboBox = typedFieldComboBox;

        initialize();
    }

    private void initialize() {
        viewModel = new ClearContentViewModel(database, selectedEntries, compoundEdit, dialogService, stateManager);

        fieldComboBox.setConverter(FIELD_STRING_CONVERTER);

        Set<Field> setFields = FieldHelper.getSetFieldsOnly(selectedEntries, viewModel.getAllFields());
        fieldComboBox.getItems().setAll(setFields);

        if (!fieldComboBox.getItems().isEmpty()) {
            fieldComboBox.getSelectionModel().selectFirst();
        }

        clearButton.disableProperty().bind(fieldComboBox.valueProperty().isNull());

        Platform.runLater(fieldComboBox::requestFocus);
    }

    void onClear() {
        Field chosen = fieldComboBox.getValue();
        if (chosen != null) {
            viewModel.clearField(chosen);
        }
    }

    @Override
    public Pane getContent() {
        return this;
    }

    @Override
    public String getTabName() {
        return Localization.lang("Clear content");
    }
}
