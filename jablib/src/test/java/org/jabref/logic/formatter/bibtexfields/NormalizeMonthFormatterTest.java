package org.jabref.logic.formatter.bibtexfields;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests in addition to the general tests from {@link org.jabref.logic.formatter.FormatterTest}
 */
class NormalizeMonthFormatterTest {

    private NormalizeMonthFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new NormalizeMonthFormatter();
    }

    @Test
    void formatExample() {
        assertEquals("#dec#", formatter.format(formatter.getExampleInput()));
    }

    @Test
    void plainAprilShouldBeApril() {
        assertEquals("#apr#", formatter.format("#apr#"));
    }
}
