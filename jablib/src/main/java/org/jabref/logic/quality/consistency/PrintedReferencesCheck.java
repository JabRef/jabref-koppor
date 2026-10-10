package org.jabref.logic.quality.consistency;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jabref.logic.importer.FetcherException;
import org.jabref.logic.importer.fetcher.CrossRef;
import org.jabref.logic.importer.fileformat.pdf.RuleBasedBibliographyPdfImporter;
import org.jabref.logic.l10n.Localization;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.BibEntryTypesManager;
import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.identifier.DOI;
import org.jabref.model.entry.types.EntryType;
import org.jabref.model.entry.types.StandardEntryType;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Checks the reference list printed in a PDF, e.g., the bibliography of a paper under review.
///
/// The references are read by [RuleBasedBibliographyPdfImporter], which keeps field values as printed.
/// Thus, faults of the underlying BibTeX data remain visible, such as a DOI given as URL.
///
/// [impl->req~jabkit.cli.pdf-check-references~1]
@NullMarked
public class PrintedReferencesCheck {

    /// @param reference the printed label of the reference (`[AL26]`), or the entry type for findings about several references (`@article`)
    public record Finding(String reference, String message) {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(PrintedReferencesCheck.class);

    private static final Pattern VOLUME_IN_NAME = Pattern.compile("\\bVolume \\d+");
    // Search engines and social networks list publications, but do not publish them
    private static final Pattern INDEX_URL = Pattern.compile("^https?://(?:[^/]*\\.)?(academia\\.edu|dblp\\.org|researchgate\\.net|scholar\\.google\\.[a-z.]+|semanticscholar\\.org)(?:[/:?]|$)");
    private static final Pattern INNER_UPPERCASE = Pattern.compile(".\\p{Lu}");

    private final @Nullable CrossRef crossRef;

    /// @param crossRef used to find missing DOIs and to compare the capitalization of titles; `null` to check offline
    public PrintedReferencesCheck(@Nullable CrossRef crossRef) {
        this.crossRef = crossRef;
    }

    public List<Finding> check(Path pdf) throws IOException {
        return check(new RuleBasedBibliographyPdfImporter().importDatabase(pdf).getDatabaseContext());
    }

    /// @param references entries as returned by [RuleBasedBibliographyPdfImporter] without citation key generation: the citation key is the printed label
    public List<Finding> check(BibDatabaseContext references) {
        List<Finding> findings = new ArrayList<>();
        for (BibEntry entry : references.getEntries()) {
            String label = label(entry);
            entry.getField(StandardField.DOI)
                 .filter(doi -> doi.startsWith("http"))
                 .ifPresent(doi -> findings.add(new Finding(label, Localization.lang("DOI is given as URL '%0'. Put only the DOI into the field, the style adds the link.", doi))));
            if ((entry.getType() == StandardEntryType.Article && !entry.hasField(StandardField.JOURNAL))
                    || (entry.getType() == StandardEntryType.InProceedings && !entry.hasField(StandardField.BOOKTITLE))) {
                findings.add(new Finding(label, Localization.lang("Nothing is printed after 'In:'. The venue is missing, or the entry type is wrong: use 'online' or 'misc' for web resources and preprints.")));
            }
            entry.getField(StandardField.URL)
                 .map(INDEX_URL::matcher)
                 .filter(Matcher::find)
                 .ifPresent(indexUrl -> findings.add(new Finding(label, Localization.lang("URL points to %0, which indexes publications, but does not publish them. Link the publisher's page or give the DOI.", indexUrl.group(1)))));
            entry.getField(StandardField.JOURNAL)
                 .filter(journal -> VOLUME_IN_NAME.matcher(journal).find())
                 .ifPresent(journal -> findings.add(new Finding(label, Localization.lang("Journal name '%0' contains the volume. Put the volume into the volume field.", journal))));
            if (crossRef != null) {
                findings.addAll(checkAgainstCrossRef(crossRef, label, entry));
            }
        }
        findings.addAll(checkConsistency(references));
        return findings;
    }

