package org.jabref.logic.ai.rag.logic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jabref.logic.FilePreferences;
import org.jabref.logic.ai.embedding.MVStoreEmbeddingStore;
import org.jabref.logic.ai.ingestion.logic.EmbeddingsCleaner;
import org.jabref.logic.ai.ingestion.logic.ingestion.FileIngestor;
import org.jabref.logic.ai.ingestion.repositories.IngestedDocumentsRepository;
import org.jabref.logic.ai.ingestion.util.FileHasher;
import org.jabref.logic.ai.preferences.AiPreferences;
import org.jabref.model.ai.pipeline.SemanticSearchMatch;
import org.jabref.model.ai.pipeline.SemanticSearchResult;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.LinkedFile;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.types.StandardEntryType;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@NullMarked
class EmbeddingsSemanticSearchTest {

    private static final Embedding ABOUT_CHOCOLATE = Embedding.from(new float[] {1.0f, 0.0f});
    private static final Embedding ABOUT_HONEY = Embedding.from(new float[] {0.0f, 1.0f});

    @TempDir
    Path tempDir;

    private AiPreferences aiPreferences;
    private IngestedDocumentsRepository ingestedDocumentsRepository;
    private EmbeddingModel embeddingModel;
    private MVStoreEmbeddingStore embeddingStore;
    private List<String> startedIngestions;
    private EmbeddingsSemanticSearch semanticSearch;

    @BeforeEach
    void setUp() {
        aiPreferences = mock(AiPreferences.class);
        when(aiPreferences.getAiFeaturesEnabled()).thenReturn(true);

        FilePreferences filePreferences = mock(FilePreferences.class);
        when(filePreferences.getUserAndHost()).thenReturn("user-host");
        when(filePreferences.shouldStoreFilesRelativeToBibFile()).thenReturn(true);

        ingestedDocumentsRepository = mock(IngestedDocumentsRepository.class);

        embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed("chocolate")).thenReturn(Response.from(ABOUT_CHOCOLATE));

        embeddingStore = new MVStoreEmbeddingStore(tempDir.resolve("embeddings.mv"), _ -> {
        });

