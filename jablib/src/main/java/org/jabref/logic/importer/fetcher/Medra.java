package org.jabref.logic.importer.fetcher;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.jabref.logic.cleanup.DoiCleanup;
import org.jabref.logic.importer.IdBasedParserFetcher;
import org.jabref.logic.importer.ParseException;
import org.jabref.logic.importer.Parser;
import org.jabref.logic.importer.util.JsonReader;
import org.jabref.logic.importer.util.MediaTypes;
import org.jabref.logic.net.URLDownload;
import org.jabref.logic.util.URLUtil;
import org.jabref.model.entry.Author;
import org.jabref.model.entry.AuthorList;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.types.EntryType;
import org.jabref.model.entry.types.StandardEntryType;

import kong.unirest.core.json.JSONArray;
import kong.unirest.core.json.JSONException;
import kong.unirest.core.json.JSONObject;

/**
 * A class for fetching DOIs from Medra
 *
 * @see <a href="https://data.medra.org">mEDRA Content Negotiation API</a> for an overview of the API
 * <p>
 * It requires "Accept" request Header attribute to be set to desired content-type.
 */
public class Medra implements IdBasedParserFetcher {

    public static final String API_URL = "https://data.medra.org";

    @Override
    public String getName() {
        return "mEDRA";
    }

    @Override
    public Parser getParser() {
        return inputStream -> {
            JSONObject response = JsonReader.toJsonObject(inputStream);
            if (response.isEmpty()) {
                return List.of();
            }
            return List.of(jsonItemToBibEntry(response));
        };
    }

    private BibEntry jsonItemToBibEntry(JSONObject item) throws ParseException {
        try {

            return new BibEntry(convertType(item.getString("type")))
                    .withField(StandardField.TITLE, item.getString("title"))
                    .withField(StandardField.AUTHOR, toAuthors(item.optJSONArray("author")))
                    .withField(StandardField.YEAR,
                            Optional.ofNullable(item.optJSONObject("issued"))
                                    .map(array -> array.optJSONArray("date-parts"))
                                    .map(array -> array.optJSONArray(0))
                                    .map(array -> array.optInt(0))
                                    .map(year -> Integer.toString(year)).orElse(""))
                    .withField(StandardField.DOI, item.getString("DOI"))
                    .withField(StandardField.PAGES, item.optString("page"))
                    .withField(StandardField.ISSN, item.optString("ISSN"))
                    .withField(StandardField.JOURNAL, item.optString("container-title"))
                    .withField(StandardField.PUBLISHER, item.optString("publisher"))
                    .withField(StandardField.URL, item.optString("URL"))
                    .withField(StandardField.VOLUME, item.optString("volume"));
        } catch (JSONException exception) {
            throw new ParseException("mEdRA API JSON format has changed", exception);
        }
    }

    private EntryType convertType(String type) {
        return "article-journal".equals(type) ? StandardEntryType.Article : StandardEntryType.Misc;
    }

    private String toAuthors(JSONArray authors) {
        if (authors == null) {
            return "";
        }
        // input: list of {"literal":"A."}
        return IntStream.range(0, authors.length())
                        .mapToObj(authors::getJSONObject)
                        .map(author -> author.has("literal") ? // quickly route through the literal string
                                new Author(author.getString("literal"), "", "", "", "") :
                                new Author(author.optString("given", ""), "", "", author.optString("family", ""), ""))
                        .collect(AuthorList.collect())
                        .getAsFirstLastNamesWithAnd();
    }

    @Override
    public URLDownload getUrlDownload(URL url) {
        URLDownload download = new URLDownload(url);
        download.addHeader("Accept", MediaTypes.CITATIONSTYLES_JSON);
        return download;
    }

    @Override
    public URL getUrlForIdentifier(String identifier) throws URISyntaxException, MalformedURLException {
        return URLUtil.create(API_URL + "/" + identifier);
    }

    @Override
    public void doPostCleanup(BibEntry entry) {
        new DoiCleanup().cleanup(entry);
    }
}
