package org.jabref.languageserver.util;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jabref.logic.bibtex.BibEntryWriter;
import org.jabref.logic.bibtex.FieldWriter;
import org.jabref.logic.citationkeypattern.CitationKeyGenerator;
import org.jabref.logic.importer.CompositeIdFetcher;
import org.jabref.logic.importer.FetcherException;
import org.jabref.logic.importer.ParserResult;
import org.jabref.logic.importer.fileformat.BibtexParser;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.preferences.CliPreferences;
import org.jabref.logic.util.io.FileUtil;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.database.BibDatabaseMode;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.BibEntryTypesManager;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.identifier.Identifier;

import com.google.gson.JsonObject;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CreateFile;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
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
    public List<Either<Command, CodeAction>> provideCodeActions(String uri, String languageId, String content, Position position, Map<String, String> bibContents) {
        int[] range = tokenRange(content, LspRangeUtil.toOffset(content, position));
        String token = content.substring(range[0], range[1]);
        Optional<Identifier> identifier = Identifier.from(token);
        if (token.isEmpty() || identifier.isEmpty()) {
            return List.of();
        }
        String id = identifier.get().asString();
        List<Either<Command, CodeAction>> actions = new ArrayList<>();
        // The citation is Markdown; in LaTeX, `\[1\]` would start display math
        if ("markdown".equals(languageId)) {
            JsonObject data = new JsonObject();
            data.addProperty("identifier", id);
            data.addProperty("replaceUri", uri);
            data.addProperty("token", token);
            data.addProperty("start", range[0]);
            data.addProperty("end", range[1]);
            actions.add(action(Localization.lang("Replace %0 with citation", id), CodeActionKind.RefactorRewrite, data));
            Optional<Path> bibPath = citationTarget(uri, content);
            if (bibPath.isPresent()) {
                JsonObject citeData = data.deepCopy();
                citeData.remove("replaceUri");
                citeData.addProperty("citeUri", uri);
                actions.add(action(Localization.lang("Cite %0 from %1", id, bibPath.get().getFileName().toString()), CodeActionKind.RefactorRewrite, citeData));
            }
            // Citing covers adding the entry
            return actions;
        }
        bibContents.keySet().stream().sorted().forEach(bibUri -> {
            JsonObject data = new JsonObject();
            data.addProperty("identifier", id);
            data.addProperty("bibUri", bibUri);
            actions.add(action(Localization.lang("Add entry for %0 to %1", id, fileName(bibUri)), CodeActionKind.Refactor, data));
        });
        return actions;
    }

    private static Either<Command, CodeAction> action(String title, String kind, JsonObject data) {
        CodeAction action = new CodeAction(title);
        action.setKind(kind);
        action.setData(data);
        return Either.forRight(action);
    }

    /// @param contents content of the open documents, keyed by URI
    public CompletableFuture<CodeAction> resolveCodeAction(CodeAction action, Map<String, String> contents) {
        if (action.getData() instanceof JsonObject data && data.has("identifier") && data.has("replaceUri")) {
            return resolveReplace(action, data, contents);
        }
        if (action.getData() instanceof JsonObject data && data.has("identifier") && data.has("citeUri")) {
            return resolveCite(action, data, contents);
        }
        if (!(action.getData() instanceof JsonObject data) || !data.has("identifier") || !data.has("bibUri")) {
            return CompletableFuture.completedFuture(action);
        }
        String identifier = data.get("identifier").getAsString();
        String bibUri = data.get("bibUri").getAsString();
        return CompletableFuture.supplyAsync(() -> {
            String bibContent = Optional.ofNullable(contents.get(bibUri))
                                        .orElseThrow(() -> error(Localization.lang("%0 is not open anymore.", fileName(bibUri))));
            BibEntry entry = new BibEntry(fetch(identifier));
            BibDatabaseContext targetContext = parserHandler.getParserResultForUri(bibUri)
                                                            .map(ParserResult::getDatabaseContext)
                                                            .orElseGet(BibDatabaseContext::new);
            action.setEdit(new WorkspaceEdit(Map.of(bibUri, List.of(append(bibContent, keyedSerialization(entry, targetContext))))));
            return action;
        });
    }

    // [impl->feat~fetchers.language-server-cite~1]
    private CompletableFuture<CodeAction> resolveCite(CodeAction action, JsonObject data, Map<String, String> contents) {
        String identifier = data.get("identifier").getAsString();
        String uri = data.get("citeUri").getAsString();
        String token = data.get("token").getAsString();
        int start = data.get("start").getAsInt();
        int end = data.get("end").getAsInt();
        return CompletableFuture.supplyAsync(() -> {
            String content = Optional.ofNullable(contents.get(uri))
                                     .orElseThrow(() -> error(Localization.lang("%0 is not open anymore.", fileName(uri))));
            if (end > content.length() || !content.substring(start, end).equals(token)) {
                throw error(Localization.lang("%0 was changed in the meantime.", fileName(uri)));
            }
            Path bibPath = citationTarget(uri, content).orElseThrow(() -> error(Localization.lang("%0 is not open anymore.", fileName(uri))));
            // An open library may have unsaved changes; take the editor's content then
            Optional<String> openBibUri = contents.keySet().stream().filter(key -> toPath(key).filter(bibPath::equals).isPresent()).findFirst();
            String bibUri = openBibUri.orElseGet(() -> bibPath.toUri().toString());
            @Nullable String bibContent = openBibUri.map(contents::get).orElseGet(() -> readIfExists(bibPath));

            BibEntry fetched = fetch(identifier);
            BibDatabaseContext targetContext = parse(bibContent == null ? "" : bibContent);
            List<Either<TextDocumentEdit, ResourceOperation>> changes = new ArrayList<>();
            String key = existingKey(fetched, targetContext).orElseGet(() -> {
                BibEntry entry = new BibEntry(fetched);
                String serialized = keyedSerialization(entry, targetContext);
                if (bibContent == null) {
                    changes.add(Either.forRight(new CreateFile(bibUri)));
                }
                changes.add(documentEdit(bibUri, append(bibContent == null ? "" : bibContent, serialized)));
                return entry.getCitationKey().orElseThrow();
            });
            Range range = new Range(LspRangeUtil.convertToLspPosition(content, start), LspRangeUtil.convertToLspPosition(content, end));
            changes.add(documentEdit(uri, new TextEdit(range, "[@" + key + "]")));
            action.setEdit(new WorkspaceEdit(changes));
            return action;
        });
    }

    /// The library a Markdown document cites from: the first `bibliography` of its front matter, otherwise the sibling `.bib` file
    static Optional<Path> citationTarget(String markdownUri, String content) {
        return toPath(markdownUri).map(markdownPath -> LspParserHandler.getBibliographiesFromFrontMatter(content).stream()
                                                                       .findFirst()
                                                                       .map(markdownPath::resolveSibling)
                                                                       .orElseGet(() -> markdownPath.resolveSibling(FileUtil.getBaseName(markdownPath.getFileName().toString()) + ".bib"))
                                                                       .normalize());
    }

    /// The key of an entry of the library describing the same work, so that citing it again does not add a duplicate
    private static Optional<String> existingKey(BibEntry fetched, BibDatabaseContext library) {
        return Stream.of(StandardField.DOI, StandardField.EPRINT, StandardField.ISBN)
                     .flatMap(field -> fetched.getField(field).stream().flatMap(value -> library.getEntries().stream()
                                                                                       .filter(entry -> entry.getField(field).filter(value::equalsIgnoreCase).isPresent())))
                     .flatMap(entry -> entry.getCitationKey().stream())
                     .findFirst();
    }

    /// Generates the key against the target library, so that it does not clash with an existing key, and serializes the entry
    private String keyedSerialization(BibEntry entry, BibDatabaseContext targetContext) {
        new CitationKeyGenerator(targetContext, preferences.getCitationKeyPatternPreferences()).generateAndSetKey(entry);
        try {
            return new BibEntryWriter(new FieldWriter(preferences.getFieldPreferences()), entryTypesManager)
                    .write(List.of(entry), BibDatabaseMode.BIBTEX);
        } catch (IOException e) {
            throw error(Localization.lang("Could not write entry: %0", e.getLocalizedMessage()));
        }
    }

    private static Either<TextDocumentEdit, ResourceOperation> documentEdit(String uri, TextEdit edit) {
        return Either.forLeft(new TextDocumentEdit(new VersionedTextDocumentIdentifier(uri, null), List.of(Either.forLeft(edit))));
    }

    private static TextEdit append(String content, String text) {
        Position end = LspRangeUtil.convertToLspPosition(content, content.length());
        String separator = content.isEmpty() || content.endsWith("\n") ? "" : "\n";
        return new TextEdit(new Range(end, end), separator + text);
    }

    private BibDatabaseContext parse(String bibContent) {
        try {
            return new BibtexParser(preferences.getImportFormatPreferences()).parse(Reader.of(bibContent)).getDatabaseContext();
        } catch (IOException e) {
            throw error(Localization.lang("Could not write entry: %0", e.getLocalizedMessage()));
        }
    }

    private static @Nullable String readIfExists(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path) : null;
        } catch (IOException e) {
            throw error(e.getLocalizedMessage());
        }
    }

    private static Optional<Path> toPath(String uri) {
        try {
            return Optional.of(Path.of(new URI(uri)).normalize());
        } catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException e) {
            return Optional.empty();
        }
    }

    // [impl->feat~fetchers.language-server-replace-citation~1]
    private CompletableFuture<CodeAction> resolveReplace(CodeAction action, JsonObject data, Map<String, String> contents) {
        String identifier = data.get("identifier").getAsString();
        String uri = data.get("replaceUri").getAsString();
        String token = data.get("token").getAsString();
        int start = data.get("start").getAsInt();
        int end = data.get("end").getAsInt();
        return CompletableFuture.supplyAsync(() -> {
            String content = Optional.ofNullable(contents.get(uri))
                                     .orElseThrow(() -> error(Localization.lang("%0 is not open anymore.", fileName(uri))));
            if (end > content.length() || !content.substring(start, end).equals(token)) {
                throw error(Localization.lang("%0 was changed in the meantime.", fileName(uri)));
            }
            String rendered = citationRenderer.renderInline(fetch(identifier));
            if (rendered.isBlank()) {
                throw error(Localization.lang("Could not find any bibliographic information."));
            }
            // The sentence's full stop stays
            String citation = rendered.endsWith(".") && content.startsWith(".", end) ? rendered.substring(0, rendered.length() - 1) : rendered;
            Range range = new Range(LspRangeUtil.convertToLspPosition(content, start), LspRangeUtil.convertToLspPosition(content, end));
            action.setEdit(new WorkspaceEdit(Map.of(uri, List.of(new TextEdit(range, citation)))));
            return action;
        });
    }

    /// Shows the identifier under the cursor as a full citation. Failures stay silent: a hover is no place for error messages.
    public CompletableFuture<@Nullable Hover> provideHover(String content, Position position) {
        Optional<Identifier> identifier = tokenAt(content, LspRangeUtil.toOffset(content, position)).flatMap(Identifier::from);
        return identifier.map(id -> CompletableFuture.supplyAsync(() -> {
            try {
                String citation = citationRenderer.render(fetch(id.asString()));
                if (citation.isBlank()) {
                    LOGGER.debug("No preview style could render {}", id.asString());
                    return null;
                }
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
                                                                                   .orElseThrow(() -> error(Localization.lang("Could not find any bibliographic information.")));
        } catch (FetcherException e) {
            // The message ends with the raw HTTP response body (often a whole HTML page), which is useless in an editor notification
            String reason = Optional.ofNullable(e.getLocalizedMessage()).map(message -> message.lines().limit(2).collect(Collectors.joining(" "))).orElse("");
            throw error(Localization.lang("Could not fetch %0: %1", identifier, reason));
        }
    }

    /// Returns the whitespace-delimited word at `offset`, without surrounding markup.
    static Optional<String> tokenAt(String content, int offset) {
        int[] range = tokenRange(content, offset);
        return range[0] < range[1] ? Optional.of(content.substring(range[0], range[1])) : Optional.empty();
    }

    /// Start (inclusive) and end (exclusive) offset of [#tokenAt(String,int)]
    static int[] tokenRange(String content, int offset) {
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
        return new int[] {start, end};
    }

    private static String fileName(String uri) {
        return uri.substring(uri.lastIndexOf('/') + 1);
    }

    private static CompletionException error(String message) {
        return new CompletionException(new ResponseErrorException(new ResponseError(ResponseErrorCode.RequestFailed, message, null)));
    }
}
