package org.jabref.languageserver.util;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jabref.logic.preferences.CliPreferences;
import org.jabref.model.entry.BibEntryTypesManager;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

// [utest->feat~fetchers.language-server-code-action~1]
class LspFetchHandlerTest {

    private final LspFetchHandler handler = new LspFetchHandler(mock(CliPreferences.class), new BibEntryTypesManager(), new LspParserHandler());

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "[A] https://arxiv.org/html/2608.10906v3 | 10 | https://arxiv.org/html/2608.10906v3",
            "see <https://doi.org/10.1000/xyz>.      | 8  | https://doi.org/10.1000/xyz",
            "see (10.1000/xyz).                      | 17 | 10.1000/xyz"
    })
    void tokenAtStripsMarkup(String content, int offset, String expected) {
        assertEquals(Optional.of(expected), LspFetchHandler.tokenAt(content, offset));
    }

    @Test
    void offersOneActionPerOpenBibFile() {
        List<Either<Command, CodeAction>> actions = handler.provideCodeActions(
                "file:///tmp/main.tex", "latex",
                "[A] https://arxiv.org/html/2608.10906v3",
                new Position(0, 15),
                Map.of("file:///tmp/b.bib", "", "file:///tmp/a.bib", ""));

        assertEquals(List.of("Add entry for 2608.10906v3 to a.bib", "Add entry for 2608.10906v3 to b.bib"),
                actions.stream().map(action -> action.getRight().getTitle()).toList());
    }

    @Test
    void offersNothingForPlainText() {
        assertEquals(List.of(), handler.provideCodeActions("file:///tmp/a.md", "markdown", "## References", new Position(0, 5), Map.of("file:///tmp/a.bib", "")));
    }

    // [utest->feat~fetchers.language-server-replace-citation~1]
    @Test
    void offersReplaceWithCitationInMarkdownWithoutBibFile() {
        List<Either<Command, CodeAction>> actions = handler.provideCodeActions(
                "file:///tmp/a.md", "markdown",
                "See https://doi.org/10.1017/s0007114507795296.",
                new Position(0, 10),
                Map.of());

        assertEquals(List.of("Replace 10.1017/s0007114507795296 with citation", "Cite 10.1017/s0007114507795296 from a.bib"),
                actions.stream().map(action -> action.getRight().getTitle()).toList());
    }

    // [utest->feat~fetchers.language-server-cite~1]
    @Test
    void citesFromSiblingBibFileWithoutFrontMatter() {
        assertEquals(Optional.of(Path.of("/tmp/notes/paper.bib")), LspFetchHandler.citationTarget("file:///tmp/notes/paper.md", "# Paper"));
    }

    // [utest->feat~fetchers.language-server-cite~1]
    @Test
    void citesFromFirstFrontMatterBibliography() {
        assertEquals(Optional.of(Path.of("/tmp/refs/main.bib")),
                LspFetchHandler.citationTarget("file:///tmp/notes/paper.md", "---\nbibliography: [../refs/main.bib, other.bib]\n---\n# Paper"));
    }
}
