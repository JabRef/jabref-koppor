package org.jabref.http.server.resources;

import java.io.IOException;
import java.util.Optional;

import org.jabref.http.SrvStateManager;
import org.jabref.http.dto.SemanticSearchRequest;
import org.jabref.http.dto.SemanticSearchResponse;
import org.jabref.http.server.services.ServerUtils;
import org.jabref.logic.ai.rag.logic.SemanticSearch;
import org.jabref.logic.ai.rag.logic.SemanticSearchNotAvailableException;
import org.jabref.logic.preferences.CliPreferences;
import org.jabref.logic.util.strings.StringUtil;
import org.jabref.model.database.BibDatabaseContext;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.MediaType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Semantic search over the files linked to the entries of a library, for external AI agents.
@Path("libraries/{id}/ai/search")
public class AiSearchResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiSearchResource.class);

    @Inject
    private SrvStateManager srvStateManager;

    @Inject
    private CliPreferences preferences;

    @Inject
    private SemanticSearch semanticSearch;

    /// [impl->req~jabsrv.ai.search~1]
    ///
    /// @param origin sent by browsers only. The response contains full text of the user's files, which no web page
    ///               should be able to trigger or read, so such requests are rejected.
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public SemanticSearchResponse search(@PathParam("id") String id,
                                         @HeaderParam("Origin") @Nullable String origin,
                                         SemanticSearchRequest request) throws IOException {
        if (StringUtil.isNotBlank(origin)) {
            throw new ForbiddenException("Requests from web pages are not allowed.");
        }
        if (StringUtil.isBlank(request.query())) {
            throw new BadRequestException("The field \"query\" is required.");
        }

        BibDatabaseContext databaseContext = ServerUtils.getBibDatabaseContext(id, srvStateManager, preferences.getImportFormatPreferences());
        int maximumResultsCount = Optional.ofNullable(request.maxResults()).orElseGet(() -> preferences.getAiPreferences().getRagMaxResultsCount());
        double minimumScore = Optional.ofNullable(request.minScore()).orElseGet(() -> preferences.getAiPreferences().getRagMinScore());

        try {
            return SemanticSearchResponse.of(semanticSearch.search(databaseContext, request.query(), maximumResultsCount, minimumScore));
        } catch (SemanticSearchNotAvailableException e) {
            LOGGER.debug("Semantic search is not available", e);
            throw new ServiceUnavailableException(e.getMessage());
        }
    }
}
