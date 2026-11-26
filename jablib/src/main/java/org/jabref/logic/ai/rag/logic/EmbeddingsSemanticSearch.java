package org.jabref.logic.ai.rag.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.jabref.logic.FilePreferences;
import org.jabref.logic.ai.AiService;
import org.jabref.logic.ai.embedding.AsyncEmbeddingModel;
import org.jabref.logic.ai.embedding.EmbeddingInputPrefixes;
import org.jabref.logic.ai.ingestion.logic.EmbeddingsCleaner;
import org.jabref.logic.ai.ingestion.logic.ingestion.FileIngestor;
import org.jabref.logic.ai.ingestion.repositories.IngestedDocumentsRepository;
import org.jabref.logic.ai.ingestion.tasks.generateembeddings.GenerateEmbeddingsTaskRequest;
import org.jabref.logic.ai.ingestion.util.FileHasher;
import org.jabref.logic.ai.preferences.AiPreferences;
import org.jabref.model.ai.pipeline.SemanticSearchMatch;
import org.jabref.model.ai.pipeline.SemanticSearchResult;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.LinkedFile;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import org.jspecify.annotations.NullMarked;

/// [SemanticSearch] on the embeddings JabRef generates for linked files.
///
/// In contrast to [EmbeddingsSearchResponseEngine], which serves the chat about a few selected entries, this class
/// searches a whole library: each linked file is hashed once per search, and the relevance score and the file are part
/// of the result.
///
/// A search starts the ingestion of the files which are not ingested yet, but does not wait for it, since ingestion can
/// take minutes.
// [impl->adr~external-ai-agent-access~1]
// [impl->req~jabsrv.ai.search~1]
@NullMarked
public class EmbeddingsSemanticSearch implements SemanticSearch {

    private final AiPreferences aiPreferences;
    private final FilePreferences filePreferences;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final IngestedDocumentsRepository ingestedDocumentsRepository;
    private final Supplier<EmbeddingModel> embeddingModelSupplier;
    private final BiConsumer<BibDatabaseContext, LinkedFile> ingestionStarter;

    /// @param embeddingModelSupplier supplies the model at each search, because the user can change it in the preferences
    /// @param ingestionStarter       starts the ingestion of a linked file in the background
    public EmbeddingsSemanticSearch(
            AiPreferences aiPreferences,
            FilePreferences filePreferences,
            EmbeddingStore<TextSegment> embeddingStore,
            IngestedDocumentsRepository ingestedDocumentsRepository,
            Supplier<EmbeddingModel> embeddingModelSupplier,
            BiConsumer<BibDatabaseContext, LinkedFile> ingestionStarter
    ) {
        this.aiPreferences = aiPreferences;
        this.filePreferences = filePreferences;
        this.embeddingStore = embeddingStore;
        this.ingestedDocumentsRepository = ingestedDocumentsRepository;
        this.embeddingModelSupplier = embeddingModelSupplier;
        this.ingestionStarter = ingestionStarter;
    }

    public EmbeddingsSemanticSearch(AiPreferences aiPreferences, FilePreferences filePreferences, AiService aiService) {
        this(
                aiPreferences,
                filePreferences,
                aiService.getEmbeddingsStore(),
                aiService.getIngestedDocumentsRepository(),
                aiService::getCurrentEmbeddingModel,
                (databaseContext, linkedFile) -> aiService.getIngestionTaskAggregator().start(
                        new GenerateEmbeddingsTaskRequest(
                                filePreferences,
                                aiService.getIngestedDocumentsRepository(),
                                aiService.getEmbeddingsStore(),
                                aiService.getCurrentEmbeddingModel(),
                                aiService.getCurrentDocumentSplitter(),
                                databaseContext,
                                linkedFile),
                        false));
    }

    @Override
    public SemanticSearchResult search(BibDatabaseContext databaseContext, String query, int maximumResultsCount, double minimumScore) throws SemanticSearchNotAvailableException {
        if (!aiPreferences.getAiFeaturesEnabled()) {
            throw new SemanticSearchNotAvailableException("The AI features are disabled. Enable them in JabRef's preferences (tab \"AI\").");
        }
        EmbeddingModel embeddingModel = embeddingModelSupplier.get();
        if (embeddingModel instanceof AsyncEmbeddingModel asyncEmbeddingModel && !asyncEmbeddingModel.isPresent()) {
            throw new SemanticSearchNotAvailableException("The embedding model is not loaded yet. Try again later.");
        }

        SequencedMap<String, Source> ingestedSources = new LinkedHashMap<>();
        List<String> pendingFiles = new ArrayList<>();
        List<String> notFoundFiles = new ArrayList<>();

        for (BibEntry entry : databaseContext.getEntries()) {
            for (LinkedFile linkedFile : entry.getFiles()) {
                if (linkedFile.isOnlineLink()) {
                    continue;
                }
                linkedFile.findIn(databaseContext, filePreferences)
                          .flatMap(FileHasher::computeHash)
                          .ifPresentOrElse(fileHash -> {
                                      if (ingestedDocumentsRepository.isDocumentIngested(fileHash)) {
                                          ingestedSources.putIfAbsent(fileHash, new Source(entry, linkedFile));
                                      } else {
                                          pendingFiles.add(linkedFile.getLink());
                                          ingestionStarter.accept(databaseContext, linkedFile);
                                      }
                                  },
                                  () -> notFoundFiles.add(linkedFile.getLink()));
            }
        }

        if (ingestedSources.isEmpty()) {
            // Without a filter, the store is searched across all libraries
            return new SemanticSearchResult(List.of(), pendingFiles, notFoundFiles);
        }

        Embedding queryEmbedding = embeddingModel.embed(EmbeddingInputPrefixes.forQuery(embeddingModel, query)).content();
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest
                .builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maximumResultsCount)
                .minScore(minimumScore)
                .filter(MetadataFilterBuilder
                        .metadataKey(EmbeddingsCleaner.FILE_HASH_METADATA_KEY)
                        .isIn(ingestedSources.keySet()))
                .build();

        List<SemanticSearchMatch> matches = embeddingStore
                .search(searchRequest)
                .matches()
                .stream()
                .map(match -> toSemanticSearchMatch(match, ingestedSources))
                .flatMap(Optional::stream)
                .toList();

        return new SemanticSearchResult(matches, pendingFiles, notFoundFiles);
    }

    private static Optional<SemanticSearchMatch> toSemanticSearchMatch(EmbeddingMatch<TextSegment> match, SequencedMap<String, Source> ingestedSources) {
        TextSegment textSegment = match.embedded();
        return Optional.ofNullable(textSegment.metadata().getString(EmbeddingsCleaner.FILE_HASH_METADATA_KEY))
                       .flatMap(fileHash -> Optional.ofNullable(ingestedSources.get(fileHash)))
                       .map(source -> new SemanticSearchMatch(
                               source.entry().getCitationKey().orElse(null),
                               source.linkedFile().getLink(),
                               textSegment.metadata().getInteger(FileIngestor.PAGE_NUMBER_METADATA_KEY),
                               match.score(),
                               textSegment.text()));
    }

    private record Source(BibEntry entry, LinkedFile linkedFile) {
    }
}
