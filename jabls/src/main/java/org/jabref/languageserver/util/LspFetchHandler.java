package org.jabref.languageserver.util;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.jabref.logic.bibtex.BibEntryWriter;
import org.jabref.logic.bibtex.FieldWriter;
import org.jabref.logic.citationkeypattern.CitationKeyGenerator;
import org.jabref.logic.importer.CompositeIdFetcher;
import org.jabref.logic.importer.FetcherException;
import org.jabref.logic.importer.ParserResult;
import org.jabref.logic.l10n.Localization;
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
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Shows a DOI, arXiv ID, ISBN, ... under the cursor as citation on hover, and offers "Add entry for <identifier> to <file>.bib" for it.
/// The fetch for the action happens in [#resolveCodeAction(CodeAction,Map)], because clients request code actions on every cursor move.
// [impl->feat~fetchers.language-server-code-action~1]
@NullMarked
public class LspFetchHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LspFetchHandler.class);

    // Markup around a pasted identifier, e.g. `<https://doi.org/...>` in Markdown or a trailing full stop
    private static final String LEADING_MARKUP = "<([{\"'";
    private static final String TRAILING_MARKUP = ">)]}\"'.,;:";

    private final CliPreferences preferences;
    private final BibEntryTypesManager entryTypesManager;
    private final LspParserHandler parserHandler;
    private final LspCitationRenderer citationRenderer;
    private final Map<String, BibEntry> fetchedEntries = new ConcurrentHashMap<>();

    public LspFetchHandler(CliPreferences preferences, BibEntryTypesManager entryTypesManager, LspParserHandler parserHandler) {
        this.preferences = preferences;
        this.entryTypesManager = entryTypesManager;
        this.parserHandler = parserHandler;
        this.citationRenderer = new LspCitationRenderer(preferences, entryTypesManager);
    }

    /// @param bibContents content of the open `.bib` documents, keyed by URI; these are the files an entry can be added to
    public List<Either<Command, CodeAction>> provideCodeActions(String content, Position position, Map<String, String> bibContents) {
        return tokenAt(content, LspRangeUtil.toOffset(content, position))
                .flatMap(Identifier::from)
                .map(identifier -> bibContents.keySet().stream().sorted().map(bibUri -> {
                    CodeAction action = new CodeAction(Localization.lang("Add entry for %0 to %1", identifier.asString(), fileName(bibUri)));
                    action.setKind(CodeActionKind.Refactor);
                    JsonObject data = new JsonObject();
                    data.addProperty("identifier", identifier.asString());
                    data.addProperty("bibUri", bibUri);
                    action.setData(data);
                    return Either.<Command, CodeAction>forRight(action);
                }).toList())
                .orElse(List.of());
    }

    public CompletableFuture<CodeAction> resolveCodeAction(CodeAction action, Map<String, String> bibContents) {
        if (!(action.getData() instanceof JsonObject data) || !data.has("identifier") || !data.has("bibUri")) {
            return CompletableFuture.completedFuture(action);
        }
        String identifier = data.get("identifier").getAsString();
        String bibUri = data.get("bibUri").getAsString();
        return CompletableFuture.supplyAsync(() -> {
            String bibContent = Optional.ofNullable(bibContents.get(bibUri))
                                        .orElseThrow(() -> error(Localization.lang("%0 is not open anymore.", fileName(bibUri))));
            BibEntry entry = new BibEntry(fetch(identifier));
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
                throw error(Localization.lang("Could not write entry: %0", e.getLocalizedMessage()));
            }
            Position end = LspRangeUtil.convertToLspPosition(bibContent, bibContent.length());
            String separator = bibContent.isEmpty() || bibContent.endsWith("\n") ? "" : "\n";
            action.setEdit(new WorkspaceEdit(Map.of(bibUri, List.of(new TextEdit(new Range(end, end), separator + serialized)))));
            return action;
        });
    }

    /// Shows the identifier under the cursor as a full citation. Failures stay silent: a hover is no place for error messages.
    public CompletableFuture<@Nullable Hover> provideHover(String content, Position position) {
        Optional<Identifier> identifier = tokenAt(content, LspRangeUtil.toOffset(content, position)).flatMap(Identifier::from);
        return identifier.map(id -> CompletableFuture.supplyAsync(() -> {
            try {
                String citation = citationRenderer.render(fetch(id.asString()));
                return (@Nullable Hover) new Hover(new MarkupContent(MarkupKind.MARKDOWN, citation));
            } catch (CompletionException e) {
                LOGGER.debug("No hover for {}", id.asString(), e);
                return null;
            }
        })).orElseGet(() -> CompletableFuture.completedFuture(null));
    }

    /// Hovering and then adding the entry, or hovering repeatedly, must not hit the fetcher again. The returned entry is shared; copy it before modifying.
    /// Failures are not cached, so a later hover retries.
    private BibEntry fetch(String identifier) {
        // ponytail: the network call runs inside computeIfAbsent, so concurrent requests for the same identifier wait for one fetch; unbounded, entries are small
        return fetchedEntries.computeIfAbsent(identifier, this::fetchUncached);
    }

    private BibEntry fetchUncached(String identifier) {
        try {
            return new CompositeIdFetcher(preferences.getImportFormatPreferences()).performSearchById(identifier)
                                                                                   .orElseThrow(() -> error(Localization.lang("Nothing found for %0.", identifier)));
        } catch (FetcherException e) {
            // The message ends with the raw HTTP response body (often a whole HTML page), which is useless in an editor notification
            String reason = Optional.ofNullable(e.getLocalizedMessage()).map(message -> message.lines().limit(2).collect(Collectors.joining(" "))).orElse("");
            throw error(Localization.lang("Could not fetch %0: %1", identifier, reason));
        }
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
