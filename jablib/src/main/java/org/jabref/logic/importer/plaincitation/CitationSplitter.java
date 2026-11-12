package org.jabref.logic.importer.plaincitation;

import com.google.common.annotations.VisibleForTesting;
import java.util.Arrays;
import java.util.stream.Stream;

public class CitationSplitter {

    @VisibleForTesting
    static Stream<String> splitCitations(String text) {
        return Arrays.stream(text.split("\\r\\r+|\\n\\n+|\\r\\n(\\r\\n)+"))
                .map(String::trim)
                .filter(str -> !str.isBlank());
    }
}
