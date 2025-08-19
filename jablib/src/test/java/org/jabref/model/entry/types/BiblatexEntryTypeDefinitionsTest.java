package org.jabref.model.entry.types;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class BiblatexEntryTypeDefinitionsTest {

    @Test
    void all() {
        assertNotNull(BiblatexEntryTypeDefinitions.ALL);
    }
}
