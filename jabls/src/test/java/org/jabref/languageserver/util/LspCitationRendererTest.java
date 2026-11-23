package org.jabref.languageserver.util;

import java.util.List;

import org.jabref.logic.citationstyle.CSLStyleLoader;
import org.jabref.logic.preferences.CliPreferences;
import org.jabref.logic.preferences.JabRefCliPreferences;
import org.jabref.logic.preview.TextBasedPreviewLayout;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.BibEntryTypesManager;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.types.StandardEntryType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// [utest->feat~fetchers.language-server-hover-citation~1]
class LspCitationRendererTest {

    @Test
    void customizedStyleFallsBackToFirstCslOrBstOfCycle() {
        JabRefCliPreferences preferences = mock(JabRefCliPreferences.class);
        when(preferences.getStringList("cyclePreview")).thenReturn(List.of(TextBasedPreviewLayout.NAME, "apa.csl", "abbrv.bst"));
        when(preferences.getInt("cyclePreviewPos", 0)).thenReturn(0);

        assertEquals(List.of(TextBasedPreviewLayout.NAME, TextBasedPreviewLayout.NAME, "apa.csl", "abbrv.bst", CSLStyleLoader.DEFAULT_STYLE),
                new LspCitationRenderer(preferences, new BibEntryTypesManager()).styleCandidates());
    }

    @Test
    void selectedStyleComesFirst() {
        JabRefCliPreferences preferences = mock(JabRefCliPreferences.class);
        when(preferences.getStringList("cyclePreview")).thenReturn(List.of(TextBasedPreviewLayout.NAME, "apa.csl", "abbrv.bst"));
        when(preferences.getInt("cyclePreviewPos", 0)).thenReturn(2);

        assertEquals("abbrv.bst", new LspCitationRenderer(preferences, new BibEntryTypesManager()).styleCandidates().getFirst());
    }

    @Test
    void rendersWithDefaultStyleWithoutStoredPreferences() {
        BibEntry entry = new BibEntry(StandardEntryType.Article)
                .withField(StandardField.AUTHOR, "Cooper, Karen A.")
                .withField(StandardField.TITLE, "Cocoa and health: a decade of research")
                .withField(StandardField.JOURNAL, "British Journal of Nutrition")
                .withField(StandardField.YEAR, "2007");

        String citation = new LspCitationRenderer(mock(CliPreferences.class), new BibEntryTypesManager()).render(entry);

        assertEquals("\\[1\\] K\\. A\\. Cooper\\, “Cocoa and health\\: a decade of research\\,” *British Journal of Nutrition*\\, 2007\\.", citation);
    }

    @Test
    void htmlToMarkdownKeepsEmphasis() {
        assertEquals("A. Author, *Title*, *Journal*, **2007**.", LspCitationRenderer.htmlToMarkdown("A. Author, <i>Title</i>, <em>Journal</em>, <b>2007</b>."));
    }
}
