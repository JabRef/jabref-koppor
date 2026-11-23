package org.jabref.languageserver;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.jabref.languageserver.util.LspDiagnosticHandler;
import org.jabref.languageserver.util.LspFetchHandler;
import org.jabref.languageserver.util.LspLinkHandler;
import org.jabref.logic.remote.server.RemoteMessageHandler;

import com.google.gson.JsonArray;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.DocumentLinkParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BibtexTextDocumentService implements TextDocumentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BibtexTextDocumentService.class);

    private final LspClientHandler clientHandler;
    private final LspDiagnosticHandler diagnosticHandler;
    private final LspLinkHandler linkHandler;
    private final LspFetchHandler fetchHandler;
    private final RemoteMessageHandler messageHandler;
    private final Map<String, String> fileUriToLanguageId;
    private final Map<String, String> contentCache;
    private LanguageClient client;

    public BibtexTextDocumentService(@NonNull RemoteMessageHandler messageHandler, @NonNull LspClientHandler clientHandler, @NonNull LspDiagnosticHandler diagnosticHandler, @NonNull LspLinkHandler linkHandler, @NonNull LspFetchHandler fetchHandler) {
        this.clientHandler = clientHandler;
        this.diagnosticHandler = diagnosticHandler;
        this.linkHandler = linkHandler;
        this.fetchHandler = fetchHandler;
        this.fileUriToLanguageId = new ConcurrentHashMap<>();
        this.contentCache = new ConcurrentHashMap<>();
        this.messageHandler = messageHandler;
    }

    public void setClient(LanguageClient client) {
        this.client = client;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        TextDocumentItem textDocument = params.getTextDocument();
        LOGGER.debug("didOpen {}", textDocument.getUri());
        fileUriToLanguageId.putIfAbsent(textDocument.getUri(), textDocument.getLanguageId());

        if ("bibtex".equals(textDocument.getLanguageId())) {
            diagnosticHandler.computeAndPublishDiagnostics(client, textDocument.getUri(), textDocument.getText(), textDocument.getVersion());
        }
        contentCache.put(textDocument.getUri(), textDocument.getText());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        VersionedTextDocumentIdentifier textDocument = params.getTextDocument();
        TextDocumentContentChangeEvent contentChange = params.getContentChanges().getFirst();
        LOGGER.debug("didChange {}", textDocument.getUri());
        String languageId = fileUriToLanguageId.get(textDocument.getUri());

        if ("bibtex".equalsIgnoreCase(languageId)) {
            diagnosticHandler.computeAndPublishDiagnostics(client, textDocument.getUri(), contentChange.getText(), textDocument.getVersion());
        }
        contentCache.put(textDocument.getUri(), contentChange.getText());
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        fileUriToLanguageId.remove(params.getTextDocument().getUri());
        contentCache.remove(params.getTextDocument().getUri());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
        if (fileUriToLanguageId.containsKey(params.getTextDocument().getUri())) {
            String fileUri = params.getTextDocument().getUri();
            return linkHandler.provideDefinition(fileUriToLanguageId.get(fileUri), fileUri, contentCache.get(fileUri), params.getPosition());
        }
        return CompletableFuture.completedFuture(Either.forLeft(List.of()));
    }

    @Override
    public CompletableFuture<List<DocumentLink>> documentLink(DocumentLinkParams params) {
        String fileUri = params.getTextDocument().getUri();
        return linkHandler.provideDocumentLinks(fileUri, fileUriToLanguageId.get(fileUri), contentCache.get(fileUri));
    }

    @Override
    public CompletableFuture<DocumentLink> documentLinkResolve(DocumentLink params) {
        if (clientHandler.isStandalone()) {
            return CompletableFuture.completedFuture(null);
        }
        if (params.getData() instanceof JsonArray data) {
            messageHandler.handleCommandLineArguments(new String[] {data.asList().getFirst().getAsString(), data.asList().getLast().getAsString()});
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position) {
        return TextDocumentService.super.completion(position);
    }

    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        return CompletableFuture.completedFuture(Optional.ofNullable(contentCache.get(params.getTextDocument().getUri()))
                                                         .map(content -> fetchHandler.provideCodeActions(content, params.getRange().getStart(), openBibContents()))
                                                         .orElse(List.of()));
    }

    @Override
    public CompletableFuture<@Nullable Hover> hover(HoverParams params) {
        return Optional.ofNullable(contentCache.get(params.getTextDocument().getUri()))
                       .map(content -> fetchHandler.provideHover(content, params.getPosition()))
                       .orElseGet(() -> CompletableFuture.completedFuture(null));
    }

    @Override
    public CompletableFuture<CodeAction> resolveCodeAction(CodeAction unresolved) {
        return fetchHandler.resolveCodeAction(unresolved, openBibContents());
    }

    private Map<String, String> openBibContents() {
        return contentCache.entrySet().stream()
                           .filter(entry -> "bibtex".equalsIgnoreCase(fileUriToLanguageId.get(entry.getKey())))
                           .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
