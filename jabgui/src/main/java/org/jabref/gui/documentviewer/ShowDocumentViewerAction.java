package org.jabref.gui.documentviewer;

import static org.jabref.gui.actions.ActionHelper.needsEntriesSelected;

import com.airhacks.afterburner.injection.Injector;
import org.jabref.gui.DialogService;
import org.jabref.gui.StateManager;
import org.jabref.gui.actions.ActionHelper;
import org.jabref.gui.actions.SimpleCommand;
import org.jabref.logic.preferences.CliPreferences;

public class ShowDocumentViewerAction extends SimpleCommand {

    private final DialogService dialogService =
        Injector.instantiateModelOrService(DialogService.class);
    private DocumentViewerView documentViewerView;

    public ShowDocumentViewerAction(
        StateManager stateManager,
        CliPreferences preferences
    ) {
        this.executable.bind(
            needsEntriesSelected(stateManager).and(
                ActionHelper.isFilePresentForSelectedEntry(
                    stateManager,
                    preferences
                )
            )
        );
    }

    @Override
    public void execute() {
        if (documentViewerView == null) {
            documentViewerView = new DocumentViewerView();
        }
        dialogService.showCustomDialog(documentViewerView);
    }
}
