package org.jabref.logic.importer.fetcher.transformers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.jabref.logic.search.query.SearchQueryVisitor;
import org.jabref.model.search.query.BaseQueryNode;
import org.jabref.model.search.query.SearchQuery;
import org.junit.jupiter.api.Test;

public abstract class YearAndYearRangeByFilteringQueryTransformerTest<
    T extends YearAndYearRangeByFilteringQueryTransformer
>
    extends YearRangeByFilteringQueryTransformerTest<T> {

    @Override
    @Test
    public void convertYearField() throws ParseCancellationException {
        YearAndYearRangeByFilteringQueryTransformer transformer =
            getTransformer();
        String queryString = "year=2021";
        SearchQuery searchQuery = new SearchQuery(queryString);
        BaseQueryNode searchQueryList = new SearchQueryVisitor(
            searchQuery.getSearchFlags()
        ).visitStart(searchQuery.getContext());
        Optional<String> query = transformer.transformSearchQuery(
            searchQueryList
        );
        assertEquals(Optional.empty(), query);
        assertEquals(Optional.of(2021), transformer.getStartYear());
        assertEquals(Optional.of(2021), transformer.getEndYear());
    }
}
