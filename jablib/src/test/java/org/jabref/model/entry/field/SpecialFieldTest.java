package org.jabref.model.entry.field;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SpecialFieldTest {

    @Test
    void getSpecialFieldInstanceFromFieldNameValid() {
        assertEquals(
            Optional.of(SpecialField.RANKING),
            SpecialField.fromName("ranking")
        );
    }

    @Test
    void getSpecialFieldInstanceFromFieldNameEmptyForInvalidField() {
        assertEquals(Optional.empty(), SpecialField.fromName("title"));
    }
}
