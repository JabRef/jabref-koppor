package org.jabref.gui.cleanup;

import java.util.EnumSet;

import org.jabref.logic.cleanup.CleanupPreferences;
import org.jabref.logic.cleanup.CleanupTabSelection;
import org.jabref.logic.l10n.Localization;

import org.jspecify.annotations.NonNull;

public class CleanupJournalRelatedPanel extends CleanupJournalRelatedPanelBase implements CleanupPanel {

    private final CleanupJournalRelatedViewModel viewModel;
    private final CleanupDialogViewModel dialogViewModel;

    public CleanupJournalRelatedPanel(@NonNull CleanupPreferences cleanupPreferences,
                                      @NonNull CleanupDialogViewModel dialogViewModel) {
        this.dialogViewModel = dialogViewModel;
        this.viewModel = new CleanupJournalRelatedViewModel(cleanupPreferences);

        initializeComponent();

        initialize();
        bindProperties();
    }

    private void initialize() {
        cleanupJournalAbbreviationsLabel.setText(Localization.lang("Manage journal abbreviations"));

        abbreviateDefault.setText(Localization.lang("Abbreviate (default)"));
        abbreviateDefault.setUserData(CleanupPreferences.CleanupStep.ABBREVIATE_DEFAULT);

        abbreviateDottles.setText(Localization.lang("Abbreviate (dotless)"));
        abbreviateDottles.setUserData(CleanupPreferences.CleanupStep.ABBREVIATE_DOTLESS);

        abbreviateShortestUnique.setText(Localization.lang("Abbreviate (shortest unique)"));
        abbreviateShortestUnique.setUserData(CleanupPreferences.CleanupStep.ABBREVIATE_SHORTEST_UNIQUE);

        abbreviateLTWA.setText(Localization.lang("Abbreviate (LTWA)"));
        abbreviateLTWA.setUserData(CleanupPreferences.CleanupStep.ABBREVIATE_LTWA);

        unabbreviate.setText(Localization.lang("Unabbreviate"));
        unabbreviate.setUserData(CleanupPreferences.CleanupStep.UNABBREVIATE);
    }

    private void bindProperties() {
        journalAbbreviationsToggleGroup.selectToggle(
                journalAbbreviationsToggleGroup.getToggles().stream()
                                               .filter(toggle -> toggle.getUserData().equals(viewModel.selectedJournalCleanupOption.get()))
                                               .findFirst().orElse(null));
        journalAbbreviationsToggleGroup.selectedToggleProperty().addListener((_, _, newToggle) -> {
            if (newToggle != null) {
                CleanupPreferences.CleanupStep step = (CleanupPreferences.CleanupStep) newToggle.getUserData();
                viewModel.selectedJournalCleanupOption.set(step);
            }
        });
    }

    @Override
    public CleanupTabSelection getSelectedTab() {
        EnumSet<CleanupPreferences.CleanupStep> selectedMethods = EnumSet.noneOf(CleanupPreferences.CleanupStep.class);

        CleanupPreferences.CleanupStep selected = viewModel.selectedJournalCleanupOption.get();
        selectedMethods.add(selected);

        return CleanupTabSelection.ofJobs(CleanupJournalRelatedViewModel.CLEANUP_JOURNAL_METHODS, selectedMethods);
    }
}
