package org.jabref.logic.importer.plaincitation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class CitationSplitterTest {

    @Test
    void correctlySplitTexts() {
        String part1 = "abc";
        String part2 = "123";
        String part3 = "xyz";

        String input = part1 + "\n\n" + part2 + "\n\n" + part3;

        List<String> output = CitationSplitter.splitCitations(input).toList();

        assertEquals(List.of(part1, part2, part3), output);
    }
}
