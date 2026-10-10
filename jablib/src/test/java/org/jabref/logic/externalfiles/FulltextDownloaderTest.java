package org.jabref.logic.externalfiles;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.jabref.logic.FilePreferences;
import org.jabref.logic.importer.FetcherResult;
import org.jabref.logic.importer.fetcher.TrustLevel;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.LinkedFile;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FulltextDownloaderTest {

    private static final byte[] PDF_CONTENT = "%PDF-1.4 fake".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    private Path libraryDirectory;

    private final FilePreferences filePreferences = mock(FilePreferences.class);
    private HttpServer server;
    private URL pdfUrl;
    private BibEntry entry;
    private BibDatabaseContext databaseContext;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/paper.pdf", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, PDF_CONTENT.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(PDF_CONTENT);
            }
        });
        server.start();
        pdfUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/paper.pdf").toURL();

        when(filePreferences.shouldStoreFilesRelativeToBibFile()).thenReturn(true);
        when(filePreferences.getFileNamePattern()).thenReturn("[citationkey]");
        when(filePreferences.getFileDirectoryPattern()).thenReturn("");

        entry = new BibEntry().withCitationKey("Tan_2021");
        databaseContext = new BibDatabaseContext(new BibDatabase(List.of(entry)));
        databaseContext.setDatabasePath(libraryDirectory.resolve("library.bib"));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private List<Path> filesInLibraryDirectory() throws IOException {
        try (Stream<Path> files = Files.list(libraryDirectory)) {
            return files.toList();
        }
    }

    private FulltextDownloader downloaderFinding(Optional<URL> url) {
        return new FulltextDownloader(filePreferences, _ -> url.map(found -> new FetcherResult(TrustLevel.PUBLISHER, found)));
    }

    @Test
    void downloadsAndLinksPdf() throws IOException {
        Path expectedFile = libraryDirectory.resolve("Tan_2021.pdf");

        FulltextDownloader.Result result = downloaderFinding(Optional.of(pdfUrl)).download(databaseContext, entry);

        assertEquals(new FulltextDownloader.Result.Downloaded(expectedFile), result);
        assertEquals(List.of(new LinkedFile("", Path.of("Tan_2021.pdf"), "PDF")), entry.getFiles());
        assertArrayEquals(PDF_CONTENT, Files.readAllBytes(expectedFile));
    }

    @Test
    void identicalExistingFileIsNotDownloadedTwice() throws IOException {
        Files.write(libraryDirectory.resolve("Tan_2021.pdf"), PDF_CONTENT);

        FulltextDownloader.Result result = downloaderFinding(Optional.of(pdfUrl)).download(databaseContext, entry);

        assertEquals(new FulltextDownloader.Result.Duplicate(), result);
        assertEquals(List.of(), entry.getFiles());
        assertEquals(List.of(libraryDirectory.resolve("Tan_2021.pdf")), filesInLibraryDirectory());
    }

    @Test
    void reportsNotFound() {
        FulltextDownloader.Result result = downloaderFinding(Optional.empty()).download(databaseContext, entry);

        assertEquals(new FulltextDownloader.Result.NotFound(), result);
    }

    @Test
    void reportsAlreadyLinkedUrl() {
        entry.addFile(new LinkedFile("", pdfUrl, ""));

        FulltextDownloader.Result result = downloaderFinding(Optional.of(pdfUrl)).download(databaseContext, entry);

        assertEquals(new FulltextDownloader.Result.AlreadyLinked(), result);
    }

    @Test
    void reportsMissingFileDirectory() {
        databaseContext.setDatabasePath(libraryDirectory.resolve("missing").resolve("library.bib"));

        FulltextDownloader.Result result = downloaderFinding(Optional.of(pdfUrl)).download(databaseContext, entry);

        assertEquals(new FulltextDownloader.Result.NoFileDirectory(), result);
    }

    @Test
    void failedDownloadLeavesNoFile() throws IOException {
        URL missing = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/missing.pdf").toURL();

        FulltextDownloader.Result result = downloaderFinding(Optional.of(missing)).download(databaseContext, entry);

        assertEquals(FulltextDownloader.Result.Failed.class, result.getClass());
        assertEquals(List.of(), filesInLibraryDirectory());
    }
}
