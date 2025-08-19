package org.jabref.logic.importer.fileformat;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RISImporterTest {

    private RisImporter importer = new RisImporter();

    @Test
    void ifNotRecognizedFormat() throws IOException, URISyntaxException {
        Path file = Path.of(
            RISImporterTest.class.getResource(
                "RisImporterCorrupted.ris"
            ).toURI()
        );
        assertFalse(importer.isRecognizedFormat(file));
    }
}