        startedIngestions = new ArrayList<>();
        semanticSearch = new EmbeddingsSemanticSearch(
                aiPreferences,
                filePreferences,
                embeddingStore,
                ingestedDocumentsRepository,
                () -> embeddingModel,
                (_, linkedFile) -> startedIngestions.add(linkedFile.getLink()));
    }

    @AfterEach
    void tearDown() {
        embeddingStore.close();
    }

    @Test
    void searchReturnsChunksOfIngestedFilesBestFirst() throws IOException, SemanticSearchNotAvailableException {
        // [utest->req~jabsrv.ai.search~1]
        String fileHash = ingestedFile("cocoa.pdf");
        addChunk(fileHash, 2, "Honey is made by bees.", ABOUT_HONEY);
        addChunk(fileHash, 7, "Chocolate is made from cocoa.", ABOUT_CHOCOLATE);
        BibDatabaseContext databaseContext = library(entry("Cocoa2026", "cocoa.pdf"));

        SemanticSearchResult result = semanticSearch.search(databaseContext, "chocolate", 10, 0.0);

        assertEquals(
                new SemanticSearchResult(
                        List.of(
                                new SemanticSearchMatch("Cocoa2026", "cocoa.pdf", 7, 1.0, "Chocolate is made from cocoa."),
                                new SemanticSearchMatch("Cocoa2026", "cocoa.pdf", 2, 0.5, "Honey is made by bees.")),
                        List.of(),
                        List.of()),
                result);
    }

    @Test
    void searchLeavesOutChunksBelowMinimumScore() throws IOException, SemanticSearchNotAvailableException {
        String fileHash = ingestedFile("cocoa.pdf");
        addChunk(fileHash, 2, "Honey is made by bees.", ABOUT_HONEY);
        addChunk(fileHash, 7, "Chocolate is made from cocoa.", ABOUT_CHOCOLATE);
        BibDatabaseContext databaseContext = library(entry("Cocoa2026", "cocoa.pdf"));

        SemanticSearchResult result = semanticSearch.search(databaseContext, "chocolate", 10, 0.7);

        assertEquals(
                List.of(new SemanticSearchMatch("Cocoa2026", "cocoa.pdf", 7, 1.0, "Chocolate is made from cocoa.")),
                result.matches());
    }

    @Test
    void searchIgnoresChunksOfFilesOfOtherLibraries() throws IOException, SemanticSearchNotAvailableException {
        String fileHash = ingestedFile("cocoa.pdf");
        addChunk(fileHash, 7, "Chocolate is made from cocoa.", ABOUT_CHOCOLATE);
        addChunk("hash-of-a-file-of-another-library", 1, "Chocolate melts on the moon.", ABOUT_CHOCOLATE);
        BibDatabaseContext databaseContext = library(entry("Cocoa2026", "cocoa.pdf"));

        SemanticSearchResult result = semanticSearch.search(databaseContext, "chocolate", 10, 0.0);

        assertEquals(
                List.of(new SemanticSearchMatch("Cocoa2026", "cocoa.pdf", 7, 1.0, "Chocolate is made from cocoa.")),
                result.matches());
    }

    @Test
    void searchReportsFilesNotIngestedAsPendingAndStartsTheirIngestion() throws IOException, SemanticSearchNotAvailableException {
        // [utest->req~jabsrv.ai.search~1]
        Files.writeString(tempDir.resolve("new.pdf"), "new");
        addChunk("hash-of-a-file-of-another-library", 1, "Chocolate melts on the moon.", ABOUT_CHOCOLATE);
        BibDatabaseContext databaseContext = library(entry("New2026", "new.pdf"));

        SemanticSearchResult result = semanticSearch.search(databaseContext, "chocolate", 10, 0.0);

        assertEquals(new SemanticSearchResult(List.of(), List.of("new.pdf"), List.of()), result);
        assertEquals(List.of("new.pdf"), startedIngestions);
    }

    @Test
    void searchReportsMissingFilesAndSkipsOnlineLinks() throws SemanticSearchNotAvailableException {
        BibEntry entry = new BibEntry(StandardEntryType.Article)
                .withCitationKey("Missing2026")
                .withFiles(List.of(
                        new LinkedFile("", "missing.pdf", "PDF"),
                        new LinkedFile("", "https://example.org/paper.pdf", "PDF")));

        SemanticSearchResult result = semanticSearch.search(library(entry), "chocolate", 10, 0.0);

        assertEquals(new SemanticSearchResult(List.of(), List.of(), List.of("missing.pdf")), result);
        assertEquals(List.of(), startedIngestions);
    }

    @Test
    void searchIsNotAvailableIfAiFeaturesAreDisabled() {
        when(aiPreferences.getAiFeaturesEnabled()).thenReturn(false);

        assertThrows(SemanticSearchNotAvailableException.class,
                () -> semanticSearch.search(library(), "chocolate", 10, 0.0));
    }

    private String ingestedFile(String fileName) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, fileName);
        String fileHash = FileHasher.computeHash(file).orElseThrow();
        when(ingestedDocumentsRepository.isDocumentIngested(fileHash)).thenReturn(true);
        return fileHash;
    }

    private void addChunk(String fileHash, int pageNumber, String text, Embedding embedding) {
        Metadata metadata = new Metadata(Map.of(
                EmbeddingsCleaner.FILE_HASH_METADATA_KEY, fileHash,
                FileIngestor.PAGE_NUMBER_METADATA_KEY, pageNumber));
        embeddingStore.add(embedding, new TextSegment(text, metadata));
    }

    private static BibEntry entry(String citationKey, String fileName) {
        return new BibEntry(StandardEntryType.Article)
                .withCitationKey(citationKey)
                .withField(StandardField.TITLE, citationKey)
                .withFiles(List.of(new LinkedFile("", fileName, "PDF")));
    }

    private BibDatabaseContext library(BibEntry... entries) {
        BibDatabaseContext databaseContext = new BibDatabaseContext(new BibDatabase(List.of(entries)));
        databaseContext.setDatabasePath(tempDir.resolve("library.bib"));
        return databaseContext;
    }
}
