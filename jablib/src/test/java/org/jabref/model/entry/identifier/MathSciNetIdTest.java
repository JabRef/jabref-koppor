package org.jabref.model.entry.identifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class MathSciNetIdTest {

    @Test
    void parseRemovesNewLineCharacterAtEnd() {
        Optional<MathSciNetId> id = MathSciNetId.parse("3014184\n");
        assertEquals(Optional.of(new MathSciNetId("3014184")), id);
    }
}
