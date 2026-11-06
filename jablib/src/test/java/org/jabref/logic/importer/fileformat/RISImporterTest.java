package org.jabref.logic.importer.fileformat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class RISImporterTest {

    private RisImporter importer = new RisImporter();

    @Test
    void ifNotRecognizedFormat() throws Exception {
        Path file = Path.of(RISImporterTest.class.getResource("RisImporterCorrupted.ris").toURI());
        assertFalse(importer.isRecognizedFormat(file));
    }
}
