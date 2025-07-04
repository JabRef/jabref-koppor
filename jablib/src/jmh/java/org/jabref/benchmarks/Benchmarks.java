package org.jabref.benchmarks;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Random;

import org.jabref.logic.bibtex.FieldPreferences;
import org.jabref.logic.citationkeypattern.CitationKeyPatternPreferences;
import org.jabref.logic.exporter.BibDatabaseWriter;
import org.jabref.logic.exporter.BibWriter;
import org.jabref.logic.exporter.SelfContainedSaveConfiguration;
import org.jabref.logic.formatter.bibtexfields.HtmlToLatexFormatter;
import org.jabref.logic.importer.ParserResult;
import org.jabref.logic.importer.fileformat.BibtexParser;
import org.jabref.logic.layout.format.HTMLChars;
import org.jabref.logic.layout.format.LatexToUnicodeFormatter;
import org.jabref.logic.os.OS;
import org.jabref.logic.preferences.CliPreferences;
import org.jabref.logic.preferences.JabRefCliPreferences;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.database.BibDatabaseMode;
import org.jabref.model.database.BibDatabaseModeDetection;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.BibEntryTypesManager;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.field.UnknownField;
import org.jabref.model.groups.GroupHierarchyType;
import org.jabref.model.groups.KeywordGroup;
import org.jabref.model.groups.WordKeywordGroup;
import org.jabref.model.metadata.MetaData;
import org.jabref.model.metadata.SaveOrder;

import com.airhacks.afterburner.injection.Injector;
import org.mockito.Answers;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import static org.mockito.Mockito.mock;

@State(Scope.Thread)
public class Benchmarks {

    private String bibtexString;
    private final BibDatabase database = new BibDatabase();
    private String latexConversionString;
    private String htmlConversionString;

    @Setup
    public void init() throws IOException {
        Injector.setModelOrService(CliPreferences.class, JabRefCliPreferences.getInstance());

        Random randomizer = new Random();
        for (int i = 0; i < 1000; i++) {
            BibEntry entry = new BibEntry();
            entry.setCitationKey("id" + i);
            entry.setField(StandardField.TITLE, "This is my title " + i);
            entry.setField(StandardField.AUTHOR, "Firstname Lastname and FirstnameA LastnameA and FirstnameB LastnameB" + i);
            entry.setField(StandardField.JOURNAL, "Journal Title " + i);
            entry.setField(StandardField.KEYWORDS, "testkeyword");
            entry.setField(StandardField.YEAR, "1" + i);
            entry.setField(new UnknownField("rnd"), "2" + randomizer.nextInt());
            database.insertEntry(entry);
        }

        bibtexString = getOutputWriter().toString();

        latexConversionString = "{A} \\textbf{bold} approach {\\it to} ${{\\Sigma}}{\\Delta}$ modulator \\textsuperscript{2} \\$";

        htmlConversionString = "<b>&Ouml;sterreich</b> &#8211; &amp; characters &#x2aa2; <i>italic</i>";
    }

    private StringWriter getOutputWriter() throws IOException {
        StringWriter outputWriter = new StringWriter();
        BibWriter bibWriter = new BibWriter(outputWriter, OS.NEWLINE);
        SelfContainedSaveConfiguration saveConfiguration = new SelfContainedSaveConfiguration(SaveOrder.getDefaultSaveOrder(), false, BibDatabaseWriter.SaveType.WITH_JABREF_META_DATA, false);
        FieldPreferences fieldPreferences = new FieldPreferences(true, List.of(), List.of());
        CitationKeyPatternPreferences citationKeyPatternPreferences = mock(CitationKeyPatternPreferences.class, Answers.RETURNS_DEEP_STUBS);

        BibDatabaseWriter databaseWriter = new BibDatabaseWriter(
                bibWriter,
                saveConfiguration,
                fieldPreferences,
                citationKeyPatternPreferences,
                new BibEntryTypesManager());
        databaseWriter.savePartOfDatabase(new BibDatabaseContext(database, new MetaData()), database.getEntries());
        return outputWriter;
    }

    @Benchmark
    public ParserResult parse() throws IOException {
        CliPreferences preferences = Injector.instantiateModelOrService(CliPreferences.class);
        BibtexParser parser = new BibtexParser(preferences.getImportFormatPreferences());
        return parser.parse(new StringReader(bibtexString));
    }

    @Benchmark
    public String write() throws IOException {
        return getOutputWriter().toString();
    }

    @Benchmark
    public List<BibEntry> search() {
        // TODO: Create Benchmark for LuceneSearch
        return List.of();
    }

    @Benchmark
    public List<BibEntry> index() {
        // TODO: Create Benchmark for LuceneIndexer
        return List.of();
    }

    @Benchmark
    public BibDatabaseMode inferBibDatabaseMode() {
        return BibDatabaseModeDetection.inferMode(database);
    }

    @Benchmark
    public String latexToUnicodeConversion() {
        LatexToUnicodeFormatter f = new LatexToUnicodeFormatter();
        return f.format(latexConversionString);
    }

    @Benchmark
    public String latexToHTMLConversion() {
        HTMLChars f = new HTMLChars();
        return f.format(latexConversionString);
    }

    @Benchmark
    public String htmlToLatexConversion() {
        HtmlToLatexFormatter f = new HtmlToLatexFormatter();
        return f.format(htmlConversionString);
    }

    @Benchmark
    public boolean keywordGroupContains() {
        KeywordGroup group = new WordKeywordGroup("testGroup", GroupHierarchyType.INDEPENDENT, StandardField.KEYWORDS, "testkeyword", false, ',', false);
        return group.containsAll(database.getEntries());
    }

    public static void main(String[] args) throws IOException {
        Main.main(args);
    }
}
