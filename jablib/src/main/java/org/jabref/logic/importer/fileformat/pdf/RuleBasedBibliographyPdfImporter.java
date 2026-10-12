package org.jabref.logic.importer.fileformat.pdf;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jabref.architecture.AllowedToUseApacheCommonsLang3;
import org.jabref.logic.citationkeypattern.CitationKeyGenerator;
import org.jabref.logic.citationkeypattern.CitationKeyPatternPreferences;
import org.jabref.logic.cleanup.URLCleanup;
import org.jabref.logic.formatter.bibtexfields.NormalizeUnicodeFormatter;
import org.jabref.logic.importer.AuthorListParser;
import org.jabref.logic.importer.ParserResult;
import org.jabref.logic.importer.plaincitation.PlainCitationParser;
import org.jabref.logic.importer.plaincitation.ReferencesBlockFromPdfFinder;
import org.jabref.logic.l10n.Localization;
import org.jabref.model.entry.AuthorList;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.Date;
import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.types.StandardEntryType;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Parses the references from the "References" section from a PDF.
///
/// Currently, IEEE two column format and the biblatex standard styles (e.g., `alphabetic`, `numeric`) are supported.
///
/// To extract a [BibEntry] matching the PDF, see [PdfContentImporter].
///
/// TODO: This class is similar to [org.jabref.logic.importer.plaincitation.RuleBasedPlainCitationParser], we need to unify them.
@AllowedToUseApacheCommonsLang3("Fastest method to count spaces in a string")
public class RuleBasedBibliographyPdfImporter extends BibliographyFromPdfImporter implements PlainCitationParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuleBasedBibliographyPdfImporter.class);

    /// Numeric labels (`[12]`) and alphabetic labels of BibTeX and biblatex styles (`[AL26]`, `[Buc+23]`, `[adr26]`, `[Kop+18a]`).
    /// BibTeX's `alpha.bst` typesets "et al." as a raised "+", which the PDF text shows as `[BSG+ 23]`.
    ///
    /// [impl->req~import.pdf.references.labelled~1]
    private static final String LABEL = "\\[(\\d+|\\p{L}[\\p{L}'-]*(?:\\+ ?)?\\d{2}[a-z]?)\\]";
    private static final Pattern REFERENCE_PATTERN = Pattern.compile(LABEL + "(.*?)(?=" + LABEL + "|$)", Pattern.DOTALL);
    private static final Pattern YEAR_AT_END = Pattern.compile(", (\\d{4})\\.$");
    private static final Pattern YEAR = Pattern.compile(", (\\d{4})(.*)");
    private static final Pattern PAGES = Pattern.compile(", pp\\. (\\d+--?\\d+)\\.?(.*)");
    private static final Pattern PAGE = Pattern.compile(", p\\. (\\d+)(.*)");
    private static final Pattern SERIES = Pattern.compile(", ser\\. ([^.,]+)(.*)");
    private static final Pattern MONTH_RANGE_AND_YEAR = Pattern.compile(", ([A-Z][a-z]{2,7}\\.?)-[A-Z][a-z]{2,7}\\.? (\\d+)(.*)");
    private static final Pattern MONTH_AND_YEAR = Pattern.compile(", ([A-Z][a-z]{2,7}\\.? \\d+),? ?(.*)");
    private static final Pattern VOLUME = Pattern.compile(", vol\\. (\\d+)(.*)");
    private static final Pattern NO = Pattern.compile(", no\\. (\\d+)(.*)");
    private static final Pattern PROCEEDINGS_INDICATION = Pattern.compile("^in (Proc\\. )?(.*)");
    private static final Pattern WORKSHOP = Pattern.compile("Workshop");
    private static final Pattern AUTHORS_AND_TITLE_AT_BEGINNING = Pattern.compile("^([^“]+), “(.*?)(”,|,”) ");
    private static final Pattern TITLE = Pattern.compile("“(.*?)”, (.*)");

    // biblatex standard styles: `Authors. “Title”. In: Container. issn: …. doi: …. url: … (visited on …).`
    private static final Pattern BIBLATEX_AUTHORS_AND_TITLE = Pattern.compile("^(.+?)\\. “(.+?)”\\.? ?(.*)$");
    // Titles of books and online resources are printed in italics, not in quotes
    private static final Pattern BIBLATEX_AUTHORS_AND_UNQUOTED_TITLE = Pattern.compile("^(.*?\\p{L}{2})\\. ?(.+?)\\. (.*)$");
    private static final Pattern BIBLATEX_FIELD_LABEL = Pattern.compile("\\b(issn|isbn|doi|url): ", Pattern.CASE_INSENSITIVE);
    private static final Pattern BIBLATEX_VISITED_ON = Pattern.compile("\\(visited on [^)]*\\)");
    private static final Pattern BIBLATEX_ARTICLE = Pattern.compile("^In: (?:(.*?[^-\\s]) )?(?:(\\d+)(?:\\.(\\S+))? )?\\([^)]*?(\\d{4})\\)(?:, pp?\\. (\\S+?))?\\.?$");
    private static final Pattern BIBLATEX_PAGES_AT_END = Pattern.compile(", pp?\\. (\\S+?)\\.?$");
    private static final Pattern BIBLATEX_YEAR_AT_END = Pattern.compile("(?:^|[ ,])(\\d{4})\\.?$");
    private static final Pattern BIBLATEX_VOLUME_AND_SERIES_AT_END = Pattern.compile("\\. Vol\\. (\\S+?)(?:\\. (.+))?$");
    private static final Pattern BIBLATEX_EDITORS_AT_END = Pattern.compile("\\. Ed\\. by (.+)$");
    // The page number printed below the last reference on a page ends up after the reference's final dot
    private static final Pattern TRAILING_PAGE_NUMBER = Pattern.compile("\\.\\s+\\d{1,4}$");

    @Nullable private final CitationKeyPatternPreferences citationKeyPatternPreferences;
    private final NormalizeUnicodeFormatter normalizeUnicodeFormatter = new NormalizeUnicodeFormatter();

    public RuleBasedBibliographyPdfImporter() {
        this.citationKeyPatternPreferences = null;
    }

    public RuleBasedBibliographyPdfImporter(@NonNull CitationKeyPatternPreferences citationKeyPatternPreferences) {
        this.citationKeyPatternPreferences = citationKeyPatternPreferences;
    }

    @Override
    public String getId() {
        return "pdfBibiliography";
    }

    @Override
    public String getName() {
        return "Bibliography from PDF";
    }

    @Override
    public String getDescription() {
        return Localization.lang("Reads the references from the 'References' section of a PDF file.");
    }

    /// Online Grobid implementation: [org.jabref.logic.importer.util.GrobidService#processReferences(java.nio.file.Path, org.jabref.logic.importer.ImportFormatPreferences)]
    @Override
    public ParserResult importDatabase(Path filePath, PDDocument document) throws IOException {
        String contents;
        contents = ReferencesBlockFromPdfFinder.getReferencesPagesText(document);
        List<BibEntry> result = getEntriesFromPDFContent(contents);

        ParserResult parserResult = new ParserResult(result);

        if (citationKeyPatternPreferences == null) {
            return parserResult;
        }

        // Generate citation keys for result
        CitationKeyGenerator citationKeyGenerator = new CitationKeyGenerator(parserResult.getDatabaseContext(), citationKeyPatternPreferences);
        parserResult.getDatabase().getEntries().forEach(citationKeyGenerator::generateAndSetKey);

        return parserResult;
    }

    @VisibleForTesting
    record IntermediateData(String label, String reference) {
    }

    /// In: `[1] ...\n...\n...[2]...\n...\n...\n[3]...`
    public List<BibEntry> getEntriesFromPDFContent(String contents) {
        List<IntermediateData> referencesStrings = getIntermediateData(contents);

        return referencesStrings.stream()
                                .map(data -> parsePlainCitation(data.label(), data.reference()))
                                .toList();
    }

    @VisibleForTesting
    static List<IntermediateData> getIntermediateData(String contents) {
        List<IntermediateData> referencesStrings = new ArrayList<>();
        Matcher matcher = REFERENCE_PATTERN.matcher(contents);
        while (matcher.find()) {
            String reference = matcher.group(2).replaceAll("\\r?\\n", " ").trim();
            referencesStrings.add(new IntermediateData(matcher.group(1).replace(" ", ""), reference));
        }
        return referencesStrings;
    }

    @Override
    public Optional<BibEntry> parsePlainCitation(String reference) {
        return Optional.of(parsePlainCitation("0", reference));
    }

    /// Example: `J. Knaster et al., “Overview of the IFMIF/EVEDA project”, Nucl. Fusion, vol. 57, p. 102016, 2017. doi:10.1088/ 1741-4326/aa6a6a`
    @VisibleForTesting
    BibEntry parsePlainCitation(String label, String reference) {
        reference = normalizeUnicodeFormatter.format(reference);
        String originalReference = "[" + label + "] " + reference;

        if (BIBLATEX_AUTHORS_AND_TITLE.matcher(reference).find()
                || (!reference.contains("“") && BIBLATEX_FIELD_LABEL.matcher(reference).find())) {
            return parseBiblatexCitation(label, reference).withField(StandardField.COMMENT, originalReference);
        }

        BibEntry result = new BibEntry(StandardEntryType.Article)
                .withCitationKey(label);

        reference = removeLineBreakArtifacts(reference);

        // Move URL to URL field
        Matcher urlPatternMatcher = URLCleanup.URL_PATTERN.matcher(reference);
        if (urlPatternMatcher.find()) {
            String url = urlPatternMatcher.group();
            result.setField(StandardField.URL, url);
            reference = reference.replace(url, "").trim();
            if (reference.endsWith(",")) {
                reference = reference.substring(0, reference.length() - 1);
            }
        }

        // J. Knaster et al., “Overview of the IFMIF/EVEDA project”, Nucl. Fusion, vol. 57, p. 102016, 2017. doi:10.1088/ 1741-4326/aa6a6a
        // Y. Shimosaki et al., “Lattice design for 5 MeV – 125 mA CW RFQ operation in LIPAc”, in Proc. IPAC’19, Mel- bourne, Australia, May 2019, pp. 977-979. doi:10.18429/ JACoW-IPAC2019-MOPTS051
        int pos = reference.indexOf("doi:");
        if (pos >= 0) {
            String doi = reference.substring(pos + "doi:".length()).trim();
            doi = doi.replace(" ", "");
            result.setField(StandardField.DOI, doi);
            reference = reference.substring(0, pos).trim();
        }

        reference = updateEntryAndReferenceIfMatches(reference, PAGES, result, StandardField.PAGES).newReference;

        // J. Knaster et al., “Overview of the IFMIF/EVEDA project”, Nucl. Fusion, vol. 57, p. 102016
        // Y. Shimosaki et al., “Lattice design for 5 MeV – 125 mA CW RFQ operation in LIPAc”, in Proc. IPAC’19, Mel- bourne, Australia, May 2019
        reference = updateEntryAndReferenceIfMatches(reference, PAGE, result, StandardField.PAGES).newReference;

        reference = updateEntryAndReferenceIfMatches(reference, SERIES, result, StandardField.SERIES).newReference;

        Matcher matcher = MONTH_RANGE_AND_YEAR.matcher(reference);
        if (matcher.find()) {
            // strip out second monthp
            reference = reference.substring(0, matcher.start()) + ", " + matcher.group(1) + " " + matcher.group(2) + matcher.group(3);
        }

        // J. Knaster et al., “Overview of the IFMIF/EVEDA project”, Nucl. Fusion, vol. 57
        // Y. Shimosaki et al., “Lattice design for 5 MeV – 125 mA CW RFQ operation in LIPAc”, in Proc. IPAC’19, Mel- bourne, Australia, May 2019
        matcher = MONTH_AND_YEAR.matcher(reference);
        if (matcher.find()) {
            Optional<Date> parsedDate = Date.parse(matcher.group(1));
            if (parsedDate.isPresent()) {
                Date date = parsedDate.get();
                date.getYear().ifPresent(year -> result.setField(StandardField.YEAR, year.toString()));
                date.getMonth().ifPresent(month -> result.setField(StandardField.MONTH, month.getJabRefFormat()));

                String prefix = reference.substring(0, matcher.start()).trim();
                String suffix = matcher.group(2);
                if (!suffix.isEmpty() && !".".equals(suffix)) {
                    suffix = ", " + suffix.replaceAll("^\\. ", "");
                } else {
                    suffix = "";
                }
                reference = prefix + suffix;
            }
        }

        // J. Knaster et al., “Overview of the IFMIF/EVEDA project”, Nucl. Fusion, vol. 57, p. 102016, 2017.
        // Y. Shimosaki et al., “Lattice design for 5 MeV – 125 mA CW RFQ operation in LIPAc”, in Proc. IPAC’19, Mel- bourne, Australia, May 2019, pp. 977-979
        matcher = YEAR_AT_END.matcher(reference);
        if (matcher.find()) {
            result.setField(StandardField.YEAR, matcher.group(1));
            reference = reference.substring(0, matcher.start()).trim();
        }

        reference = updateEntryAndReferenceIfMatches(reference, YEAR, result, StandardField.YEAR).newReference;

        // J. Knaster et al., “Overview of the IFMIF/EVEDA project”, Nucl. Fusion, vol. 57
        // Y. Shimosaki et al., “Lattice design for 5 MeV – 125 mA CW RFQ operation in LIPAc”, in Proc. IPAC’19, Mel- bourne, Australia
        EntryUpdateResult entryUpdateResult = updateEntryAndReferenceIfMatches(reference, VOLUME, result, StandardField.VOLUME);
        boolean volumeFound = entryUpdateResult.modified;
        reference = entryUpdateResult.newReference;

        entryUpdateResult = updateEntryAndReferenceIfMatches(reference, NO, result, StandardField.NUMBER);
        boolean numberFound = entryUpdateResult.modified;
        reference = entryUpdateResult.newReference;

        // J. Knaster et al., “Overview of the IFMIF/EVEDA project”, Nucl. Fusion
        // Y. Shimosaki et al., “Lattice design for 5 MeV – 125 mA CW RFQ operation in LIPAc”, in Proc. IPAC’19, Mel- bourne, Australia
        matcher = AUTHORS_AND_TITLE_AT_BEGINNING.matcher(reference);
        if (matcher.find()) {
            result.setField(StandardField.AUTHOR, normalizeAuthors(matcher.group(1)));
            result.setField(StandardField.TITLE, matcher.group(2).replaceAll("et al\\.?", "and others"));
            reference = reference.substring(matcher.end()).trim();
        } else {
            // No authors present
            // Example: “AF4.1.1 SRF Linac Engineering Design Report”, Internal note.
            reference = updateEntryAndReferenceIfMatches(reference, TITLE, result, StandardField.TITLE).newReference;
        }

        // Nucl. Fusion
        // in Proc. IPAC’19, Mel- bourne, Australia
        // presented at th 8th DITANET Topical Workshop on Beam Position Monitors, CERN, Geneva, Switzreland
        List<String> stringsToRemove = List.of("presented at", "to be presented at");
        // need to use "for" loop instead of "stream().foreach", because "reference" is modified inside the loop
        for (String check : stringsToRemove) {
            if (reference.startsWith(check)) {
                reference = reference.substring(check.length()).trim();
                result.setType(StandardEntryType.InProceedings);
            }
        }

        Matcher proceedingsMatcher = PROCEEDINGS_INDICATION.matcher(reference);
        Matcher workshopMatcher = WORKSHOP.matcher(reference);
        if (proceedingsMatcher.find() || workshopMatcher.find() && (!volumeFound && !numberFound)) {
            result.setType(StandardEntryType.InProceedings);

            String bookTitle;
            int offset;
            if (proceedingsMatcher.hasMatch()) {
                offset = proceedingsMatcher.start(2) - 3; // 3 is the length of "in "
                String proc = proceedingsMatcher.group(1);
                if (proc == null) {
                    bookTitle = proceedingsMatcher.group(2);
                } else {
                    // We keep "Proc. "
                    bookTitle = proc + proceedingsMatcher.group(2);
                }
            } else {
                offset = 0;
                bookTitle = reference;
            }
            reference = "";

            int lastDot = bookTitle.substring(offset).lastIndexOf(". ");
            if (lastDot == -1) {
                lastDot = bookTitle.substring(offset).lastIndexOf('.');
            }
            if (lastDot > offset) {
                String textAfterDot = bookTitle.substring(offset + lastDot + 1).trim();
                // We use Apache Commons here, because it is fastest - see table at https://stackoverflow.com/a/35242882/873282
                if (!textAfterDot.contains("http") && (StringUtils.countMatches(textAfterDot, ' ') <= 1)) {
                    bookTitle = bookTitle.substring(0, offset + lastDot).trim();
                    if (bookTitle.startsWith("in ")) {
                        bookTitle = bookTitle.substring(3);
                    }
                    result.setField(StandardField.PUBLISHER, textAfterDot);
                }
            }

            result.setField(StandardField.BOOKTITLE, bookTitle);
        }

        if (reference.isEmpty()) {
            // Early quit if everything was handled
            result.setField(StandardField.COMMENT, originalReference);
            return result;
        }

        // Nucl. Fusion
        reference = reference.trim().replaceAll("\\.$", "");

        if (volumeFound || numberFound) {
            result.setField(StandardField.JOURNAL, reference);
        } else if (!reference.contains(",") && !reference.isEmpty()) {
            if (reference.endsWith(" Note") || reference.endsWith(" note")) {
                result.setField(StandardField.NOTE, reference);
                result.setType(StandardEntryType.TechReport);
            } else {
                LOGGER.debug("Falling back to journal even if no volume and no number was found. Reference: {}", reference);
                result.setField(StandardField.JOURNAL, reference);
            }
        } else {
            LOGGER.trace("InProceedings fallback used. Reference: {}", reference);
            result.setType(StandardEntryType.InProceedings);
            if (result.hasField(StandardField.BOOKTITLE)) {
                String oldTitle = result.getField(StandardField.BOOKTITLE).get();
                result.setField(StandardField.BOOKTITLE, oldTitle + " " + reference);
            } else {
                result.setField(StandardField.BOOKTITLE, reference);
            }
        }

        result.setField(StandardField.COMMENT, originalReference);
        return result;
    }

    private static String removeLineBreakArtifacts(String reference) {
        return reference
                .replace(".-", "-")
                // Unicode en dash (used as page separator)
                .replace("–", "-")
                // Remove "- " introduced by linebreaks in the PDF
                .replaceAll("([^ ])- ", "$1");
    }

    private static String normalizeAuthors(String authorsText) {
        String authors = authorsText.replaceAll("et al\\.?", "and others");
        // Alternative: AuthorList.fixAuthorFirstNameFirst(authors) only
        // However, this does not work with special cases. Thus, we do a simple transformation only.
        return AuthorListParser.normalizeSimply(authors).orElseGet(() -> AuthorList.fixAuthorFirstNameFirst(authors));
    }

    /// Parses a reference formatted by one of the biblatex standard styles.
    ///
    /// [impl->req~import.pdf.references.labelled~1]
    ///
    /// Example: `Aisha Alansari and Hamzah Luqman. “Large language models hallucination: A comprehensive survey”. In: Computer Science Review 61 (2026), p. 100970. issn: 1574-0137. doi: 10.1016/j.cosrev.2026.100970.`
    private static BibEntry parseBiblatexCitation(String label, String reference) {
        BibEntry result = new BibEntry(StandardEntryType.Misc).withCitationKey(label);
        reference = TRAILING_PAGE_NUMBER.matcher(reference.trim()).replaceFirst(".");

        // issn, isbn, doi, and url come last. Their values are taken as printed:
        // there, a line break at a "-" does not hyphenate, the "-" is part of the value.
        List<MatchResult> fieldLabels = BIBLATEX_FIELD_LABEL.matcher(reference).results().toList();
        for (int i = 0; i < fieldLabels.size(); i++) {
            MatchResult fieldLabel = fieldLabels.get(i);
            Field field = switch (fieldLabel.group(1).toLowerCase(Locale.ROOT)) {
                case "issn" ->
                        StandardField.ISSN;
                case "isbn" ->
                        StandardField.ISBN;
                case "doi" ->
                        StandardField.DOI;
                default ->
                        StandardField.URL;
            };
            int valueEnd = i + 1 < fieldLabels.size() ? fieldLabels.get(i + 1).start() : reference.length();
            String value = BIBLATEX_VISITED_ON.matcher(reference.substring(fieldLabel.end(), valueEnd)).replaceAll("")
                                              .replaceAll("\\s", "")
                                              .replaceAll("\\.$", "");
            result.setField(field, value);
        }

        String head = fieldLabels.isEmpty() ? reference : reference.substring(0, fieldLabels.getFirst().start());
        String rest = removeLineBreakArtifacts(head).trim();
        Matcher authorsAndTitle = BIBLATEX_AUTHORS_AND_TITLE.matcher(rest);
        if (!authorsAndTitle.matches()) {
            authorsAndTitle = BIBLATEX_AUTHORS_AND_UNQUOTED_TITLE.matcher(rest);
        }
        if (authorsAndTitle.matches()) {
            result.setField(StandardField.AUTHOR, normalizeBiblatexNames(authorsAndTitle.group(1)));
            result.setField(StandardField.TITLE, authorsAndTitle.group(2));
            rest = authorsAndTitle.group(3).trim();
        }

        Matcher article = BIBLATEX_ARTICLE.matcher(rest);
        if (article.matches()) {
            result.setType(StandardEntryType.Article);
            setFieldIfPresent(result, StandardField.JOURNAL, article.group(1));
            setFieldIfPresent(result, StandardField.VOLUME, article.group(2));
            setFieldIfPresent(result, StandardField.NUMBER, article.group(3));
            result.setField(StandardField.YEAR, article.group(4));
            setFieldIfPresent(result, StandardField.PAGES, article.group(5));
            return result;
        }

        boolean inContainer = rest.startsWith("In: ");
        if (inContainer) {
            result.setType(StandardEntryType.InProceedings);
            rest = rest.substring("In: ".length());
        }
        rest = takeFromEnd(rest, BIBLATEX_PAGES_AT_END, result, StandardField.PAGES);
        rest = takeFromEnd(rest, BIBLATEX_YEAR_AT_END, result, StandardField.YEAR);
        if (rest.endsWith(",")) {
            // "Publisher, 2019" or "Location: Publisher, 2019"
            rest = rest.substring(0, rest.length() - 1);
            int publisherStart = rest.lastIndexOf(". ") + 1;
            String publisher = rest.substring(publisherStart).trim();
            int locationEnd = publisher.indexOf(": ");
            if (locationEnd >= 0) {
                result.setField(StandardField.LOCATION, publisher.substring(0, locationEnd));
                publisher = publisher.substring(locationEnd + 2);
            }
            result.setField(StandardField.PUBLISHER, publisher);
            rest = rest.substring(0, publisherStart);
        }
        if (!inContainer) {
            return result;
        }

        rest = rest.replaceAll("\\.$", "");
        rest = takeFromEnd(rest, BIBLATEX_VOLUME_AND_SERIES_AT_END, result, StandardField.VOLUME, StandardField.SERIES);
        Matcher editors = BIBLATEX_EDITORS_AT_END.matcher(rest);
        if (editors.find()) {
            result.setField(StandardField.EDITOR, normalizeBiblatexNames(editors.group(1)));
            rest = rest.substring(0, editors.start());
        }
        setFieldIfPresent(result, StandardField.BOOKTITLE, rest);
        return result;
    }

    /// biblatex prints names as "Given Family", thus a comma only separates names: "A, B, and C".
    private static String normalizeBiblatexNames(String names) {
        return names.replace(", and ", " and ")
                    .replace(", ", " and ")
                    .replaceAll(" et al\\.?$", " and others");
    }

    /// Moves the groups of `pattern`, which has to match at the end of `reference`, into `fields`.
    ///
    /// @return `reference` without the matched part
    private static String takeFromEnd(String reference, Pattern pattern, BibEntry entry, Field... fields) {
        Matcher matcher = pattern.matcher(reference);
        if (!matcher.find()) {
            return reference;
        }
        for (int i = 0; i < fields.length; i++) {
            setFieldIfPresent(entry, fields[i], matcher.group(i + 1));
        }
        return reference.substring(0, matcher.start()).trim();
    }

    private static void setFieldIfPresent(BibEntry entry, Field field, @Nullable String value) {
        if (value != null && !value.isBlank()) {
            entry.setField(field, value.trim());
        }
    }

    /// @param pattern A pattern matching two groups: The first one to take, the second one to leave at the end of the string
    private static EntryUpdateResult updateEntryAndReferenceIfMatches(String reference, Pattern pattern, BibEntry result, Field
            field) {
        Matcher matcher;
        matcher = pattern.matcher(reference);
        if (!matcher.find()) {
            return new EntryUpdateResult(false, reference);
        }
        result.setField(field, matcher.group(1));
        String suffix = matcher.group(2);
        reference = reference.substring(0, matcher.start()).trim() + suffix;
        return new EntryUpdateResult(true, reference);
    }

    private record EntryUpdateResult(boolean modified, String newReference) {
    }
}
