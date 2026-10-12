package org.jabref.logic.bibtex.comparator;

import java.util.stream.Stream;

import org.jabref.logic.bibtex.comparator.BibEntryCompare.Result;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BibEntryCompareTest {

    public static Stream<Arguments> compareEntries() {
        return Stream.of(
                Arguments.of(Result.EQUAL, new BibEntry(), new BibEntry()),
                Arguments.of(Result.SUBSET, new BibEntry(),
                        new BibEntry().withField(StandardField.AUTHOR, "Knuth")),
                Arguments.of(Result.SUPERSET, new BibEntry().withField(StandardField.AUTHOR, "Knuth"),
                        new BibEntry()),
                Arguments.of(Result.DISJUNCT, new BibEntry().withField(StandardField.AUTHOR, "Knuth"),
                        new BibEntry().withField(StandardField.EDITOR, "Mittelbach")),
                Arguments.of(
                        Result.DISJUNCT_OR_EQUAL_FIELDS,
                        new BibEntry()
                                .withField(StandardField.AUTHOR, "Knuth")
                                .withField(StandardField.YEAR, "2000"),
                        new BibEntry()
                                .withField(StandardField.EDITOR, "Mittelbach")
                                .withField(StandardField.YEAR, "2000")),
                Arguments.of(Result.DIFFERENT, new BibEntry().withField(StandardField.AUTHOR, "Knuth"),
                        new BibEntry().withField(StandardField.AUTHOR, "Mittelbach")));
    }

    @ParameterizedTest
    @MethodSource
    void compareEntries(Result equal, BibEntry first, BibEntry second) {
        assertEquals(equal, BibEntryCompare.compareEntries(first, second));
    }
}
