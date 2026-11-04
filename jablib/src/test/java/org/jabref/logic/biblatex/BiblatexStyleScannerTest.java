package org.jabref.logic.biblatex;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BiblatexStyleScannerTest {

    private static final Function<String, Optional<Path>> NOT_INSTALLED = _ -> Optional.empty();

    @Test
    void fieldsOfStyleSiblingAndRequiredStylesAreCollected(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("base.bbx"), """
                \\printlist{location}
                \\RequireBibliographyStyle{my}
                """);
        Path style = Files.writeString(tempDir.resolve("my.bbx"), """
                \\RequireBibliographyStyle{base}
                \\printfield[titlecase]{journaltitle}
                \\printnames[family-given][-\\value{listtotal}]{author}
                % \\printfield{note}
                \\iffieldundef{volume}{}{
                  \\printfield{volume}
                }
                \\printdate
                \\printurldate
                \\DeclareFieldFormat{pages}{#1}
                """);
        Files.writeString(tempDir.resolve("my.cbx"), """
                \\printnames{labelname}
                \\printfield{labelyear}
                """);

        assertEquals(Set.of("location", "journaltitle", "author", "volume", "date", "urldate", "editor", "translator", "year"),
                BiblatexStyleScanner.scanFields(style, NOT_INSTALLED));
    }

    @Test
    void citationStyleScansItsBibliographyStyle(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("my.bbx"), "\\printfield{title}");
        Path citationStyle = Files.writeString(tempDir.resolve("my.cbx"), "\\printfield{shorthand}");

        assertEquals(Set.of("title", "shorthand"), BiblatexStyleScanner.scanFields(citationStyle, NOT_INSTALLED));
    }

    @Test
    void missingRequiredStyleIsSkipped(@TempDir Path tempDir) throws Exception {
        Path style = Files.writeString(tempDir.resolve("my.bbx"), "\\RequireBibliographyStyle{does-not-exist-anywhere}\n\\printfield{title}");

        assertEquals(Set.of("title"), BiblatexStyleScanner.scanFields(style, NOT_INSTALLED));
    }
}
