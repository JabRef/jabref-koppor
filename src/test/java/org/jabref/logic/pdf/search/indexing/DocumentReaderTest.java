package org.jabref.logic.pdf.search.indexing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.LinkedFile;
import org.jabref.preferences.FilePreferences;

import org.apache.lucene.document.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DocumentReaderTest {

    private BibDatabaseContext databaseContext;
    private FilePreferences filePreferences;

    @BeforeEach
    public void setup() {
        this.databaseContext = mock(BibDatabaseContext.class);
        when(databaseContext.getFileDirectories(Mockito.any())).thenReturn(Collections.singletonList(Path.of("src/test/resources/pdfs")));
        this.filePreferences = mock(FilePreferences.class);
        when(filePreferences.getUser()).thenReturn("test");
        when(filePreferences.getFileDirectory()).thenReturn(Optional.empty());
        when(filePreferences.shouldStoreFilesRelativeToBib()).thenReturn(true);
    }

    @Test
    public void unknownFileTestShouldReturnEmptyList() throws IOException {
        // given
        BibEntry entry = new BibEntry();
        entry.setFiles(Collections.singletonList(new LinkedFile("Wrong path", "NOT_PRESENT.pdf", "Type")));

        // when
        final List<Document> emptyDocumentList = new DocumentReader(entry, filePreferences).readLinkedPdfs(databaseContext);

        // then
        assertEquals(Collections.emptyList(), emptyDocumentList);
    }
}
