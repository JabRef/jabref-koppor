package org.jabref.gui.libraryproperties.preamble;

import javax.swing.undo.UndoManager;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;

import org.jabref.gui.libraryproperties.AbstractPropertiesTabView;
import org.jabref.logic.l10n.Localization;
import org.jabref.model.database.BibDatabaseContext;

import org.jabref.gui.util.ViewLoader;
import jakarta.inject.Inject;

public class PreamblePropertiesView extends AbstractPropertiesTabView<PreamblePropertiesViewModel> {
    @FXML private TextArea preamble;

    @Inject private UndoManager undoManager;

    public PreamblePropertiesView(BibDatabaseContext databaseContext) {
        this.databaseContext = databaseContext;

        ViewLoader.view(this)
                  .root(this)
                  .load();
    }

    @Override
    public String getTabName() {
        return Localization.lang("Preamble");
    }

    public void initialize() {
        this.viewModel = new PreamblePropertiesViewModel(databaseContext, undoManager);

        preamble.textProperty().bindBidirectional(viewModel.preambleProperty());
    }
}
