package org.jabref.toolkit.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jabref.logic.externalfiles.FulltextDownloader;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.LinkedFile;
import org.jabref.toolkit.util.CapturingCommandLine;
import org.jabref.toolkit.util.CommandFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetFulltextTest extends AbstractJabKitTest {

    @TempDir
    private Path tempDir;

    private final FulltextDownloader downloader = mock(FulltextDownloader.class);

    @BeforeEach
    void setupDownloader() {
        GetFulltext sut = new GetFulltext() {
            @Override
            void initFields() {
                this.fulltextDownloader = downloader;
            }
        };
        commandLine = new CapturingCommandLine(new JabKit(preferences, entryTypesManager), new CommandFactory(sut));
    }

    @Test
    void updatesLibraryInPlaceAndReportsEachEntry() throws IOException {
        Path library = tempDir.resolve("library.bib");
        Files.writeString(library, """
                @Article{Found,
                }
                @Article{Missing,
                }
                @Article{HasPdf,
                  file = {:HasPdf.pdf:PDF},
                }
                """);
        Path downloadedFile = tempDir.resolve("Found.pdf");
        when(downloader.download(any(), any())).thenAnswer(invocation -> {
            BibEntry entry = invocation.getArgument(1);
            if (entry.getCitationKey().orElseThrow().equals("Found")) {
                entry.addFile(new LinkedFile("", Path.of("Found.pdf"), "PDF"));
                return new FulltextDownloader.Result.Downloaded(downloadedFile);
            }
            return new FulltextDownloader.Result.NotFound();
        });

        int exitCode = commandLine.executeToLog("get-fulltext", library.toString());

        assertEquals(CommandLine.ExitCode.OK, exitCode);
        String output = commandLine.getStandardOutput();
        assertTrue(output.contains("Downloaded full text document for entry Found: " + downloadedFile), output);
        assertTrue(output.contains("No full text document found for entry Missing."), output);
        assertTrue(output.contains("Full text document for entry HasPdf already linked."), output);
        assertTrue(output.contains("Full text documents: 1 downloaded, 1 not found, 1 skipped, 0 failed."), output);
        verify(downloader, times(2)).download(any(), any());
        assertTrue(Files.readString(library).contains(":Found.pdf:PDF"), Files.readString(library));
    }
}
