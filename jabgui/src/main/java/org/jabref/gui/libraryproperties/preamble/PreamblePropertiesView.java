package org.jabref.gui.libraryproperties.preamble;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;

import org.jabref.gui.StateManager;
import org.jabref.gui.libraryproperties.AbstractPropertiesTabView;
import org.jabref.gui.util.ViewLoader;
import org.jabref.logic.l10n.Localization;
import org.jabref.model.database.BibDatabaseContext;

import jakarta.inject.Inject;

public class PreamblePropertiesView extends AbstractPropertiesTabView<PreamblePropertiesViewModel> {
    @FXML private TextArea preamble;

    @Inject private StateManager stateManager;

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
        this.viewModel = new PreamblePropertiesViewModel(databaseContext, stateManager.getUndoManager(databaseContext));

        preamble.textProperty().bindBidirectional(viewModel.preambleProperty());
    }
}
