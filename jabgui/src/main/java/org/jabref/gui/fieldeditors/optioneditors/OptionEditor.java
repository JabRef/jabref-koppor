package org.jabref.gui.fieldeditors.optioneditors;

import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;

import org.jabref.gui.fieldeditors.FieldEditorFX;
import org.jabref.gui.fieldeditors.contextmenu.EditorContextAction;
import org.jabref.gui.util.ViewModelListCellFactory;
import org.jabref.model.entry.BibEntry;

/// Field editor that provides various pre-defined options as a drop-down combobox.
public class OptionEditor<T> extends OptionEditorBase implements FieldEditorFX {

    private final OptionEditorViewModel<T> viewModel;

    // FXML/2 does not carry Java generics: the generated base declares `comboBox` as the raw
    // `ComboBox` type. Cast once so the generic-typed calls below type-check.
    private final ComboBox<T> comboBox;

    public OptionEditor(OptionEditorViewModel<T> viewModel) {
        this.viewModel = viewModel;

        initializeComponent();

        @SuppressWarnings("unchecked")
        ComboBox<T> typedComboBox = (ComboBox<T>) (ComboBox<?>) super.comboBox;
        this.comboBox = typedComboBox;

        comboBox.setConverter(viewModel.getStringConverter());
        comboBox.setCellFactory(new ViewModelListCellFactory<T>().withText(viewModel::convertToDisplayText));
        comboBox.getItems().setAll(viewModel.getItems());
        comboBox.getEditor().textProperty().bindBidirectional(viewModel.textProperty());

        comboBox.getEditor().setOnContextMenuRequested(event -> {
            ContextMenu contextMenu = new ContextMenu();
            contextMenu.getItems().setAll(EditorContextAction.getDefaultContextMenuItems(comboBox.getEditor()));
            contextMenu.show(comboBox, event.getScreenX(), event.getScreenY());
        });
    }

    public OptionEditorViewModel<T> getViewModel() {
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
