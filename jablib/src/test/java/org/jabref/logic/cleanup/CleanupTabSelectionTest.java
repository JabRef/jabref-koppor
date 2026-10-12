package org.jabref.logic.cleanup;

import java.util.EnumSet;

import org.jabref.logic.cleanup.CleanupPreferences.CleanupStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CleanupTabSelectionTest {

    private CleanupPreferences cleanupPreferences;

    @BeforeEach
    void setUp() {
        cleanupPreferences = new CleanupPreferences(EnumSet.of(CleanupStep.CLEAN_UP_DOI));
    }

    @Test
    void updatePreferencesReplacesStepsInSameCategory() {
        EnumSet<CleanupStep> allJobs = EnumSet.of(CleanupStep.CLEAN_UP_DOI, CleanupStep.CLEANUP_EPRINT);
        EnumSet<CleanupStep> selectedJobs = EnumSet.of(CleanupStep.CLEANUP_EPRINT);

        CleanupTabSelection selection = CleanupTabSelection.ofJobs(allJobs, selectedJobs);
        CleanupPreferences result = selection.updatePreferences(cleanupPreferences);

        EnumSet<CleanupStep> expected = EnumSet.of(CleanupStep.CLEANUP_EPRINT);
        assertEquals(expected, result.getActiveJobs());
    }

    @Test
    void updatePreferencesAddsStepInDifferentCategory() {
        EnumSet<CleanupStep> allJobs = EnumSet.of(CleanupStep.MOVE_PDF, CleanupStep.MAKE_PATHS_RELATIVE);
        EnumSet<CleanupStep> selectedJobs = EnumSet.of(CleanupStep.MOVE_PDF);

        CleanupTabSelection selection = CleanupTabSelection.ofJobs(allJobs, selectedJobs);
        CleanupPreferences result = selection.updatePreferences(cleanupPreferences);

        EnumSet<CleanupStep> expected = EnumSet.of(CleanupStep.CLEAN_UP_DOI, CleanupStep.MOVE_PDF);
        assertEquals(expected, result.getActiveJobs());
    }

    @Test
    void updatePreferencesAppliesFormatterCleanups() {
        FieldFormatterCleanupActions formatter = new FieldFormatterCleanupActions(true, FieldFormatterCleanupMapper.parseActions("title[identity]"));

        CleanupTabSelection selection = CleanupTabSelection.ofFormatters(formatter);
        CleanupPreferences result = selection.updatePreferences(cleanupPreferences);

        assertEquals(formatter, result.getFieldFormatterCleanups());
    }
}
