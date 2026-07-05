package org.jabref.gui.entryeditor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.regex.Pattern;

import org.jabref.logic.util.strings.StringUtil;
import org.jabref.model.entry.AuthorList;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.BibEntryType;
import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.OrFields;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.types.EntryType;
import org.jabref.model.entry.types.StandardEntryType;
import org.jabref.model.strings.LatexToUnicodeAdapter;

import org.jspecify.annotations.NullMarked;

/// The semantic preview of an entry as an ordered token list (issue #12711, concept #2):
/// a citation-like rendering ("Author. Title. Journal, 12(3):45–67, 2020.") where every
/// field value is its own [FieldToken] (clickable → in-place edit in the [AllFieldsTab])
/// and every required-but-unset field is a [PlaceholderToken] (rendered as `{{Field}}`).
///
/// Plain Java (no JavaFX) so templates and punctuation logic are unit-testable.
///
/// [#coveredFields()] lists every field represented in the preview (set fields rendered
/// as text plus required fields rendered as placeholders). The tab subtracts these from
/// all other chip lists and editor rows — the preview takes precedence, a field never
/// appears twice ("no duplication" rule).
///
/// A required field that is not part of the template vocabulary shows up as a trailing
/// placeholder while unset; once set, it is no longer covered and moves to its regular
/// place below the preview (section or "more fields" row).
@NullMarked
public record CitationSegments(List<Token> tokens, SequencedSet<Field> coveredFields) {
    /// Rendered field values are capped at this many characters (long values such as
    /// abstracts are not part of the template vocabulary, but titles can get long too).
    static final int VALUE_DISPLAY_MAX_LENGTH = 250;

    /// Runs of whitespace (including line breaks) collapse to one space for display.
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /// Visual style of a [FieldToken]; standalone titles and venue names render italic.
    public enum SegmentStyle {
        PLAIN,
        ITALIC
    }

    public sealed interface Token permits TextToken, FieldToken, PlaceholderToken {
    }

    /// Punctuation or literal text between field segments; not clickable.
    public record TextToken(String text) implements Token {
    }

    /// A set field with its display text; clickable → in-place edit.
    public record FieldToken(Field field, String displayText, SegmentStyle style) implements Token {
    }

    /// A required-but-unset field; rendered as `{{Field}}`; clickable → in-place edit.
    public record PlaceholderToken(Field field) implements Token {
    }

    // [impl->req~entry-editor.main-tab.semantic-preview~1]
    public static CitationSegments of(BibEntry entry, Optional<BibEntryType> entryType) {
        SequencedSet<OrFields> requiredFields =
                entryType.map(BibEntryType::getRequiredFields).orElseGet(LinkedHashSet::new);
        Builder builder = new Builder(entry, requiredFields);

        EntryType type = entry.getType();
        if (type instanceof StandardEntryType standardType) {
            switch (standardType) {
                case Article,
                     SuppPeriodical ->
                        article(builder);
                case Book,
                     MvBook,
                     InBook,
                     BookInBook,
                     SuppBook,
                     Booklet,
                     Collection,
                     MvCollection,
                     Proceedings,
                     MvProceedings,
                     Reference,
                     MvReference ->
                        book(builder);
                case InProceedings,
                     Conference,
                     InCollection,
                     SuppCollection,
                     InReference ->
                        partOfCollection(builder);
                case PhdThesis,
                     MastersThesis,
                     Thesis,
                     TechReport,
                     Report ->
                        thesisOrReport(builder);
                default ->
                        fallback(builder);
            }
        } else {
            fallback(builder);
        }

        builder.trailingRequiredPlaceholders();
        builder.finish();
        return new CitationSegments(List.copyOf(builder.tokens), builder.covered);
    }

    // region templates

    /// Authors. Title. *Journal*, 12(3):45–67, 2020.
    private static void article(Builder b) {
        b.sentence();
        b.personSlot();
        b.sentence();
        b.slot("", SegmentStyle.PLAIN, StandardField.TITLE);
        b.sentence();
        b.slot("", SegmentStyle.ITALIC, StandardField.JOURNAL, StandardField.JOURNALTITLE);
        b.volumeNumberPages();
        b.yearSlot();
    }

    /// Authors. *Title*. Publisher, 2020.
    private static void book(Builder b) {
        b.sentence();
        b.personSlot();
        b.sentence();
        b.slot("", SegmentStyle.ITALIC, StandardField.TITLE);
        b.sentence();
        b.slot("", SegmentStyle.PLAIN, StandardField.PUBLISHER);
        b.yearSlot();
    }

