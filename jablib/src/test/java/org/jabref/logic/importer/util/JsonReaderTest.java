package org.jabref.logic.importer.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import kong.unirest.core.json.JSONObject;
import org.jabref.logic.importer.ParseException;
import org.junit.jupiter.api.Test;

class JsonReaderTest {

    @Test
    void nullStreamThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
            JsonReader.toJsonObject(null)
        );
    }

    @Test
    void invalidJsonThrowsParserException() {
        assertThrows(ParseException.class, () ->
            JsonReader.toJsonObject(
                new ByteArrayInputStream("invalid JSON".getBytes())
            )
        );
    }

    @Test
    void emptyStringResultsInEmptyObject() throws ParseException {
        JSONObject result = JsonReader.toJsonObject(
            new ByteArrayInputStream("".getBytes())
        );
        assertEquals("{}", result.toString());
    }

    @Test
    void arrayThrowsParserException() {
        // Reason: We expect a JSON object, not a JSON array
        assertThrows(ParseException.class, () ->
            JsonReader.toJsonObject(new ByteArrayInputStream("[]".getBytes()))
        );
    }

    @Test
    void exampleJsonResultsInSameJson() throws ParseException {
        String input = "{\"name\":\"test\"}";
        JSONObject result = JsonReader.toJsonObject(
            new ByteArrayInputStream(input.getBytes())
        );
        assertEquals(input, result.toString());
    }
}
