package org.jabref.logic.importer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Objects;

import org.jabref.model.entry.BibEntry;

import org.slf4j.LoggerFactory;

/**
 * Provides a convenient interface for entry-based fetcher, which follow the usual three-step procedure:
 * 1. Open a URL based on the entry
 * 2. Parse the response to get a list of {@link BibEntry}
 * 3. Post-process fetched entries
 */
public interface EntryBasedParserFetcher extends EntryBasedFetcher, ParserFetcher {

    /**
     * Constructs a URL based on the {@link BibEntry}.
     *
     * @param entry the entry to look information for
     */
    URL getURLForEntry(BibEntry entry) throws URISyntaxException, MalformedURLException, FetcherException;

    /**
     * Returns the parser used to convert the response to a list of {@link BibEntry}.
     */
    Parser getParser();

    @Override
    default List<BibEntry> performSearch(BibEntry entry) throws FetcherException {
        Objects.requireNonNull(entry);

        URL urlForEntry;
        try {
            if ((urlForEntry = getURLForEntry(entry)) == null) {
                return List.of();
            }
        } catch (MalformedURLException | URISyntaxException e) {
            throw new FetcherException("Search URI is malformed", e);
        }

        try (InputStream stream = new BufferedInputStream(urlForEntry.openStream())) {
            List<BibEntry> fetchedEntries = getParser().parseEntries(stream);

            // Post-cleanup
            fetchedEntries.forEach(this::doPostCleanup);

            return fetchedEntries;
        } catch (IOException e) {
            // TODO: Catch HTTP Response 401 errors and report that user has no rights to access resource
            //       Same TODO as in org.jabref.logic.net.URLDownload.openConnection. Code should be reused.
            LoggerFactory.getLogger(EntryBasedParserFetcher.class).error("Could not fetch from URL {}", urlForEntry, e);
            throw new FetcherException(urlForEntry, "A network error occurred", e);
        } catch (ParseException e) {
            throw new FetcherException(urlForEntry, "An internal parser error occurred", e);
        }
    }
}