    /// Authors. Title. In *Booktitle*, pages 45–67, Publisher, 2020.
    private static void partOfCollection(Builder b) {
        b.sentence();
        b.personSlot();
        b.sentence();
        b.slot("", SegmentStyle.PLAIN, StandardField.TITLE);
        b.sentence();
        b.slot("In ", SegmentStyle.ITALIC, StandardField.BOOKTITLE);
        b.slot("pages ", SegmentStyle.PLAIN, StandardField.PAGES);
        b.slot("", SegmentStyle.PLAIN, StandardField.PUBLISHER);
        b.yearSlot();
    }

    /// Author. *Title*. Type, School/Institution, number 42, 2020.
    /// Covers bibtex (phdthesis/mastersthesis/techreport: type optional) and biblatex
    /// (thesis/report: type required → placeholder when unset).
    private static void thesisOrReport(Builder b) {
        b.sentence();
        b.personSlot();
        b.sentence();
        b.slot("", SegmentStyle.ITALIC, StandardField.TITLE);
        b.sentence();
        b.slot("", SegmentStyle.PLAIN, StandardField.TYPE);
        b.slot("", SegmentStyle.PLAIN, StandardField.SCHOOL, StandardField.INSTITUTION);
        b.slot("number ", SegmentStyle.PLAIN, StandardField.NUMBER);
        b.yearSlot();
    }

    /// Generic shape for misc and unknown/custom types; required fields outside the
    /// vocabulary surface via [Builder#trailingRequiredPlaceholders()].
    private static void fallback(Builder b) {
        b.sentence();
        b.personSlot();
        b.sentence();
        b.slot("", SegmentStyle.PLAIN, StandardField.TITLE);
        b.sentence();
        b.slot("", SegmentStyle.ITALIC, StandardField.JOURNAL, StandardField.JOURNALTITLE);
        b.volumeNumberPages();
        b.sentence();
        b.slot("In ", SegmentStyle.ITALIC, StandardField.BOOKTITLE);
        b.sentence();
        b.slot("", SegmentStyle.PLAIN, StandardField.HOWPUBLISHED);
        b.slot("", SegmentStyle.PLAIN,
                StandardField.PUBLISHER, StandardField.ORGANIZATION, StandardField.SCHOOL, StandardField.INSTITUTION);
        b.yearSlot();
    }

    // endregion

    /// Accumulates tokens with citation punctuation: parts within a sentence are joined
    /// by ", ", sentences by ". ", and a final "." closes the rendering. A slot emits
    /// nothing when its field is neither set nor required — the surrounding punctuation
    /// is suppressed with it.
    private static final class Builder {

        private final BibEntry entry;
        private final SequencedSet<OrFields> requiredFields;
        private final List<Token> tokens = new ArrayList<>();
        private final SequencedSet<Field> covered = new LinkedHashSet<>();

        private boolean sentenceHasContent;
        private boolean anyContent;

        private Builder(BibEntry entry, SequencedSet<OrFields> requiredFields) {
            this.entry = entry;
            this.requiredFields = requiredFields;
        }

        private void sentence() {
            sentenceHasContent = false;
        }

        /// First set candidate → [FieldToken]; otherwise first candidate whose
        /// requirement (its [OrFields] group) is entirely unmet → [PlaceholderToken];
        /// otherwise nothing. The prefix literal is emitted only alongside a token.
        private void slot(String prefix, SegmentStyle style, Field... candidates) {
            firstSet(candidates).ifPresentOrElse(
                    field -> emitField(prefix, style, field, displayValue(field)),
                    () -> firstUnmetRequired(candidates).ifPresent(field -> emitPlaceholder(prefix, field)));
        }

        /// Author/editor slot: display via [AuthorList] ("First Last and First Last");
        /// an editor-resolved slot is marked with " (Eds.)".
        private void personSlot() {
            firstSet(StandardField.AUTHOR, StandardField.EDITOR).ifPresentOrElse(
                    field -> {
                        emitField("", SegmentStyle.PLAIN, field, displayPersons(field));
                        if (field == StandardField.EDITOR) {
                            tokens.add(new TextToken(" (Eds.)"));
                        }
                    },
                    () -> firstUnmetRequired(StandardField.AUTHOR, StandardField.EDITOR)
                            .ifPresent(field -> emitPlaceholder("", field)));
        }

        /// Year with date alias: the set one of year/date, else the required one.
        private void yearSlot() {
            slot("", SegmentStyle.PLAIN, StandardField.YEAR, StandardField.DATE);
        }

