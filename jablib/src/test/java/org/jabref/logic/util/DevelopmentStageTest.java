package org.jabref.logic.util;

import org.jabref.logic.util.Version.DevelopmentStage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevelopmentStageTest {

    @Test
    void checkStabilityOrder() {
        assertTrue(DevelopmentStage.ALPHA.isMoreStableThan(DevelopmentStage.UNKNOWN));
        assertTrue(DevelopmentStage.BETA.isMoreStableThan(DevelopmentStage.ALPHA));
        assertTrue(DevelopmentStage.STABLE.isMoreStableThan(DevelopmentStage.BETA));

        assertEquals(4, DevelopmentStage.values().length, "It seems that the development stages have been changed, please adjust the test");
    }

    @Test
    void parseStages() {
        assertEquals(DevelopmentStage.ALPHA, DevelopmentStage.parse("-alpha"));
        assertEquals(DevelopmentStage.BETA, DevelopmentStage.parse("-beta"));
        assertEquals(DevelopmentStage.STABLE, DevelopmentStage.parse(""));
    }

    @Test
    void parseNull() {
        assertEquals(DevelopmentStage.UNKNOWN, DevelopmentStage.parse(null));
    }

    @Test
    void parseUnknownString() {
        assertEquals(DevelopmentStage.UNKNOWN, DevelopmentStage.parse("asdf"));
    }
}
