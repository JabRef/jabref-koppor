package org.jabref.gui.edit.automaticfiededitor.copyormovecontent;

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

import com.tobiasdiez.easybind.EasyBind;
import de.saxsys.mvvmfx.utils.validation.visualization.ControlsFxVisualizer;

import static org.jabref.gui.util.FieldsUtil.FIELD_STRING_CONVERTER;

public class CopyOrMoveFieldContentTabView extends CopyOrMoveFieldContentTabViewBase implements AutomaticFieldEditorTab, LocalizedView {
    private final NamedCompoundEdit compoundEdit;
    private final DialogService dialogService;
    private final List<BibEntry> selectedEntries;
    private final BibDatabase database;
    private final StateManager stateManager;
    private final ControlsFxVisualizer visualizer = new ControlsFxVisualizer();
    private CopyOrMoveFieldContentTabViewModel viewModel;

    public CopyOrMoveFieldContentTabView(BibDatabase database,
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
        viewModel = new CopyOrMoveFieldContentTabViewModel(database, selectedEntries, compoundEdit, dialogService, stateManager);
        initializeFromAndToComboBox();

        viewModel.overwriteFieldContentProperty().bindBidirectional(overwriteFieldContentCheckBox.selectedProperty());

        moveContentButton.disableProperty().bind(viewModel.canMoveProperty().not());
        swapContentButton.disableProperty().bind(viewModel.canSwapProperty().not());
        copyContentButton.disableProperty().bind(viewModel.toFieldValidationStatus().validProperty().not());
        overwriteFieldContentCheckBox.disableProperty().bind(viewModel.toFieldValidationStatus().validProperty().not());

        Platform.runLater(() -> visualizer.initVisualization(viewModel.toFieldValidationStatus(), toFieldComboBox, true));
    }

    private void initializeFromAndToComboBox() {
        // From
        fromFieldComboBox.getItems().setAll(FieldHelper.getSetFieldsOnly(selectedEntries, viewModel.getAllFields()));
        fromFieldComboBox.setConverter(FIELD_STRING_CONVERTER);
        fromFieldComboBox.valueProperty().bindBidirectional(viewModel.fromFieldProperty());
        EasyBind.listen(fromFieldComboBox.getEditor().textProperty(), _ -> fromFieldComboBox.commitValue());
        if (!fromFieldComboBox.getItems().isEmpty()) {
            fromFieldComboBox.getSelectionModel().selectFirst();
        }

        // To
        toFieldComboBox.getItems().setAll(viewModel.getAllFields());
        toFieldComboBox.setConverter(FIELD_STRING_CONVERTER);
        toFieldComboBox.valueProperty().bindBidirectional(viewModel.toFieldProperty());
        EasyBind.listen(toFieldComboBox.getEditor().textProperty(), _ -> toFieldComboBox.commitValue());
    }

    @Override
    public Pane getContent() {
        return this;
    }

    @Override
    public String getTabName() {
        return Localization.lang("Copy or move content");
    }

    void copyContent() {
        viewModel.copyValue();
    }

    void moveContent() {
        viewModel.moveValue();
    }

    void swapContent() {
        viewModel.swapValues();
    }
}
