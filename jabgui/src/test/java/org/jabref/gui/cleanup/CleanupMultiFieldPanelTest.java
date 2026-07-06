package org.jabref.gui.cleanup;

import org.jabref.logic.cleanup.CleanupPreferences;
import org.jabref.logic.l10n.Localization;

import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/// Exercises the first localized FXML/2-compiled view: `%key` resources are resolved through
/// [org.jabref.gui.l10n.JabRefResourceContext], keeping JabRef's key=value convention.
class CleanupMultiFieldPanelTest extends ApplicationTest {

    @Test
    void resourceKeysAreResolvedThroughJabRefLocalization() {
        CleanupMultiFieldPanel panel = new CleanupMultiFieldPanel(mock(CleanupPreferences.class), mock(CleanupDialogViewModel.class));

        // plain %key form: spaces and apostrophes in the key
        assertEquals(Localization.lang("Move DOIs from 'note' field and 'URL' field to 'DOI' field and remove http prefix"),
                panel.cleanupDoi.getText());
        // quoted %'key' form: comma in the key, escaped apostrophes
        assertEquals(Localization.lang("For arXiv entries, keep the DOI and remove the redundant 'eprint' fields"),
                panel.cleanupArXivDoi.getText());
        assertEquals(Localization.lang("Convert to biblatex format (e.g., store publication date in date field)"),
                panel.cleanupBibLaTeX.getText());
    }
}
