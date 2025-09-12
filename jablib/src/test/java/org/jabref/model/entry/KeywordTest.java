package org.jabref.model.entry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KeywordTest {

    @Test
    void getPathFromRootAsStringForSimpleChain() {
        Keyword keywordChain = Keyword.of("A", "B", "C");
        assertEquals(
            "A > B",
            keywordChain.getChild().get().getPathFromRootAsString('>')
        );
    }

    @Test
    void getAllSubchainsAsStringForSimpleChain() {
        Keyword keywordChain = Keyword.of("A", "B", "C");
        Set<String> expected = new HashSet<>();
        expected.add("A");
        expected.add("A > B");
        expected.add("A > B > C");

        assertEquals(expected, keywordChain.getAllSubchainsAsString('>'));
    }
}
