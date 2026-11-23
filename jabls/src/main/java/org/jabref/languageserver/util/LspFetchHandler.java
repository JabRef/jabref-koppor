package org.jabref.languageserver.util;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import org.jabref.logic.bibtex.BibEntryWriter;
import org.jabref.logic.bibtex.FieldWriter;
import org.jabref.logic.citationkeypattern.CitationKeyGenerator;
import org.jabref.logic.importer.CompositeIdFetcher;
import org.jabref.logic.importer.FetcherException;
import org.jabref.logic.importer.ParserResult;
import org.jabref.logic.preferences.CliPreferences;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.database.BibDatabaseMode;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.BibEntryTypesManager;
import org.jabref.model.entry.identifier.Identifier;

import com.google.gson.JsonObject;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

/// Offers "Add entry for <identifier> to <file>.bib" for a DOI, arXiv ID, ISBN, ... under the cursor.
/// The fetch itself happens in [#resolveCodeAction(CodeAction,Map)], because clients request code actions on every cursor move.
public class LspFetchHandler {

    // Markup around a pasted identifier, e.g. `<https://doi.org/...>` in Markdown or a trailing full stop
    private static final String LEADING_MARKUP = "<([{\"'";
    private static final String TRAILING_MARKUP = ">)]}\"'.,;:";

    private final CliPreferences preferences;
    private final BibEntryTypesManager entryTypesManager;
    private final LspParserHandler parserHandler;

    public LspFetchHandler(CliPreferences preferences, BibEntryTypesManager entryTypesManager, LspParserHandler parserHandler) {
        this.preferences = preferences;
        this.entryTypesManager = entryTypesManager;
        this.parserHandler = parserHandler;
    }

    /// @param bibContents content of the open `.bib` documents, keyed by URI; these are the files an entry can be added to
    public List<Either<Command, CodeAction>> provideCodeActions(String content, Position position, Map<String, String> bibContents) {
        Optional<Identifier> identifier = tokenAt(content, LspRangeUtil.toOffset(content, position)).flatMap(Identifier::from);
        if (identifier.isEmpty()) {
            return List.of();
        }
        return bibContents.keySet().stream().sorted().map(bibUri -> {
            CodeAction action = new CodeAction("Add entry for %s to %s".formatted(identifier.get().asString(), fileName(bibUri)));
            action.setKind(CodeActionKind.Refactor);
            JsonObject data = new JsonObject();
            data.addProperty("identifier", identifier.get().asString());
            data.addProperty("bibUri", bibUri);
            action.setData(data);
            return Either.<Command, CodeAction>forRight(action);
        }).toList();
    }

    public CompletableFuture<CodeAction> resolveCodeAction(CodeAction action, Map<String, String> bibContents) {
        if (!(action.getData() instanceof JsonObject data) || !data.has("identifier") || !data.has("bibUri")) {
            return CompletableFuture.completedFuture(action);
        }
        String identifier = data.get("identifier").getAsString();
        String bibUri = data.get("bibUri").getAsString();
        return CompletableFuture.supplyAsync(() -> {
            String bibContent = bibContents.get(bibUri);
            if (bibContent == null) {
                throw error("%s is not open anymore".formatted(fileName(bibUri)));
            }
            BibEntry entry;
            try {
                entry = new CompositeIdFetcher(preferences.getImportFormatPreferences()).performSearchById(identifier)
                                                                                        .orElseThrow(() -> error("Nothing found for " + identifier));
            } catch (FetcherException e) {
                // The message ends with the raw HTTP response body (often a whole HTML page), which is useless in an editor notification
                String reason = e.getLocalizedMessage().lines().limit(2).collect(Collectors.joining(" "));
                throw error("Could not fetch %s: %s".formatted(identifier, reason));
            }
            // Generate the key against the target library, so that it does not clash with an existing key
            BibDatabaseContext targetContext = parserHandler.getParserResultForUri(bibUri)
                                                            .map(ParserResult::getDatabaseContext)
                                                            .orElseGet(BibDatabaseContext::new);
            new CitationKeyGenerator(targetContext, preferences.getCitationKeyPatternPreferences()).generateAndSetKey(entry);

            String serialized;
            try {
                serialized = new BibEntryWriter(new FieldWriter(preferences.getFieldPreferences()), entryTypesManager)
                        .write(List.of(entry), BibDatabaseMode.BIBTEX);
            } catch (IOException e) {
                throw error("Could not write entry: " + e.getLocalizedMessage());
            }
            Position end = LspRangeUtil.convertToLspPosition(bibContent, bibContent.length());
            String separator = bibContent.isEmpty() || bibContent.endsWith("\n") ? "" : "\n";
            action.setEdit(new WorkspaceEdit(Map.of(bibUri, List.of(new TextEdit(new Range(end, end), separator + serialized)))));
            return action;
        });
    }

    /// Returns the whitespace-delimited word at `offset`, without surrounding markup.
    static Optional<String> tokenAt(String content, int offset) {
        int start = offset;
        while (start > 0 && !Character.isWhitespace(content.charAt(start - 1))) {
            start--;
        }
        int end = offset;
        while (end < content.length() && !Character.isWhitespace(content.charAt(end))) {
            end++;
        }
        while (start < end && LEADING_MARKUP.indexOf(content.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && TRAILING_MARKUP.indexOf(content.charAt(end - 1)) >= 0) {
            end--;
        }
        return start < end ? Optional.of(content.substring(start, end)) : Optional.empty();
    }

    private static String fileName(String uri) {
        return uri.substring(uri.lastIndexOf('/') + 1);
    }

    private static CompletionException error(String message) {
        return new CompletionException(new ResponseErrorException(new ResponseError(ResponseErrorCode.RequestFailed, message, null)));
    }
}
