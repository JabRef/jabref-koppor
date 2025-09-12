package org.jabref.logic.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

public class StandardFileTypeTest {

    @Test
    void recognizeBlgFileType() {
        FileType detected = StandardFileType.fromExtensions("blg");
        assertEquals(StandardFileType.BLG, detected);
    }

    @Test
    void blgFileTypeProperties() {
        assertEquals("BibTeX log file", StandardFileType.BLG.getName());
        assertEquals(List.of("blg"), StandardFileType.BLG.getExtensions());
    }
}
