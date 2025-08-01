package org.jabref.gui.duplicationFinder;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.BorderPane;

import org.jabref.gui.DialogService;
import org.jabref.gui.StateManager;
import org.jabref.gui.actions.ActionFactory;
import org.jabref.gui.actions.StandardActions;
import org.jabref.gui.duplicationFinder.DuplicateResolverDialog.DuplicateResolverResult;
import org.jabref.gui.help.HelpAction;
import org.jabref.gui.mergeentries.threewaymerge.ThreeWayMergeView;
import org.jabref.gui.preferences.GuiPreferences;
import org.jabref.gui.util.BaseDialog;
import org.jabref.gui.util.DialogWindowState;
import org.jabref.logic.help.HelpFile;
import org.jabref.logic.l10n.Localization;
import org.jabref.model.entry.BibEntry;

public class DuplicateResolverDialog extends BaseDialog<DuplicateResolverResult> {

    private final StateManager stateManager;

    public enum DuplicateResolverType {
        DUPLICATE_SEARCH,
        IMPORT_CHECK,
        DUPLICATE_SEARCH_WITH_EXACT
    }

    public enum DuplicateResolverResult {
        KEEP_BOTH(Localization.lang("Keep both")),
        KEEP_LEFT(Localization.lang("Keep existing entry")),
        KEEP_RIGHT(Localization.lang("Keep from import")),
        AUTOREMOVE_EXACT(Localization.lang("Automatically remove exact duplicates")),
        KEEP_MERGE(Localization.lang("Keep merged")),
        BREAK(Localization.lang("Ask every time"));

        final String defaultTranslationForImport;

        DuplicateResolverResult(String defaultTranslationForImport) {
            this.defaultTranslationForImport = defaultTranslationForImport;
        }

        public String getDefaultTranslationForImport() {
            return defaultTranslationForImport;
        }

        public static DuplicateResolverResult parse(String name) {
            try {
                return DuplicateResolverResult.valueOf(name);
            } catch (IllegalArgumentException e) {
                return BREAK; // default
            }
        }
    }

    private ThreeWayMergeView threeWayMerge;
    private final DialogService dialogService;
    private final ActionFactory actionFactory;
    private final GuiPreferences preferences;

    public DuplicateResolverDialog(BibEntry one,
                                   BibEntry two,
                                   DuplicateResolverType type,
                                   StateManager stateManager,
                                   DialogService dialogService,
                                   GuiPreferences preferences) {
        this.setTitle(Localization.lang("Possible duplicate entries"));
        this.stateManager = stateManager;
        this.dialogService = dialogService;
        this.preferences = preferences;
        this.actionFactory = new ActionFactory();
        init(one, two, type);
    }

    private void init(BibEntry one, BibEntry two, DuplicateResolverType type) {
        ButtonType cancel = ButtonType.CANCEL;
        ButtonType merge = new ButtonType(Localization.lang("Keep merged"), ButtonData.OK_DONE);

        ButtonType both;
        ButtonType second;
        ButtonType first;
        ButtonType removeExact = new ButtonType(Localization.lang("Automatically remove exact duplicates"), ButtonData.LEFT);
        boolean removeExactVisible = false;

        switch (type) {
            case DUPLICATE_SEARCH -> {
                first = new ButtonType(Localization.lang("Keep left"), ButtonData.LEFT);
                second = new ButtonType(Localization.lang("Keep right"), ButtonData.LEFT);
                both = new ButtonType(Localization.lang("Keep both"), ButtonData.LEFT);
                threeWayMerge = new ThreeWayMergeView(one, two, preferences);
            }
            case DUPLICATE_SEARCH_WITH_EXACT -> {
                first = new ButtonType(Localization.lang("Keep left"), ButtonData.LEFT);
                second = new ButtonType(Localization.lang("Keep right"), ButtonData.LEFT);
                both = new ButtonType(Localization.lang("Keep both"), ButtonData.LEFT);
                removeExactVisible = true;
                threeWayMerge = new ThreeWayMergeView(one, two, preferences);
            }
            case IMPORT_CHECK -> {
                first = new ButtonType(Localization.lang("Keep existing entry"), ButtonData.LEFT);
                second = new ButtonType(Localization.lang("Keep from import"), ButtonData.LEFT);
                both = new ButtonType(Localization.lang("Keep both"), ButtonData.LEFT);
                threeWayMerge = new ThreeWayMergeView(one, two, Localization.lang("Existing entry"),
                        Localization.lang("From import"), preferences);
            }
            default -> throw new IllegalStateException("Switch expression should be exhaustive");
        }

        this.getDialogPane().getButtonTypes().addAll(first, second, both, merge, cancel);
        this.getDialogPane().setFocusTraversable(false);

        if (removeExactVisible) {
            this.getDialogPane().getButtonTypes().add(removeExact);

            // This will prevent all dialog buttons from having the same size
            // Read more: https://stackoverflow.com/questions/45866249/javafx-8-alert-different-button-sizes
            getDialogPane().getButtonTypes().stream()
                           .map(getDialogPane()::lookupButton)
                           .forEach(btn -> ButtonBar.setButtonUniformSize(btn, false));
        }

        // Retrieves the previous window state and sets the new dialog window size and position to match it
        DialogWindowState state = stateManager.getDialogWindowState(getClass().getSimpleName());
        if (state != null) {
            this.getDialogPane().setPrefSize(state.getWidth(), state.getHeight());
            this.setX(state.getX());
            this.setY(state.getY());
        }

        BorderPane borderPane = new BorderPane(threeWayMerge);

        this.setResultConverter(button -> {
            // Updates the window state on button press
            stateManager.setDialogWindowState(getClass().getSimpleName(), new DialogWindowState(this.getX(), this.getY(), this.getDialogPane().getHeight(), this.getDialogPane().getWidth()));
            threeWayMerge.saveConfiguration();

            if (button.equals(first)) {
                return DuplicateResolverResult.KEEP_LEFT;
            } else if (button.equals(second)) {
                return DuplicateResolverResult.KEEP_RIGHT;
            } else if (button.equals(both)) {
                return DuplicateResolverResult.KEEP_BOTH;
            } else if (button.equals(merge)) {
                return DuplicateResolverResult.KEEP_MERGE;
            } else if (button.equals(removeExact)) {
                return DuplicateResolverResult.AUTOREMOVE_EXACT;
            } else if (button.equals(cancel)) {
                return DuplicateResolverResult.KEEP_LEFT;
            }
            return null;
        });

        HelpAction helpCommand = new HelpAction(HelpFile.FIND_DUPLICATES, dialogService, preferences.getExternalApplicationsPreferences());
        Button helpButton = actionFactory.createIconButton(StandardActions.HELP, helpCommand);
        borderPane.setRight(helpButton);

        getDialogPane().setContent(borderPane);
    }

    public BibEntry getMergedEntry() {
        return threeWayMerge.getMergedEntry();
    }

    public BibEntry getNewLeftEntry() {
        return threeWayMerge.getLeftEntry();
    }

    public BibEntry getNewRightEntry() {
        return threeWayMerge.getRightEntry();
    }
}
