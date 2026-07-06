package org.jabref.gui.cleanup;

import org.jabref.logic.cleanup.CleanupPreferences;
import org.jabref.logic.cleanup.CleanupTabSelection;
import org.jabref.logic.cleanup.FieldFormatterCleanupActions;

import org.jspecify.annotations.NonNull;

public class CleanupSingleFieldPanel extends CleanupSingleFieldPanelBase implements CleanupPanel {

    private final CleanupSingleFieldViewModel viewModel;

    public CleanupSingleFieldPanel(@NonNull CleanupPreferences cleanupPreferences,
                                   @NonNull CleanupDialogViewModel dialogViewModel) {

        this.viewModel = new CleanupSingleFieldViewModel(cleanupPreferences.getFieldFormatterCleanups());

        initializeComponent();

        bindProperties();
    }

    private void bindProperties() {
        formatterCleanupsPanel.setShowCleanupEnabledButton(false);
        formatterCleanupsPanel.cleanupsProperty().bindBidirectional(viewModel.cleanups);
    }

    public CleanupTabSelection getSelectedTab() {
        FieldFormatterCleanupActions selectedFormatters = viewModel.getSelectedFormatters();
        return CleanupTabSelection.ofFormatters(selectedFormatters);
    }
}
