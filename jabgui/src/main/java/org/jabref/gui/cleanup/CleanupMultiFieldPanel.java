package org.jabref.gui.cleanup;

import java.util.EnumSet;

import org.jabref.gui.l10n.LocalizedView;
import org.jabref.logic.cleanup.CleanupPreferences;
import org.jabref.logic.cleanup.CleanupTabSelection;

import org.jspecify.annotations.NonNull;

public class CleanupMultiFieldPanel extends CleanupMultiFieldPanelBase implements CleanupPanel, LocalizedView {

    private final CleanupMultiFieldViewModel viewModel;
    private final CleanupDialogViewModel dialogViewModel;

    public CleanupMultiFieldPanel(@NonNull CleanupPreferences cleanupPreferences,
                                  @NonNull CleanupDialogViewModel dialogViewModel) {

        this.dialogViewModel = dialogViewModel;
        this.viewModel = new CleanupMultiFieldViewModel(cleanupPreferences);

        initializeComponent();

        bindProperties();
    }

    private void bindProperties() {
        cleanupDoi.selectedProperty().bindBidirectional(viewModel.doiSelected);
        cleanupArXivDoi.selectedProperty().bindBidirectional(viewModel.arXivDoiSelected);
        cleanupEprint.selectedProperty().bindBidirectional(viewModel.eprintSelected);
        cleanupUrl.selectedProperty().bindBidirectional(viewModel.urlSelected);
        cleanupBibTeX.selectedProperty().bindBidirectional(viewModel.bibTexSelected);
        cleanupBibLaTeX.selectedProperty().bindBidirectional(viewModel.bibLaTexSelected);
        cleanupTimestampToCreationDate.selectedProperty().bindBidirectional(viewModel.timestampToCreationSelected);
        cleanupTimestampToModificationDate.selectedProperty().bindBidirectional(viewModel.timestampToModificationSelected);
    }

    @Override
    public CleanupTabSelection getSelectedTab() {
        EnumSet<CleanupPreferences.CleanupStep> selectedJobs = viewModel.getSelectedJobs();
        return CleanupTabSelection.ofJobs(CleanupMultiFieldViewModel.MULTI_FIELD_JOBS, selectedJobs);
    }
}