        /// Compact "12(3):45–67" when the volume is set; otherwise labeled stand-alone
        /// parts ("number 3", "pages 45–67"). Emits set fields only (never required in
        /// the standard types; custom requirements surface as trailing placeholders).
        private void volumeNumberPages() {
            if (isSet(StandardField.VOLUME)) {
                emitField("", SegmentStyle.PLAIN, StandardField.VOLUME, displayValue(StandardField.VOLUME));
                if (isSet(StandardField.NUMBER)) {
                    appendRaw("(", StandardField.NUMBER, ")");
                }
                if (isSet(StandardField.PAGES)) {
                    appendRaw(":", StandardField.PAGES, "");
                }
                return;
            }
            if (isSet(StandardField.NUMBER)) {
                slot("number ", SegmentStyle.PLAIN, StandardField.NUMBER);
            }
            if (isSet(StandardField.PAGES)) {
                slot("pages ", SegmentStyle.PLAIN, StandardField.PAGES);
            }
        }

        /// Required fields (per [OrFields] group) that the template did not represent
        /// and that are entirely unset — appended as a final placeholder sentence so
        /// custom/exotic requirements (e.g. biblatex `type`, `url`) stay visible.
        private void trailingRequiredPlaceholders() {
            sentence();
            requiredFields.stream()
                          .filter(group -> group.getFields().stream()
                                                .noneMatch(field -> covered.contains(field) || isSet(field)))
                          .map(OrFields::getPrimary)
                          .filter(field -> !covered.contains(field))
                          .forEach(field -> emitPlaceholder("", field));
        }

        /// Closing period after the last rendered token.
        private void finish() {
            if (anyContent) {
                tokens.add(new TextToken("."));
            }
        }

        // region emission

        private void emitField(String prefix, SegmentStyle style, Field field, String displayText) {
            separator();
            if (!prefix.isEmpty()) {
                tokens.add(new TextToken(prefix));
            }
            tokens.add(new FieldToken(field, displayText, style));
            markEmitted(field);
        }

        private void emitPlaceholder(String prefix, Field field) {
            separator();
            if (!prefix.isEmpty()) {
                tokens.add(new TextToken(prefix));
            }
            tokens.add(new PlaceholderToken(field));
            markEmitted(field);
        }

        /// Appends `open` + field + `close` directly after the previous token
        /// (no separator), for composites like "12(3):45–67".
        private void appendRaw(String open, Field field, String close) {
            tokens.add(new TextToken(open));
            tokens.add(new FieldToken(field, displayValue(field), SegmentStyle.PLAIN));
            if (!close.isEmpty()) {
                tokens.add(new TextToken(close));
            }
            markEmitted(field);
        }

        private void separator() {
            if (sentenceHasContent) {
                tokens.add(new TextToken(", "));
            } else if (anyContent) {
                tokens.add(new TextToken(". "));
            }
        }

        private void markEmitted(Field field) {
            covered.add(field);
            sentenceHasContent = true;
            anyContent = true;
        }

        // endregion

        // region field access and display

        private boolean isSet(Field field) {
            return entry.getField(field).filter(StringUtil::isNotBlank).isPresent();
        }

        private Optional<Field> firstSet(Field... candidates) {
            return Arrays.stream(candidates).filter(this::isSet).findFirst();
        }

        /// A candidate is an unmet requirement when some [OrFields] group contains it
        /// and none of that group's alternatives is set.
        private Optional<Field> firstUnmetRequired(Field... candidates) {
            return Arrays.stream(candidates)
                         .filter(candidate -> requiredFields.stream()
                                                            .anyMatch(group -> group.contains(candidate)
                                                                    && group.getFields().stream().noneMatch(this::isSet)))
                         .findFirst();
        }

        private String displayValue(Field field) {
            String value = WHITESPACE.matcher(LatexToUnicodeAdapter.format(entry.getField(field).orElse("")))
                                     .replaceAll(" ")
                                     .trim();
            if (field == StandardField.PAGES) {
                value = value.replace("--", "–");
            }
            if (value.length() > VALUE_DISPLAY_MAX_LENGTH) {
                value = value.substring(0, VALUE_DISPLAY_MAX_LENGTH - 1) + "…";
            }
            return value;
        }

        private String displayPersons(Field field) {
            String value = LatexToUnicodeAdapter.format(entry.getField(field).orElse(""));
            String formatted = AuthorList.parse(value).getAsFirstLastNamesWithAnd();
            return formatted.isBlank() ? displayValue(field) : formatted;
        }

        // endregion
    }
}
