package org.jabref.logic.quality.consistency;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import org.jabref.logic.importer.fetcher.CrossRef;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.types.StandardEntryType;
import org.jabref.support.ExternalServicesTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// [utest->req~jabkit.cli.pdf-check-references~1]
class PrintedReferencesCheckTest {

    @Test
    void biblatexAlphabetic() throws URISyntaxException, IOException {
        Path pdf = Path.of(PrintedReferencesCheckTest.class.getResource("/pdfs/biblatex/alphabetic.pdf").toURI());
        assertEquals(List.of(
                        new PrintedReferencesCheck.Finding("[adr26]", "No journal is printed. The entry type is probably wrong: use 'online' or 'misc' for web resources."),
                        new PrintedReferencesCheck.Finding("[AL26]", "DOI is given as URL 'https://doi.org/10.1016/j.cosrev.2026.100970'. Put only the DOI into the field, the style adds the link."),
                        new PrintedReferencesCheck.Finding("[ATN25]", "Journal name 'Frontiers in Artificial Intelligence Volume 8 - 2025' contains the volume. Put the volume into the volume field."),
                        new PrintedReferencesCheck.Finding("@article", "'doi' is printed for [AL26], [ATN25], [Buc+23], but not for [adr26]."),
                        new PrintedReferencesCheck.Finding("@article", "'issn' is printed for [AL26], [ATN25], but not for [adr26], [Buc+23]."),
                        new PrintedReferencesCheck.Finding("@article", "'journal' is printed for [AL26], [ATN25], [Buc+23], but not for [adr26]."),
                        new PrintedReferencesCheck.Finding("@article", "'pages' is printed for [AL26], [Buc+23], but not for [adr26], [ATN25]."),
                        new PrintedReferencesCheck.Finding("@article", "'url' is printed for [adr26], [AL26], [ATN25], but not for [Buc+23]."),
                        new PrintedReferencesCheck.Finding("@article", "'volume' is printed for [AL26], [Buc+23], but not for [adr26], [ATN25]."),
                        new PrintedReferencesCheck.Finding("@inproceedings", "'doi' is printed for [JB05], but not for [KA19], [KAZ18]."),
                        new PrintedReferencesCheck.Finding("@inproceedings", "'pages' is printed for [JB05], [KA19], but not for [KAZ18]."),
                        new PrintedReferencesCheck.Finding("@inproceedings", "'publisher' is printed for [KA19], but not for [JB05], [KAZ18]."),
                        new PrintedReferencesCheck.Finding("@inproceedings", "'series' is printed for [KA19], but not for [JB05], [KAZ18]."),
                        new PrintedReferencesCheck.Finding("@inproceedings", "'url' is printed for [KA19], [KAZ18], but not for [JB05]."),
                        new PrintedReferencesCheck.Finding("@inproceedings", "'volume' is printed for [KA19], but not for [JB05], [KAZ18].")),
                new PrintedReferencesCheck(null).check(pdf));
    }

    @Test
    void lostCapitalsReportsWordsWithInnerCapitals() {
        assertEquals("'Iot' should be 'IoT'", PrintedReferencesCheck.lostCapitals("Iot reference model", "IoT Reference Model"));
    }

    @Test
    void lostCapitalsIgnoresSentenceCase() {
        assertEquals("", PrintedReferencesCheck.lostCapitals("Large language models hallucination", "Large Language Models Hallucination"));
    }

    @Test
    @ExternalServicesTest
    void crossRefFindsDoiAndLostCapitals() {
        BibEntry entry = new BibEntry(StandardEntryType.InCollection)
                .withCitationKey("7")
                .withField(StandardField.AUTHOR, "M. Bauer and N. Bui and J. De Loof and C. Magerkurth and A. Nettsträter and J. Stefa and J. W. Walewski")
                .withField(StandardField.TITLE, "Iot reference model")
                .withField(StandardField.BOOKTITLE, "Enabling Things to Talk: Designing IoT solutions with the IoT architectural reference model")
                .withField(StandardField.YEAR, "2013");
        assertEquals(List.of(
                        new PrintedReferencesCheck.Finding("[7]", "No DOI is printed. Crossref has DOI 10.1007/978-3-642-40403-0_7."),
                        new PrintedReferencesCheck.Finding("[7]", "Capitalization differs from Crossref: 'Iot' should be 'IoT'. Protect these words with braces in the title field.")),
                new PrintedReferencesCheck(new CrossRef()).check(new BibDatabaseContext(new BibDatabase(List.of(entry)))));
    }
}
