package org.jabref.logic.citationkeypattern;

import static org.jabref.logic.citationkeypattern.CitationKeyPatternPreferences.DEFAULT_UNWANTED_CHARACTERS;

import javafx.beans.property.SimpleObjectProperty;

public class CitationKeyGeneratorTestUtils {

    public static CitationKeyPatternPreferences getInstanceForTesting() {
        return new CitationKeyPatternPreferences(
                true,
                false,
                false,
                false,
                CitationKeyPatternPreferences.KeySuffix.SECOND_WITH_A,
                "",
                "",
                DEFAULT_UNWANTED_CHARACTERS,
                GlobalCitationKeyPatterns.fromPattern("[auth][year]"),
                new SimpleObjectProperty<>(','));
    }
}
