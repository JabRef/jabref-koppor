package org.jabref.gui.util;

import org.jabref.gui.util.ControlHelper.EllipsisPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ControlHelperTest {

    private final String TEXT = "abcdef";
    private final int MAX_CHARACTERS = 5;
    private final int DEFAULT_MAX_CHARACTERS = -1;
    private final String ELLIPSIS_STRING = "***";
    private final EllipsisPosition ELLIPSIS_POSITION = EllipsisPosition.ENDING;

    @ParameterizedTest
    @NullAndEmptySource
    void truncateWithTextNullAndEmptyReturnsSource(String text) {
        String truncatedText = ControlHelper.truncateString(text, MAX_CHARACTERS, ELLIPSIS_STRING, ELLIPSIS_POSITION);
        assertEquals(text, truncatedText);
    }

    @Test
    void truncateWithDefaultMaxCharactersReturnsText() {
        String truncatedText = ControlHelper.truncateString(TEXT, DEFAULT_MAX_CHARACTERS, ELLIPSIS_STRING, ELLIPSIS_POSITION);
        assertEquals(TEXT, truncatedText);
    }

    @Test
    void truncateWithEllipsisPositionBeginningReturnsTruncatedText() {
        String truncatedText = ControlHelper.truncateString(TEXT, MAX_CHARACTERS, ELLIPSIS_STRING, EllipsisPosition.BEGINNING);
        assertEquals("***ef", truncatedText);
    }

    @Test
    void truncateWithEllipsisPositionCenterReturnsTruncatedText() {
        String truncatedText = ControlHelper.truncateString(TEXT, MAX_CHARACTERS, ELLIPSIS_STRING, EllipsisPosition.CENTER);
        assertEquals("a***f", truncatedText);
    }

    @Test
    void truncateWithDefaultMaxCharactersAndNullEllipsisAndPositionEndingReturnsTruncatedText() {
        String text = "a".repeat(75) + "b".repeat(25);
        String truncatedText = ControlHelper.truncateString(text, DEFAULT_MAX_CHARACTERS, null, EllipsisPosition.ENDING);
        assertEquals("a".repeat(75), truncatedText);
    }

    @ParameterizedTest
    @NullSource
    void truncateWithNullEllipsisPositionThrowsNullPointerException(EllipsisPosition ellipsisPosition) {
        assertThrows(NullPointerException.class, () -> ControlHelper.truncateString(TEXT, MAX_CHARACTERS, ELLIPSIS_STRING, ellipsisPosition));
    }
}
