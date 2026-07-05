package org.jabref.gui.entryeditor;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jabref.model.database.BibDatabaseMode;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.BibEntryType;
import org.jabref.model.entry.BibEntryTypeBuilder;
import org.jabref.model.entry.BibEntryTypesManager;
import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.types.StandardEntryType;
import org.jabref.model.entry.types.UnknownEntryType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitationSegmentsTest {

    private final BibEntryTypesManager entryTypesManager = new BibEntryTypesManager();

    private CitationSegments segmentsOf(BibEntry entry, BibDatabaseMode mode) {
        return CitationSegments.of(entry, entryTypesManager.enrich(entry.getType(), mode));
    }

    /// Flattens the token list to a plain string; placeholders render as {{name}}.
    private static String render(CitationSegments segments) {
        return segments.tokens().stream()
                       .map(token -> switch (token) {
                           case CitationSegments.TextToken(String text) ->
                                   text;
                           case CitationSegments.FieldToken(Field _, String displayText, CitationSegments.SegmentStyle _) ->
                                   displayText;
                           case CitationSegments.PlaceholderToken(Field field) ->
                                   "{{" + field.getName() + "}}";
                       })
                       .collect(Collectors.joining());
    }

    private static Optional<CitationSegments.FieldToken> fieldTokenOf(CitationSegments segments, Field field) {
        return segments.tokens().stream()
                       .filter(token -> token instanceof CitationSegments.FieldToken(Field tokenField, String _, CitationSegments.SegmentStyle _)
                               && tokenField == field)
                       .map(CitationSegments.FieldToken.class::cast)
                       .findFirst();
    }

    @Test
    void completeArticleRendersCitationLike() {
        BibEntry entry = new BibEntry(StandardEntryType.Article)
                .withField(StandardField.AUTHOR, "Doe, John and Smith, Jane")
                .withField(StandardField.TITLE, "Great Results")
                .withField(StandardField.JOURNAL, "Journal of Tests")
                .withField(StandardField.VOLUME, "12")
                .withField(StandardField.NUMBER, "3")
                .withField(StandardField.PAGES, "45--67")
                .withField(StandardField.YEAR, "2020");

        assertEquals("John Doe and Jane Smith. Great Results. Journal of Tests, 12(3):45–67, 2020.",
                render(segmentsOf(entry, BibDatabaseMode.BIBTEX)));
    }

    @Test
    void emptyArticleRendersRequiredPlaceholders() {
        BibEntry entry = new BibEntry(StandardEntryType.Article);

        assertEquals("{{author}}. {{title}}. {{journal}}, {{year}}.",
                render(segmentsOf(entry, BibDatabaseMode.BIBTEX)));
    }

    @Test
    void emptyBiblatexArticleUsesJournaltitleAndDate() {
        BibEntry entry = new BibEntry(StandardEntryType.Article);

        assertEquals("{{author}}. {{title}}. {{journaltitle}}, {{date}}.",
                render(segmentsOf(entry, BibDatabaseMode.BIBLATEX)));
    }

    @Test
    void coveredFieldsListRenderedAndPlaceholderFields() {
        BibEntry entry = new BibEntry(StandardEntryType.Article)
                .withField(StandardField.AUTHOR, "Doe, John")
                .withField(StandardField.YEAR, "2020");

        assertEquals(List.of(StandardField.AUTHOR, StandardField.TITLE, StandardField.JOURNAL, StandardField.YEAR),
                List.copyOf(segmentsOf(entry, BibDatabaseMode.BIBTEX).coveredFields()));
    }

    @Test
    void editorOnlyBookIsMarkedAsEdited() {
        BibEntry entry = new BibEntry(StandardEntryType.Book)
                .withField(StandardField.EDITOR, "Doe, John")
                .withField(StandardField.TITLE, "Collected Works")
                .withField(StandardField.PUBLISHER, "ACME")
                .withField(StandardField.YEAR, "2020");

        assertEquals("John Doe (Eds.). Collected Works. ACME, 2020.",
                render(segmentsOf(entry, BibDatabaseMode.BIBTEX)));
    }

    @Test
    void bookTitleIsItalicArticleTitleIsPlain() {
        BibEntry book = new BibEntry(StandardEntryType.Book)
                .withField(StandardField.TITLE, "Collected Works");
        BibEntry article = new BibEntry(StandardEntryType.Article)
                .withField(StandardField.TITLE, "Great Results")
                .withField(StandardField.JOURNAL, "Journal of Tests");

        CitationSegments bookSegments = segmentsOf(book, BibDatabaseMode.BIBTEX);
        CitationSegments articleSegments = segmentsOf(article, BibDatabaseMode.BIBTEX);

        assertEquals(CitationSegments.SegmentStyle.ITALIC,
                fieldTokenOf(bookSegments, StandardField.TITLE).orElseThrow().style());
        assertEquals(CitationSegments.SegmentStyle.PLAIN,
                fieldTokenOf(articleSegments, StandardField.TITLE).orElseThrow().style());
        assertEquals(CitationSegments.SegmentStyle.ITALIC,
                fieldTokenOf(articleSegments, StandardField.JOURNAL).orElseThrow().style());
    }

    @Test
    void volumeWithoutNumberOmitsParentheses() {
        BibEntry entry = new BibEntry(StandardEntryType.Article)
                .withField(StandardField.AUTHOR, "Doe, John")
                .withField(StandardField.TITLE, "Great Results")
                .withField(StandardField.JOURNAL, "Journal of Tests")
                .withField(StandardField.VOLUME, "12")
                .withField(StandardField.PAGES, "45")
                .withField(StandardField.YEAR, "2020");

        assertEquals("John Doe. Great Results. Journal of Tests, 12:45, 2020.",
                render(segmentsOf(entry, BibDatabaseMode.BIBTEX)));
    }

    @Test
    void pagesWithoutVolumeAreLabeled() {
        BibEntry entry = new BibEntry(StandardEntryType.Article)
                .withField(StandardField.AUTHOR, "Doe, John")
                .withField(StandardField.TITLE, "Great Results")
                .withField(StandardField.JOURNAL, "Journal of Tests")
                .withField(StandardField.PAGES, "45--67")
                .withField(StandardField.YEAR, "2020");

        assertEquals("John Doe. Great Results. Journal of Tests, pages 45–67, 2020.",
                render(segmentsOf(entry, BibDatabaseMode.BIBTEX)));
    }

    @Test
    void numberWithoutVolumeIsLabeled() {
        BibEntry entry = new BibEntry(StandardEntryType.Article)
                .withField(StandardField.AUTHOR, "Doe, John")
                .withField(StandardField.TITLE, "Great Results")
                .withField(StandardField.JOURNAL, "Journal of Tests")
                .withField(StandardField.NUMBER, "3")
                .withField(StandardField.YEAR, "2020");

        assertEquals("John Doe. Great Results. Journal of Tests, number 3, 2020.",
                render(segmentsOf(entry, BibDatabaseMode.BIBTEX)));
    }

    @Test
    void inProceedingsRendersBooktitleWithInPrefix() {
        BibEntry entry = new BibEntry(StandardEntryType.InProceedings)
                .withField(StandardField.AUTHOR, "Doe, John")
                .withField(StandardField.TITLE, "Great Results")
                .withField(StandardField.BOOKTITLE, "Proceedings of Testing")
                .withField(StandardField.PAGES, "1--2")
                .withField(StandardField.YEAR, "2020");

        assertEquals("John Doe. Great Results. In Proceedings of Testing, pages 1–2, 2020.",
                render(segmentsOf(entry, BibDatabaseMode.BIBTEX)));
    }

    @Test
    void missingBooktitlePlaceholderKeepsInPrefix() {
        BibEntry entry = new BibEntry(StandardEntryType.InProceedings)
                .withField(StandardField.AUTHOR, "Doe, John")
                .withField(StandardField.TITLE, "Great Results")
                .withField(StandardField.YEAR, "2020");

        assertEquals("John Doe. Great Results. In {{booktitle}}, 2020.",
                render(segmentsOf(entry, BibDatabaseMode.BIBTEX)));
    }

    @Test
    void phdThesisRendersSchool() {
        BibEntry entry = new BibEntry(StandardEntryType.PhdThesis)
                .withField(StandardField.AUTHOR, "Doe, John")
                .withField(StandardField.TITLE, "Deep Insights")
                .withField(StandardField.SCHOOL, "MIT")
                .withField(StandardField.YEAR, "2020");

        assertEquals("John Doe. Deep Insights. MIT, 2020.",
                render(segmentsOf(entry, BibDatabaseMode.BIBTEX)));
    }

    @Test
    void emptyBiblatexThesisRendersTypeInstitutionAndDatePlaceholders() {
        BibEntry entry = new BibEntry(StandardEntryType.Thesis);

        assertEquals("{{author}}. {{title}}. {{type}}, {{institution}}, {{date}}.",
                render(segmentsOf(entry, BibDatabaseMode.BIBLATEX)));
    }

    @Test
    void dateAliasIsUsedWhenYearUnset() {
        BibEntry entry = new BibEntry(StandardEntryType.Article)
                .withField(StandardField.AUTHOR, "Doe, John")
                .withField(StandardField.TITLE, "Great Results")
                .withField(StandardField.JOURNALTITLE, "Journal of Tests")
                .withField(StandardField.DATE, "2020-07");

        assertEquals("John Doe. Great Results. Journal of Tests, 2020-07.",
                render(segmentsOf(entry, BibDatabaseMode.BIBLATEX)));
    }

    @Test
    void latexIsConvertedForDisplay() {
        BibEntry entry = new BibEntry(StandardEntryType.Book)
                .withField(StandardField.TITLE, "Gr{\\\"o}bner Bases");

        assertEquals("Gröbner Bases",
                fieldTokenOf(segmentsOf(entry, BibDatabaseMode.BIBTEX), StandardField.TITLE)
                        .orElseThrow().displayText());
    }

    @Test
    void whitespaceIsCollapsedForDisplay() {
        BibEntry entry = new BibEntry(StandardEntryType.Book)
                .withField(StandardField.TITLE, "Collected\n   Works");

        assertEquals("Collected Works",
                fieldTokenOf(segmentsOf(entry, BibDatabaseMode.BIBTEX), StandardField.TITLE)
                        .orElseThrow().displayText());
    }

    @Test
    void longValuesAreTruncatedWithEllipsis() {
        BibEntry entry = new BibEntry(StandardEntryType.Book)
                .withField(StandardField.TITLE, "a".repeat(300));

        String displayText = fieldTokenOf(segmentsOf(entry, BibDatabaseMode.BIBTEX), StandardField.TITLE)
                .orElseThrow().displayText();

        assertEquals(CitationSegments.VALUE_DISPLAY_MAX_LENGTH, displayText.length());
        assertTrue(displayText.endsWith("…"));
    }

    @Test
    void requiredFieldOutsideVocabularyBecomesTrailingPlaceholder() {
        BibEntryType gadgetType = new BibEntryTypeBuilder()
                .withType(new UnknownEntryType("gadget"))
                .withRequiredFields(StandardField.SERIES)
                .build();
        BibEntry entry = new BibEntry(new UnknownEntryType("gadget"))
                .withField(StandardField.TITLE, "Great Results");

        CitationSegments segments = CitationSegments.of(entry, Optional.of(gadgetType));

        assertEquals("Great Results. {{series}}.", render(segments));
        assertEquals(List.of(StandardField.TITLE, StandardField.SERIES),
                List.copyOf(segments.coveredFields()));
    }

    @Test
    void emptyMiscRendersNothing() {
        BibEntry entry = new BibEntry(StandardEntryType.Misc);

        CitationSegments segments = segmentsOf(entry, BibDatabaseMode.BIBTEX);

        assertEquals(List.of(), segments.tokens());
        assertEquals(List.of(), List.copyOf(segments.coveredFields()));
    }

    @Test
    void unknownTypeWithoutDefinitionFallsBack() {
        BibEntry entry = new BibEntry(new UnknownEntryType("whatever"))
                .withField(StandardField.AUTHOR, "Doe, John")
                .withField(StandardField.TITLE, "Great Results")
                .withField(StandardField.HOWPUBLISHED, "Self-published")
                .withField(StandardField.YEAR, "2020");

        assertEquals("John Doe. Great Results. Self-published, 2020.",
                render(CitationSegments.of(entry, Optional.empty())));
    }
}
