package org.jabref.gui.cleanup;

import java.util.List;

import org.jabref.gui.StateManager;
import org.jabref.gui.commonfxcontrols.FieldFormatterCleanupsPanel;
import org.jabref.injection.Injector;
import org.jabref.logic.cleanup.CleanupPreferences;
import org.jabref.logic.cleanup.FieldFormatterCleanup;
import org.jabref.logic.cleanup.FieldFormatterCleanupActions;
import org.jabref.logic.formatter.casechanger.LowerCaseFormatter;
import org.jabref.model.entry.field.StandardField;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/// Exercises the first FXML/2-compiled view (scene graph built by the generated
/// `CleanupSingleFieldPanelBase#initializeComponent()` instead of `FXMLLoader`).
class CleanupSingleFieldPanelTest extends ApplicationTest {

    private final FieldFormatterCleanup cleanup = new FieldFormatterCleanup(StandardField.TITLE, new LowerCaseFormatter());

    @BeforeEach
    void setUp() {
        Injector.setModelOrService(StateManager.class, mock(StateManager.class));
    }

    @Test
    void sceneGraphAndBindingsAreInitialized() {
        CleanupPreferences cleanupPreferences = mock(CleanupPreferences.class);
        when(cleanupPreferences.getFieldFormatterCleanups())
                .thenReturn(new FieldFormatterCleanupActions(true, List.of(cleanup)));

        CleanupSingleFieldPanel panel = new CleanupSingleFieldPanel(cleanupPreferences, mock(CleanupDialogViewModel.class));

        assertEquals(1, panel.getChildren().size());
        assertTrue(panel.getChildren().getFirst() instanceof FieldFormatterCleanupsPanel);
        assertEquals(List.of(cleanup), panel.getSelectedTab().formatters().orElseThrow().getConfiguredActions());
    }
}
