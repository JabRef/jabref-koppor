package org.jabref.model.entry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jabref.model.entry.types.EntryType;
import org.jabref.model.entry.types.EntryTypeFactory;
import org.jabref.model.entry.types.IEEETranEntryType;
import org.junit.jupiter.api.Test;

class EntryTypeFactoryTest {

    @Test
    void parseEntryTypePatent() {
        EntryType patent = IEEETranEntryType.Patent;
        assertEquals(patent, EntryTypeFactory.parse("patent"));
    }
}
