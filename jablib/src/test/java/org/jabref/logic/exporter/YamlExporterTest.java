package org.jabref.logic.exporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.jabref.logic.layout.LayoutFormatterPreferences;
import org.jabref.logic.util.StandardFileType;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.types.StandardEntryType;
import org.jabref.model.metadata.SaveOrder;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class YamlExporterTest {

    private static Exporter yamlExporter;
    private static BibDatabaseContext databaseContext;

    @BeforeAll
    static void setUp() {
        yamlExporter = new TemplateExporter(
                "CSL YAML",
                "yaml",
                "yaml",
                null,
                StandardFileType.YAML,
                mock(LayoutFormatterPreferences.class, Answers.RETURNS_DEEP_STUBS),
                SaveOrder.getDefaultSaveOrder(),
                BlankLineBehaviour.DELETE_BLANKS);

        databaseContext = new BibDatabaseContext();
    }

    @Test
    final void exportForNoEntriesWritesNothing(@TempDir Path tempFile) throws IOException, SaveException, ParserConfigurationException, TransformerException {
        Path file = tempFile.resolve("ThisIsARandomlyNamedFile");
        Files.createFile(file);
        yamlExporter.export(databaseContext, tempFile, List.of());
        assertEquals(List.of(), Files.readAllLines(file));
    }

    @Test
    final void exportsCorrectContent(@TempDir Path tempFile) throws IOException, SaveException, ParserConfigurationException, TransformerException {
        BibEntry entry = new BibEntry(StandardEntryType.Article)
                .withCitationKey("test")
                .withField(StandardField.AUTHOR, "Test Author")
                .withField(StandardField.TITLE, "Test Title")
                .withField(StandardField.URL, "http://example.com")
                .withField(StandardField.DATE, "2020-10-14");

        Path file = tempFile.resolve("RandomFileName");
        Files.createFile(file);
        yamlExporter.export(databaseContext, file, List.of(entry));

        List<String> expected = List.of(
                "---",
                "references:",
                "- id: test",
                "  type: article",
                "  author:",
                "  - literal: \"Test Author\"",
                "  title: \"Test Title\"",
                "  issued: 2020-10-14",
                "  url: http://example.com",
                "---");

        assertEquals(expected, Files.readAllLines(file));
    }

    @Test
    final void formatsContentCorrect(@TempDir Path tempFile) throws IOException, SaveException, ParserConfigurationException, TransformerException {
        BibEntry entry = new BibEntry(StandardEntryType.Misc)
                .withCitationKey("test")
                .withField(StandardField.AUTHOR, "Test Author")
                .withField(StandardField.TITLE, "Test Title")
                .withField(StandardField.URL, "http://example.com")
                .withField(StandardField.DATE, "2020-10-14");

        Path file = tempFile.resolve("RandomFileName");
        Files.createFile(file);
        yamlExporter.export(databaseContext, file, List.of(entry));

        List<String> expected = List.of(
                "---",
                "references:",
                "- id: test",
                "  type: no-type",
                "  author:",
                "  - literal: \"Test Author\"",
                "  title: \"Test Title\"",
                "  issued: 2020-10-14",
                "  url: http://example.com",
                "---");

        assertEquals(expected, Files.readAllLines(file));
    }

    @Test
    void passesModifiedCharset(@TempDir Path tempFile) throws IOException, SaveException, ParserConfigurationException, TransformerException {
        BibEntry entry = new BibEntry(StandardEntryType.Article)
            .withCitationKey("test")
            .withField(StandardField.AUTHOR, "谷崎 潤一郎")
            .withField(StandardField.TITLE, "細雪")
            .withField(StandardField.URL, "http://example.com")
            .withField(StandardField.DATE, "2020-10-14");

        Path file = tempFile.resolve("RandomFileName");
        Files.createFile(file);
        yamlExporter.export(databaseContext, file, List.of(entry));

        List<String> expected = List.of(
                "---",
                "references:",
                "- id: test",
                "  type: article",
                "  author:",
                "  - literal: \"谷崎 潤一郎\"",
                "  title: \"細雪\"",
                "  issued: 2020-10-14",
                "  url: http://example.com",
                "---");

        assertEquals(expected, Files.readAllLines(file));
    }

    @Test
    void passesModifiedCharsetNull(@TempDir Path tempFile) throws IOException, SaveException, ParserConfigurationException, TransformerException {
        BibEntry entry = new BibEntry(StandardEntryType.Article)
            .withCitationKey("test")
            .withField(StandardField.AUTHOR, "谷崎 潤一郎")
            .withField(StandardField.TITLE, "細雪")
            .withField(StandardField.URL, "http://example.com")
            .withField(StandardField.DATE, "2020-10-14");

        Path file = tempFile.resolve("RandomFileName");
        Files.createFile(file);
        yamlExporter.export(databaseContext, file, List.of(entry));

        List<String> expected = List.of(
                "---",
                "references:",
                "- id: test",
                "  type: article",
                "  author:",
                "  - literal: \"谷崎 潤一郎\"",
                "  title: \"細雪\"",
                "  issued: 2020-10-14",
                "  url: http://example.com",
                "---");
        assertEquals(expected, Files.readAllLines(file));
    }
}