    private static List<Finding> checkAgainstCrossRef(CrossRef crossRef, String label, BibEntry entry) {
        List<Finding> findings = new ArrayList<>();
        try {
            Optional<DOI> doi = entry.getField(StandardField.DOI).flatMap(DOI::parse);
            if (doi.isEmpty()) {
                doi = crossRef.findIdentifier(entry);
                doi.ifPresent(found -> findings.add(new Finding(label, Localization.lang("No DOI is printed. Crossref has DOI %0.", found.asString()))));
            }
            if (doi.isEmpty()) {
                return findings;
            }
            Optional<String> printedTitle = entry.getField(StandardField.TITLE);
            Optional<String> registeredTitle = crossRef.performSearchById(doi.get().asString()).flatMap(registered -> registered.getField(StandardField.TITLE));
            if (printedTitle.isPresent() && registeredTitle.isPresent()) {
                String lostCapitals = lostCapitals(printedTitle.get(), registeredTitle.get());
                if (!lostCapitals.isEmpty()) {
                    findings.add(new Finding(label, Localization.lang("Capitalization differs from Crossref: %0. Protect these words with braces in the title field.", lostCapitals)));
                }
            }
        } catch (FetcherException e) {
            LOGGER.warn("Could not look up {} at Crossref", label, e);
        }
        return findings;
    }

    /// Words the printed title writes in a different case than the registered one, restricted to words with a capital letter
    /// after the first one ("IoT", "GitHub"). Other case differences are the choice of the bibliography style (title case, sentence case).
    ///
    /// @return `'Iot' should be 'IoT'`, several joined by `, `; empty if there are none
    static String lostCapitals(String printedTitle, String registeredTitle) {
        String[] printedWords = printedTitle.split("\\s+");
        String[] registeredWords = registeredTitle.split("\\s+");
        if (printedWords.length != registeredWords.length) {
            return "";
        }
        List<String> differences = new ArrayList<>();
        for (int i = 0; i < printedWords.length; i++) {
            String printed = printedWords[i];
            String registered = registeredWords[i];
            if (printed.equalsIgnoreCase(registered) && !printed.equals(registered) && INNER_UPPERCASE.matcher(registered).find()) {
                differences.add(Localization.lang("'%0' should be '%1'", printed, registered));
            }
        }
        return String.join(", ", differences);
    }

    /// References of the same type should list the same fields, e.g., all articles an ISSN or none.
    private static List<Finding> checkConsistency(BibDatabaseContext references) {
        BibliographyConsistencyCheck.Result result = new BibliographyConsistencyCheck().check(references, new BibEntryTypesManager(), (_, _) -> {
        });
        List<Finding> findings = new ArrayList<>();
        // The result map is filled from a HashMap; sorting keeps the output stable
        result.entryTypeToResultMap().entrySet().stream().sorted(Comparator.comparing(typeResult -> typeResult.getKey().getName())).forEach(typeResult -> {
            EntryType entryType = typeResult.getKey();
            BibliographyConsistencyCheck.EntryTypeResult entryTypeResult = typeResult.getValue();
            List<BibEntry> entriesOfType = references.getEntries().stream().filter(entry -> entry.getType().equals(entryType)).toList();
            entryTypeResult.fields().stream().sorted(Comparator.comparing(Field::getName)).forEach(field ->
                    findings.add(new Finding("@" + entryType.getName(), Localization.lang("'%0' is printed for %1, but not for %2.",
                            field.getName(), labels(entriesOfType, field, true), labels(entriesOfType, field, false)))));
        });
        return findings;
    }

    private static String labels(List<BibEntry> entries, Field field, boolean withField) {
        return entries.stream()
                      .filter(entry -> entry.hasField(field) == withField)
                      .map(PrintedReferencesCheck::label)
                      .collect(Collectors.joining(", "));
    }

    private static String label(BibEntry entry) {
        return "[" + entry.getCitationKey().orElse("?") + "]";
    }
}
