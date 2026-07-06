package org.jabref.gui.edit.automaticfiededitor;

import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

import javafx.scene.control.ButtonBar;
import javafx.scene.control.Tab;

import org.jabref.gui.DialogService;
import org.jabref.gui.StateManager;
import org.jabref.gui.util.BaseDialog;
import org.jabref.logic.l10n.Localization;
import org.jabref.model.database.BibDatabase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutomaticFieldEditorDialog extends BaseDialog<String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutomaticFieldEditorDialog.class);

    private final AutomaticFieldEditorViewModel viewModel;

    public AutomaticFieldEditorDialog(StateManager stateManager,
                                      DialogService dialogService,
                                      UndoManager undoManager) {
        BibDatabase database = stateManager.getActiveDatabase().orElseThrow().getDatabase();
        this.viewModel = new AutomaticFieldEditorViewModel(database, undoManager, dialogService, stateManager);

        this.setTitle(Localization.lang("Automatic field editor"));

        AutomaticFieldEditorDialogPane pane = new AutomaticFieldEditorDialogPane();
        setDialogPane(pane);

        for (AutomaticFieldEditorTab tabModel : viewModel.getFieldEditorTabs()) {
            pane.tabPane.getTabs().add(new Tab(tabModel.getTabName(), tabModel.getContent()));
        }

        setResultConverter(buttonType -> {
            if (buttonType != null && buttonType.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                saveChanges();
            } else {
                cancelChanges();
            }
            return "";
        });
    }

    private void saveChanges() {
        viewModel.saveChanges();
    }

    private void cancelChanges() {
        try {
            viewModel.cancelChanges();
        } catch (CannotUndoException e) {
            LOGGER.info("Could not undo", e);
        }
    }
}
