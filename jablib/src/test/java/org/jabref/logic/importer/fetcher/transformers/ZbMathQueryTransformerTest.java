package org.jabref.logic.importer.fetcher.transformers;

import java.util.Optional;

import org.apache.lucene.queryparser.flexible.core.QueryNodeParseException;
import org.apache.lucene.queryparser.flexible.core.nodes.QueryNode;
import org.apache.lucene.queryparser.flexible.standard.parser.StandardSyntaxParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZbMathQueryTransformerTest extends InfixTransformerTest<ZbMathQueryTransformer> {

    @Override
    public ZbMathQueryTransformer getTransformer() {
        return new ZbMathQueryTransformer();
    }

    @Override
    public String getAuthorPrefix() {
        return "au:";
    }

    @Override
    public String getUnFieldedPrefix() {
        return "any:";
    }

    @Override
    public String getJournalPrefix() {
        return "so:";
    }

    @Override
    public String getTitlePrefix() {
        return "ti:";
    }

    @Override
    @Test
    public void convertYearField() throws QueryNodeParseException {
        String queryString = "year:2015";
        QueryNode luceneQuery = new StandardSyntaxParser().parse(queryString, AbstractQueryTransformer.NO_EXPLICIT_FIELD);
        Optional<String> searchQuery = getTransformer().transformLuceneQuery(luceneQuery);
        Optional<String> expected = Optional.of("py:2015");
        assertEquals(expected, searchQuery);
    }

    @Override
    @Test
    public void convertYearRangeField() throws QueryNodeParseException {
        String queryString = "year-range:2012-2015";
        QueryNode luceneQuery = new StandardSyntaxParser().parse(queryString, AbstractQueryTransformer.NO_EXPLICIT_FIELD);
        Optional<String> searchQuery = getTransformer().transformLuceneQuery(luceneQuery);
        Optional<String> expected = Optional.of("py:2012-2015");
        assertEquals(expected, searchQuery);
    }
}
