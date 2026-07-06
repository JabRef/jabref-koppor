package org.jabref.gui.edit.automaticfiededitor.editfieldcontent;

import java.util.ArrayList;
import java.util.List;

import javafx.application.Platform;
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

import com.tobiasdiez.easybind.EasyBind;
import de.saxsys.mvvmfx.utils.validation.visualization.ControlsFxVisualizer;

import static org.jabref.gui.util.FieldsUtil.FIELD_STRING_CONVERTER;

public class EditFieldContentTabView extends EditFieldContentTabViewBase implements AutomaticFieldEditorTab, LocalizedView {
    private final NamedCompoundEdit compoundEdit;
    private final DialogService dialogService;
    private final List<BibEntry> selectedEntries;
    private final BibDatabase database;
    private final StateManager stateManager;
    private final ControlsFxVisualizer visualizer = new ControlsFxVisualizer();
    private EditFieldContentViewModel viewModel;

    public EditFieldContentTabView(BibDatabase database,
                                   NamedCompoundEdit compoundEdit,
                                   DialogService dialogService,
                                   StateManager stateManager) {
        this.compoundEdit = compoundEdit;
        this.dialogService = dialogService;
        this.selectedEntries = new ArrayList<>(stateManager.getSelectedEntries());
        this.database = database;
        this.stateManager = stateManager;

        initializeComponent();

        initialize();
    }

    private void initialize() {
        viewModel = new EditFieldContentViewModel(database, selectedEntries, compoundEdit, dialogService, stateManager);
        fieldComboBox.setConverter(FIELD_STRING_CONVERTER);

        showOnlySetFieldsCheckBox.setSelected(true);

        EasyBind.subscribe(showOnlySetFieldsCheckBox.selectedProperty(), selected -> {
            java.util.Collection<Field> items = selected
                                                ? FieldHelper.getSetFieldsOnly(selectedEntries, viewModel.getAllFields())
                                                : viewModel.getAllFields();

            fieldComboBox.getItems().setAll(items);
            if (!fieldComboBox.getItems().isEmpty()) {
                fieldComboBox.getSelectionModel().selectFirst();
            }
        });

        fieldComboBox.valueProperty().bindBidirectional(viewModel.selectedFieldProperty());
        EasyBind.listen(fieldComboBox.getEditor().textProperty(), _ -> fieldComboBox.commitValue());

        fieldValueTextField.textProperty().bindBidirectional(viewModel.fieldValueProperty());

        overwriteFieldContentCheckBox.selectedProperty().bindBidirectional(viewModel.overwriteFieldContentProperty());

        appendValueButton.disableProperty().bind(viewModel.canAppendProperty().not());
        setValueButton.disableProperty().bind(viewModel.fieldValidationStatus().validProperty().not());
        overwriteFieldContentCheckBox.disableProperty().bind(viewModel.fieldValidationStatus().validProperty().not());

        Platform.runLater(() -> visualizer.initVisualization(viewModel.fieldValidationStatus(), fieldComboBox, true));
    }

    @Override
    public Pane getContent() {
        return this;
    }

    @Override
    public String getTabName() {
        return Localization.lang("Edit content");
    }

    void appendToFieldValue() {
        viewModel.appendToFieldValue();
    }

    void setFieldValue() {
        viewModel.setFieldValue();
    }
}
