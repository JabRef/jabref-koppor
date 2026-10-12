package org.jabref.gui.entryeditor;

import java.util.List;
import java.util.Set;

import org.jabref.gui.entryeditor.EntryEditorTabModel.BuiltIn;
import org.jabref.gui.entryeditor.EntryEditorTabModel.BuiltInTab;
import org.jabref.gui.entryeditor.EntryEditorTabModel.CustomizedFieldsTab;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.field.UnknownField;
import org.jabref.model.entry.field.UserSpecificCommentField;
import org.jabref.model.entry.types.StandardEntryType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryEditorTabModelTest {

    private final BibEntry entry = new BibEntry(StandardEntryType.Article)
            .withField(StandardField.AUTHOR, "Author")
            .withField(StandardField.COMMENT, "general comment")
            .withField(new UserSpecificCommentField("alice"), "alice's comment")
            .withField(new UserSpecificCommentField("bob"), "bob's comment");

    private static List<Field> resolve(BibEntry entry, String... patterns) {
        return List.copyOf(new CustomizedFieldsTab("Test", List.of(patterns)).resolveFields(entry));
    }

    @Test
    void plainFieldNamesAreAlwaysShownEvenWhenUnset() {
        assertEquals(List.of(StandardField.AUTHOR, StandardField.URL), resolve(entry, "author", "url"));
    }

    @Test
    void unknownPlainFieldNameResolvesToUnknownField() {
        assertEquals(List.of(new UnknownField("myfield")), resolve(entry, "myfield"));
    }

    @Test
    void regexPatternCapturesMatchingSetFields() {
        assertEquals(List.of(new UserSpecificCommentField("alice"), new UserSpecificCommentField("bob")),
                resolve(entry, "comment-.*"));
    }

    @Test
    void patternOrderIsKept() {
        assertEquals(
                List.of(StandardField.AUTHOR, new UserSpecificCommentField("alice"), new UserSpecificCommentField("bob"), StandardField.COMMENT),
                resolve(entry, "author", "comment-.*", "comment"));
    }

    @Test
    void invalidRegexResolvesToNothing() {
        assertEquals(List.of(StandardField.AUTHOR), resolve(entry, "author", "comment-["));
    }

    @Test
    void extractedFieldsOnCustomTabsUnitesExtractedPatternsAndIgnoresBuiltInTabs() {
        List<EntryEditorTabModel> tabModels = List.of(
                new BuiltInTab(BuiltIn.ALL_FIELDS, true),
                new CustomizedFieldsTab("One", List.of("author", "url"), Set.of("author", "url")),
                new CustomizedFieldsTab("Two", List.of("comment-.*"), Set.of("comment-.*")));
        assertEquals(
                Set.of(StandardField.AUTHOR, StandardField.URL, new UserSpecificCommentField("alice"), new UserSpecificCommentField("bob")),
                EntryEditorTabModel.extractedFieldsOnCustomTabs(tabModels, entry));
    }

    // [utest->req~entry-editor.custom-tabs.extract-field~1]
    @Test
    void nonExtractedKnownFieldsLeaveTheMainTabAlone() {
        List<EntryEditorTabModel> tabModels = List.of(new CustomizedFieldsTab("One", List.of("author", "title")),
                new CustomizedFieldsTab("Two", List.of("url", "comment"), Set.of("comment")));
        assertEquals(Set.of(StandardField.COMMENT), EntryEditorTabModel.extractedFieldsOnCustomTabs(tabModels, entry));
    }

    // [utest->req~entry-editor.custom-tabs.extract-field~1]
    @Test
    void regexAndUnknownFieldPatternsAreAlwaysExtracted() {
        List<EntryEditorTabModel> tabModels = List.of(new CustomizedFieldsTab("One", List.of("author", "comment-.*", "myfield")));
        // author is a known field and not marked for extraction; the regex captures and the unknown
        // field name are extracted regardless of the (empty) extracted set.
        assertEquals(
                Set.of(new UserSpecificCommentField("alice"), new UserSpecificCommentField("bob"), new UnknownField("myfield")),
                EntryEditorTabModel.extractedFieldsOnCustomTabs(tabModels, entry));
    }

    // [utest->req~entry-editor.custom-tabs.extract-field~1]
    @Test
    void extractChoiceOnlyAppliesToKnownPlainFieldNames() {
        assertTrue(CustomizedFieldsTab.appearsOnMainTab("author"));
        // Type-scoped fields (BibLaTeX software / APA) parse as unknown without an entry type
        // but are known fields, so the extraction choice stays enabled for them.
        assertTrue(CustomizedFieldsTab.appearsOnMainTab("license"));
        assertTrue(CustomizedFieldsTab.appearsOnMainTab("repository"));
        assertTrue(CustomizedFieldsTab.appearsOnMainTab("article"));
        assertFalse(CustomizedFieldsTab.appearsOnMainTab("comment-.*"));
        assertFalse(CustomizedFieldsTab.appearsOnMainTab("myfield"));
    }

    @Test
    void extractedRegexCapturesFieldOnlyOnceItHasAValue() {
        List<EntryEditorTabModel> tabModels = List.of(new CustomizedFieldsTab("Notes", List.of("note.*"), Set.of("note.*")));
        BibEntry withoutNote = new BibEntry(StandardEntryType.Article);
        assertEquals(Set.of(), EntryEditorTabModel.extractedFieldsOnCustomTabs(tabModels, withoutNote));
        // First typed character sets the field; from then on it belongs to the custom tab
        // (and the Main tab must drop it, even when it was chip-added there).
        BibEntry withNote = new BibEntry(StandardEntryType.Article).withField(StandardField.NOTE, "x");
        assertEquals(Set.of(StandardField.NOTE), EntryEditorTabModel.extractedFieldsOnCustomTabs(tabModels, withNote));
    }
}
